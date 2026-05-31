package media.barney.cognitive.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import java.net.URI;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class JavaMethodParserTest {

    @Test
    void extractsConcreteMethodsWithLinesAndCognitiveComplexity() {
        String source = """
                package demo;
                class Sample {
                    int alpha(boolean a, boolean b) {
                        if (a && b) {
                            return 1;
                        }
                        return 0;
                    }

                    int beta(int x) {
                        switch (x) {
                            case 1: return 1;
                            case 2: return 2;
                            default: return 0;
                        }
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("demo.Sample", source);

        assertEquals(List.of(
                new MethodDescriptor("alpha", 3, 8, 2),
                new MethodDescriptor("beta", 10, 16, 1)
        ), methods);
    }

    @Test
    void includesConstructorsAndSkipsAbstractMethods() {
        String source = """
                abstract class Sample {
                    Sample(boolean value) {
                        if (value) {
                        }
                    }

                    Sample() {
                    }

                    abstract int missing();

                    int present() {
                        return 1;
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("Sample", source);

        assertEquals(List.of(
                new MethodDescriptor("Sample", 2, 5, 1),
                new MethodDescriptor("Sample", 7, 8, 0),
                new MethodDescriptor("present", 12, 14, 0)
        ), methods);
    }

    @Test
    void includesCompactRecordConstructors() {
        String source = """
                record Sample(int value) {
                    Sample {
                        if (value < 0) {
                            throw new IllegalArgumentException();
                        }
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("Sample", source);

        assertEquals(List.of(new MethodDescriptor("Sample", 2, 6, 1)), methods);
    }

    @Test
    void constructorIdentityDoesNotCollideWithSameNamedMethods() {
        String source = """
                class Sample {
                    Sample() {
                        Sample();
                    }

                    void Sample() {
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("Sample", source);

        assertEquals(List.of(
                new MethodDescriptor("Sample", 2, 4, 0),
                new MethodDescriptor("Sample", 6, 7, 0)
        ), methods);
    }

    @Test
    void includesMethodsDeclaredInsideAnonymousClasses() {
        String source = """
                class Sample {
                    int outer() {
                        Runnable runnable = new Runnable() {
                            @Override
                            public void run() {
                                if (true) {
                                }
                            }
                        };
                        return 1;
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("Sample", source);

        assertEquals(List.of(
                new MethodDescriptor("outer", 2, 11, 0),
                new MethodDescriptor("Sample.outer.<anonymous Runnable@3:44>.run", 4, 8, 1)
        ), methods);
    }

    @Test
    void includesMultipleAnonymousClassesWithDistinctNames() {
        String source = """
                class Sample {
                    void outer() {
                        Runnable first = new Runnable() {
                            public void run() {
                            }
                        };
                        Runnable second = new Runnable() {
                            public void run() {
                            }
                        };
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("Sample", source);

        assertEquals(List.of(
                new MethodDescriptor("outer", 2, 11, 0),
                new MethodDescriptor("Sample.outer.<anonymous Runnable@3:41>.run", 4, 5, 0),
                new MethodDescriptor("Sample.outer.<anonymous Runnable@7:42>.run", 8, 9, 0)
        ), methods);
    }

    @Test
    void includesMethodsDeclaredInsideLocalClasses() {
        String source = """
                class Sample {
                    int outer(boolean value) {
                        class Local {
                            int work() {
                                if (value) {
                                    return 1;
                                }
                                return 0;
                            }
                        }
                        return new Local().work();
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("Sample", source);

        assertEquals(List.of(
                new MethodDescriptor("outer", 2, 12, 0),
                new MethodDescriptor("Sample.outer.Local@3:9.work", 4, 9, 1)
        ), methods);
    }

    @Test
    void ignoresKeywordsInsideCommentsAndStrings() {
        String source = """
                class Sample {
                    int stable() {
                        String text = "if && || ? switch catch";
                        // if && || ? switch catch
                        /* if && || ? switch catch */
                        return 1;
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("Sample", source);

        assertEquals(List.of(new MethodDescriptor("stable", 2, 7, 0)), methods);
    }

    @Test
    void packageNamesIgnoreCommentsAndStrings() {
        String source = """
                /*
                 * package fake;
                 */
                package demo;

                class Real {
                    int stable() {
                        String text = "package wrong;";
                        return 1;
                    }
                }
                """;

        List<ParsedMethod> methods = JavaMethodParser.parseDetailed("demo/Real.java", source);

        assertEquals("demo.Real", methods.get(0).className());
    }

    @Test
    void skipsMethodLineRangesWithoutCompilerPositions() {
        MethodTree method = proxy(MethodTree.class, "getBody", proxy(BlockTree.class));
        SourcePositions positions = new SourcePositions() {
            @Override
            public long getStartPosition(CompilationUnitTree file, Tree tree) {
                return Diagnostic.NOPOS;
            }

            @Override
            public long getEndPosition(CompilationUnitTree file, Tree tree) {
                return Diagnostic.NOPOS;
            }
        };

        JavaMethodParser.MethodLineRange range = JavaMethodParser.methodLineRange(
                proxy(CompilationUnitTree.class),
                method,
                positions);

        assertNull(range);
    }

    @Test
    void formatsDiagnosticsWithoutPositionsUsingPlaceholders() {
        Diagnostic<JavaFileObject> diagnostic = new Diagnostic<>() {
            @Override
            public Kind getKind() {
                return Kind.ERROR;
            }

            @Override
            public @Nullable JavaFileObject getSource() {
                return null;
            }

            @Override
            public long getPosition() {
                return NOPOS;
            }

            @Override
            public long getStartPosition() {
                return NOPOS;
            }

            @Override
            public long getEndPosition() {
                return NOPOS;
            }

            @Override
            public long getLineNumber() {
                return NOPOS;
            }

            @Override
            public long getColumnNumber() {
                return NOPOS;
            }

            @Override
            public String getCode() {
                return "compiler.err.synthetic";
            }

            @Override
            public String getMessage(Locale locale) {
                return "synthetic parse failure";
            }
        };

        assertEquals("<unknown>:?:?: synthetic parse failure", JavaMethodParser.formatDiagnostic(diagnostic));
    }

    @Test
    void countsElseIfElseAndNestedIfWithoutExtraElseIfNestingPenalty() {
        String source = """
                class Sample {
                    int chain(boolean a, boolean b, boolean c) {
                        if (a) {
                            return 1;
                        } else if (b) {
                            return 2;
                        } else {
                            if (c) {
                                return 3;
                            }
                        }
                        return 0;
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("Sample", source);

        assertEquals(List.of(new MethodDescriptor("chain", 2, 13, 5)), methods);
    }

    @Test
    void countsLogicalSequencesAcrossNegationBoundaries() {
        String source = """
                class Sample {
                    int score(boolean a, boolean b, boolean c) {
                        if (a && !(b && c)) {
                            return 1;
                        }
                        return 0;
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("Sample", source);

        assertEquals(List.of(new MethodDescriptor("score", 2, 7, 3)), methods);
    }

    @Test
    void countsLabeledJumpsLambdaNestingAndSwitchExpressions() {
        String source = """
                class Sample {
                    int mixed(boolean a, boolean b, int[] values, int x) {
                        OUT: for (int value : values) {
                            if (a) {
                                continue OUT;
                            }
                        }
                        Runnable runnable = () -> {
                            if (b) {
                            }
                        };
                        return switch (x) {
                            case 1 -> 1;
                            default -> 0;
                        };
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("Sample", source);

        assertEquals(List.of(new MethodDescriptor("mixed", 2, 16, 7)), methods);
    }

    @Test
    void addsRecursionIncrementForDirectRecursion() {
        String source = """
                class Sample {
                    int recurse(int value) {
                        if (value <= 0) {
                            return 0;
                        }
                        return recurse(value - 1);
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("Sample", source);

        assertEquals(List.of(new MethodDescriptor("recurse", 2, 7, 2)), methods);
    }

    @Test
    void buildsSourcePathAndUriFromClassNames() {
        assertEquals("demo/Sample.java", JavaMethodParser.sourcePath("demo.Sample"));
        assertEquals("demo/Sample.java", JavaMethodParser.sourcePath("demo.Sample.java"));
        assertEquals(URI.create("string:///demo/Sample.java"), JavaMethodParser.sourceUri("demo.Sample"));
        assertEquals(URI.create("string:///demo/Sample.java"), JavaMethodParser.sourceUri("demo.Sample.java"));
    }

    @Test
    void proxyDefaultValuesMatchPrimitiveReturnTypes() {
        PrimitiveDefaults defaults = proxy(PrimitiveDefaults.class);

        assertEquals(false, defaults.booleanValue());
        assertEquals(Byte.valueOf((byte) 0), defaults.byteValue());
        assertEquals(Short.valueOf((short) 0), defaults.shortValue());
        assertEquals(Integer.valueOf(0), defaults.intValue());
        assertEquals(Long.valueOf(0L), defaults.longValue());
        assertEquals(Float.valueOf(0.0F), defaults.floatValue());
        assertEquals(Double.valueOf(0.0D), defaults.doubleValue());
        assertEquals(Character.valueOf('\0'), defaults.charValue());
    }

    private static <T> T proxy(Class<T> type) {
        return proxy(type, "", new Object());
    }

    private static <T> T proxy(Class<T> type, String methodName, Object value) {
        Object proxy = Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (ignored, method, args) -> method.getName().equals(methodName)
                        ? value
                        : defaultValue(method.getReturnType()));
        return type.cast(proxy);
    }

    private static @Nullable Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        if (type == double.class) {
            return 0.0D;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }

    private interface PrimitiveDefaults {
        boolean booleanValue();

        byte byteValue();

        short shortValue();

        int intValue();

        long longValue();

        float floatValue();

        double doubleValue();

        char charValue();
    }
}

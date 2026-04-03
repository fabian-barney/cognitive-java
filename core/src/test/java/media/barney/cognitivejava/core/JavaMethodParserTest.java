package media.barney.cognitivejava.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.List;
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
    void ignoresConstructorsAndAbstractMethods() {
        String source = """
                abstract class Sample {
                    Sample() {
                    }

                    abstract int missing();

                    int present() {
                        return 1;
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("Sample", source);

        assertEquals(List.of(new MethodDescriptor("present", 7, 9, 0)), methods);
    }

    @Test
    void ignoresMethodsDeclaredInsideAnonymousClasses() {
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

        assertEquals(List.of(new MethodDescriptor("outer", 2, 11, 0)), methods);
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
}

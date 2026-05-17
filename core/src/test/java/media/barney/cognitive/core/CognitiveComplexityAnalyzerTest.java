package media.barney.cognitive.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CognitiveComplexityAnalyzerTest {

    @TempDir
    Path tempDir;

    @Test
    void addsRecursionIncrementForMutualRecursionAcrossSources() {
        Map<String, String> sources = Map.of(
                "A.java", """
                        package demo;

                        class A {
                            static int alpha(int value) {
                                if (value <= 0) {
                                    return 0;
                                }
                                return B.beta(value - 1);
                            }
                        }
                        """,
                "B.java", """
                        package demo;

                        class B {
                            static int beta(int value) {
                                if (value <= 0) {
                                    return 0;
                                }
                                return A.alpha(value - 1);
                            }
                        }
                        """
        );

        List<MethodMetrics> metrics = CognitiveComplexityAnalyzer.analyzeSources(sources);

        assertEquals(List.of(
                metric("alpha", "demo.A", "A.java", 4, 9, 2),
                metric("beta", "demo.B", "B.java", 4, 9, 2)
        ), metrics);
    }

    @Test
    void addsRecursionIncrementForDirectSelfRecursion() {
        Map<String, String> sources = Map.of(
                "Sample.java", """
                        package demo;

                        class Sample {
                            static int alpha(int value) {
                                if (value <= 0) {
                                    return 0;
                                }
                                return alpha(value - 1);
                            }
                        }
                        """
        );

        List<MethodMetrics> metrics = CognitiveComplexityAnalyzer.analyzeSources(sources);

        assertEquals(List.of(
                metric("alpha", "demo.Sample", "Sample.java", 4, 9, 2)
        ), metrics);
    }

    @Test
    void analyzesFilesWithDuplicateBasenamesAcrossModules() throws Exception {
        Path moduleA = tempDir.resolve("module-a/src/main/java/demo/Sample.java");
        Path moduleB = tempDir.resolve("module-b/src/main/java/demo/Sample.java");
        Files.createDirectories(moduleA.getParent());
        Files.createDirectories(moduleB.getParent());
        Files.writeString(moduleA, """
                package demo;

                class Sample {
                    int alpha(boolean value) {
                        if (value) {
                            return 1;
                        }
                        return 0;
                    }
                }
                """);
        Files.writeString(moduleB, """
                package demo;

                class Sample {
                    int beta(boolean left, boolean right) {
                        if (left && right) {
                            return 1;
                        }
                        return 0;
                    }
                }
                """);

        List<MethodMetrics> metrics = CognitiveComplexityAnalyzer.analyze(tempDir, List.of(moduleA, moduleB));

        assertEquals(List.of(
                metric("beta", "demo.Sample", "module-b/src/main/java/demo/Sample.java", 4, 9, 2),
                metric("alpha", "demo.Sample", "module-a/src/main/java/demo/Sample.java", 4, 9, 1)
        ), metrics);
    }

    @Test
    void doesNotTreatAmbiguousSimpleClassNamesAcrossPackagesAsRecursive() {
        Map<String, String> sources = Map.of(
                "A.java", """
                        package pkg1;

                        class A {
                            static int alpha(int value) {
                                if (value <= 0) {
                                    return 0;
                                }
                                return Foo.beta(value - 1);
                            }
                        }
                        """,
                "FooOne.java", """
                        package pkg1;

                        class Foo {
                            static int beta(int value) {
                                return value;
                            }
                        }
                        """,
                "FooTwo.java", """
                        package pkg2;

                        import pkg1.A;

                        class Foo {
                            static int beta(int value) {
                                if (value <= 0) {
                                    return 0;
                                }
                                return A.alpha(value - 1);
                            }
                        }
                        """
        );

        List<MethodMetrics> metrics = CognitiveComplexityAnalyzer.analyzeSources(sources);

        assertEquals(List.of(
                metric("alpha", "pkg1.A", "A.java", 4, 9, 1),
                metric("beta", "pkg2.Foo", "FooTwo.java", 6, 11, 1),
                metric("beta", "pkg1.Foo", "FooOne.java", 4, 6, 0)
        ), metrics);
    }

    private static MethodMetrics metric(String methodName,
                                        String className,
                                        String sourcePath,
                                        int startLine,
                                        int endLine,
                                        int complexity) {
        return new MethodMetrics(methodName, className, sourcePath, startLine, endLine, complexity);
    }
}

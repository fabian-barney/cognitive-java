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
                new MethodMetrics("alpha", "demo.A", 2),
                new MethodMetrics("beta", "demo.B", 2)
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

        List<MethodMetrics> metrics = CognitiveComplexityAnalyzer.analyze(List.of(moduleA, moduleB));

        assertEquals(List.of(
                new MethodMetrics("beta", "demo.Sample", 2),
                new MethodMetrics("alpha", "demo.Sample", 1)
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
                new MethodMetrics("alpha", "pkg1.A", 1),
                new MethodMetrics("beta", "pkg2.Foo", 1),
                new MethodMetrics("beta", "pkg1.Foo", 0)
        ), metrics);
    }
}

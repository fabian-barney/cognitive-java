package media.barney.cognitivejava.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CognitiveComplexityAnalyzerTest {

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
}

package media.barney.cognitive.core;

import java.util.List;

record CognitiveReport(
        String status,
        int threshold,
        List<MethodReport> methods,
        SourceExclusionAudit exclusions,
        double elapsedSeconds
) {
    CognitiveReport(String status, int threshold, List<MethodReport> methods) {
        this(status, threshold, methods, SourceExclusionAudit.empty(), 0.0);
    }

    CognitiveReport(String status, int threshold, List<MethodReport> methods, SourceExclusionAudit exclusions) {
        this(status, threshold, methods, exclusions, 0.0);
    }

    static CognitiveReport from(List<MethodMetrics> metrics, int threshold) {
        return from(metrics, threshold, SourceExclusionAudit.empty());
    }

    static CognitiveReport from(List<MethodMetrics> metrics, int threshold, SourceExclusionAudit exclusions) {
        int validatedThreshold = Thresholds.validate(threshold);
        List<MethodReport> methods = metrics.stream()
                .map(metric -> MethodReport.from(metric, validatedThreshold))
                .toList();
        return new CognitiveReport(status(methods), validatedThreshold, methods, exclusions);
    }

    CognitiveReport withElapsedNanos(long elapsedNanos) {
        double seconds = Math.max(0.0, elapsedNanos / 1_000_000_000.0);
        return new CognitiveReport(status, threshold, methods, exclusions, seconds);
    }

    private static String status(List<MethodReport> methods) {
        boolean failed = methods.stream()
                .anyMatch(method -> method.status() == MethodStatus.FAILED);
        return failed ? MethodStatus.FAILED.value() : MethodStatus.PASSED.value();
    }

    record MethodReport(
            MethodStatus status,
            int complexity,
            String methodName,
            String className,
            String sourcePath,
            int startLine,
            int endLine
    ) {
        private static MethodReport from(MethodMetrics metric, int threshold) {
            return new MethodReport(
                    status(metric, threshold),
                    metric.cognitiveComplexity(),
                    metric.methodName(),
                    metric.className(),
                    metric.sourcePath(),
                    metric.startLine(),
                    metric.endLine()
            );
        }

        private static MethodStatus status(MethodMetrics metric, int threshold) {
            if (metric.cognitiveComplexity() > threshold) {
                return MethodStatus.FAILED;
            }
            return MethodStatus.PASSED;
        }
    }
}

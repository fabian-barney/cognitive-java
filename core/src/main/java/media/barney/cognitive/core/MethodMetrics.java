package media.barney.cognitive.core;

record MethodMetrics(
        String methodName,
        String className,
        String sourcePath,
        int startLine,
        int endLine,
        int cognitiveComplexity
) {
}

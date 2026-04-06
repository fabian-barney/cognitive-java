package media.barney.cognitive.core;

record MethodDescriptor(
        String name,
        int startLine,
        int endLine,
        int cognitiveComplexity
) {
}

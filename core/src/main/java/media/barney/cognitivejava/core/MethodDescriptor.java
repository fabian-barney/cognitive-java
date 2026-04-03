package media.barney.cognitivejava.core;

record MethodDescriptor(
        String name,
        int startLine,
        int endLine,
        int cognitiveComplexity
) {
}

package media.barney.cognitive.core;

import java.util.List;

record ParsedMethod(
        String packageName,
        String className,
        String methodName,
        String sourcePath,
        int arity,
        int startLine,
        int endLine,
        List<String> classAnnotations,
        int baseCognitiveComplexity,
        List<MethodCall> calls
) {
    ParsedMethod {
        classAnnotations = List.copyOf(classAnnotations);
        calls = List.copyOf(calls);
    }

    MethodKey key() {
        return new MethodKey(className, methodName, arity);
    }

    String id() {
        return sourcePath + "#" + className + "#" + methodName + "/" + arity + ":" + startLine;
    }
}

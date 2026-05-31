package media.barney.cognitive.core;

import java.util.List;

record ParsedMethod(
        String packageName,
        String className,
        String declarationName,
        String displayName,
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
        return new MethodKey(className, declarationName, arity);
    }

    String id() {
        return sourcePath + "#" + className + "#" + declarationName + "/" + arity + ":" + startLine;
    }
}

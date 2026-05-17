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
        int baseCognitiveComplexity,
        List<MethodCall> calls
) {
    ParsedMethod {
        calls = List.copyOf(calls);
    }

    MethodKey key() {
        return new MethodKey(className, methodName, arity);
    }

    String id() {
        return sourcePath + "#" + className + "#" + methodName + "/" + arity + ":" + startLine;
    }
}

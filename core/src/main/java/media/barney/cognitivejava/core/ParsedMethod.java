package media.barney.cognitivejava.core;

import java.util.List;

record ParsedMethod(
        String packageName,
        String className,
        String methodName,
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
        return className + "#" + methodName + "/" + arity + ":" + startLine;
    }
}

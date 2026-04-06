package media.barney.cognitive.core;

import org.jspecify.annotations.Nullable;

record MethodCall(@Nullable String ownerName, boolean sameClass, String methodName, int arity) {
}

package media.barney.cognitive.core;

import java.util.List;
import java.util.Objects;

record SourceExclusionOptions(
        List<String> excludes,
        List<String> excludeClasses,
        List<String> excludeAnnotations,
        boolean useDefaultExclusions
) {
    SourceExclusionOptions {
        excludes = normalized(excludes);
        excludeClasses = normalized(excludeClasses);
        excludeAnnotations = normalized(excludeAnnotations);
    }

    static SourceExclusionOptions defaults() {
        return new SourceExclusionOptions(List.of(), List.of(), List.of(), true);
    }

    private static List<String> normalized(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}

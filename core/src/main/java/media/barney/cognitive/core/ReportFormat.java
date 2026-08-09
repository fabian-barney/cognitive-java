package media.barney.cognitive.core;

import java.util.Locale;
import java.util.Map;

enum ReportFormat {
    TOON,
    JSON,
    TEXT,
    JUNIT,
    NONE;

    private static final Map<String, ReportFormat> BY_VALUE = Map.of(
            "toon", TOON,
            "json", JSON,
            "text", TEXT,
            "junit", JUNIT,
            "none", NONE
    );

    static ReportFormat parse(String value) {
        ReportFormat format = BY_VALUE.get(value.toLowerCase(Locale.ROOT));
        if (format == null) {
            throw new IllegalArgumentException("Unknown report format: " + value);
        }
        return format;
    }
}

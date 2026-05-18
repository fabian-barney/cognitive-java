package media.barney.cognitive.core;

import java.util.List;
import org.jspecify.annotations.Nullable;

record CliArguments(
        CliMode mode,
        ReportFormat reportFormat,
        int threshold,
        boolean agent,
        boolean failuresOnly,
        boolean omitRedundancy,
        @Nullable String outputPath,
        @Nullable String junitReportPath,
        List<String> fileArgs,
        SourceExclusionOptions exclusionOptions
) {
}

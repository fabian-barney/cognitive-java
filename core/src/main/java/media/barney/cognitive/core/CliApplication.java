package media.barney.cognitive.core;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

final class CliApplication {

    private final Path projectRoot;
    private final PrintStream out;
    private final PrintStream err;
    private final LongSupplier nanoTime;

    CliApplication(Path projectRoot, PrintStream out, PrintStream err) {
        this(projectRoot, out, err, System::nanoTime);
    }

    CliApplication(Path projectRoot, PrintStream out, PrintStream err, LongSupplier nanoTime) {
        this.projectRoot = projectRoot;
        this.out = out;
        this.err = err;
        this.nanoTime = nanoTime;
    }

    int execute(String[] args) throws Exception {
        ParseOutcome parse = parseArguments(args);
        if (parse.exitCode >= 0) {
            return parse.exitCode;
        }

        long startedAt = nanoTime.getAsLong();
        CliArguments parsed = parse.arguments();
        try {
            ReportOptions options = reportOptions(parsed);
            List<Path> filesToAnalyze = filesForMode(parsed);
            List<MethodMetrics> metrics = filesToAnalyze.isEmpty()
                    ? List.of()
                    : CognitiveComplexityAnalyzer.analyze(projectRoot, filesToAnalyze);
            CognitiveReport report = CognitiveReport.from(metrics, parsed.threshold())
                    .withElapsedNanos(nanoTime.getAsLong() - startedAt);
            ReportPublisher.publish(report, options, out);

            int max = Main.maxCognitiveComplexity(metrics);
            if (thresholdExceeded(max, parsed.threshold())) {
                err.printf("Cognitive Complexity threshold exceeded: %d > %d%n", max, parsed.threshold());
                return 2;
            }
            return 0;
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            return 1;
        }
    }

    static boolean thresholdExceeded(int max, int threshold) {
        return max > threshold;
    }

    private ParseOutcome parseArguments(String[] args) {
        try {
            CliArguments parsed = CliArgumentsParser.parse(args);
            if (parsed.mode() == CliMode.HELP) {
                out.println(Main.usage());
                return ParseOutcome.exit(0);
            }
            return ParseOutcome.ok(parsed);
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            out.println(Main.usage());
            return ParseOutcome.exit(1);
        }
    }

    private List<Path> filesForMode(CliArguments parsed) throws Exception {
        if (parsed.mode() == CliMode.EXPLICIT_FILES) {
            return explicitFiles(parsed.fileArgs());
        }
        return nonExplicitFiles(parsed.mode());
    }

    private ReportOptions reportOptions(CliArguments parsed) {
        return ReportOptions.create(
                projectRoot,
                parsed.reportFormat(),
                parsed.failuresOnly(),
                parsed.omitRedundancy(),
                parsed.outputPath(),
                parsed.junitReportPath()
        );
    }

    private List<Path> nonExplicitFiles(CliMode mode) throws Exception {
        return switch (mode) {
            case CHANGED_SRC -> ChangedFileDetector.changedJavaFilesUnderSourceRoots(projectRoot);
            case ALL_SRC -> SourceFileFinder.findAllJavaFilesUnderSourceRoots(projectRoot);
            case EXPLICIT_FILES, HELP ->
                    throw new IllegalStateException("Unexpected CLI mode during non-explicit file resolution: " + mode);
        };
    }

    private List<Path> explicitFiles(List<String> args) throws Exception {
        Set<Path> files = new LinkedHashSet<>();
        for (String arg : args) {
            Path path = projectRoot.resolve(arg).normalize();
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Path does not exist: " + arg);
            }
            if (Files.isDirectory(path)) {
                files.addAll(SourceFileFinder.findAllJavaFilesUnderSourceRoots(path));
            } else {
                files.add(path);
            }
        }
        List<Path> sorted = new ArrayList<>(files);
        sorted.sort(Comparator.naturalOrder());
        return sorted;
    }

    private static final class ParseOutcome {
        private final @Nullable CliArguments arguments;
        private final int exitCode;

        private ParseOutcome(@Nullable CliArguments arguments, int exitCode) {
            this.arguments = arguments;
            this.exitCode = exitCode;
        }

        private static ParseOutcome ok(CliArguments arguments) {
            return new ParseOutcome(arguments, -1);
        }

        private static ParseOutcome exit(int code) {
            return new ParseOutcome(null, code);
        }

        private CliArguments arguments() {
            if (arguments == null) {
                throw new IllegalStateException("No parsed arguments are available");
            }
            return arguments;
        }
    }
}

package media.barney.cognitive.core;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class CliApplication {

    private final Path projectRoot;
    private final PrintStream out;
    private final PrintStream err;

    CliApplication(Path projectRoot, PrintStream out, PrintStream err) {
        this.projectRoot = projectRoot;
        this.out = out;
        this.err = err;
    }

    int execute(String[] args) throws Exception {
        ParseOutcome parse = parseArguments(args);
        if (parse.exitCode >= 0) {
            return parse.exitCode;
        }

        CliArguments parsed = parse.arguments();
        try {
            List<Path> filesToAnalyze = filesForMode(parsed);
            if (filesToAnalyze.isEmpty()) {
                out.println("No Java files to analyze.");
                return 0;
            }

            List<MethodMetrics> metrics = CognitiveComplexityAnalyzer.analyze(filesToAnalyze);
            out.print(ReportFormatter.format(metrics));

            int max = Main.maxCognitiveComplexity(metrics);
            if (thresholdExceeded(max)) {
                err.printf("Cognitive Complexity threshold exceeded: %d > 25%n", max);
                return 2;
            }
            return 0;
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            return 1;
        }
    }

    static boolean thresholdExceeded(int max) {
        return max > 25;
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
        return switch (parsed.mode()) {
            case ALL_SRC -> SourceFileFinder.findAllJavaFilesUnderSourceRoots(projectRoot);
            case CHANGED_SRC -> ChangedFileDetector.changedJavaFilesUnderSourceRoots(projectRoot);
            case EXPLICIT_FILES -> explicitFiles(parsed.fileArgs());
            case HELP -> List.of();
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

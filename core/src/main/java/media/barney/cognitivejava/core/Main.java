package media.barney.cognitivejava.core;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        System.exit(run(args, Path.of(".").toAbsolutePath().normalize(), System.out, System.err));
    }

    public static int run(String[] args, Path projectRoot, PrintStream out, PrintStream err) throws Exception {
        return new CliApplication(projectRoot, out, err).execute(args);
    }

    static String usage() {
        return """
                Usage:
                  cognitive-java            Analyze all Java files under any nested src/main/java tree
                  cognitive-java --changed  Analyze changed Java files under any nested src/main/java tree
                  cognitive-java <path...> Analyze files, or for directory args analyze nested src/main/java trees under each path
                  cognitive-java --help     Print this help message
                """;
    }

    static int maxCognitiveComplexity(List<MethodMetrics> metrics) {
        int max = 0;
        for (MethodMetrics metric : metrics) {
            max = Math.max(max, metric.cognitiveComplexity());
        }
        return max;
    }
}

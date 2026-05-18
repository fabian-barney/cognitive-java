package media.barney.cognitive.maven;

import media.barney.cognitive.core.Main;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, aggregator = true, threadSafe = true)
public class CognitiveJavaCheckMojo extends AbstractMojo {

    private static final Set<String> VALID_FORMATS = Set.of("toon", "json", "text", "junit", "none");

    private final CognitiveJavaRunner runner;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private @Nullable MavenSession session;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private @Nullable MavenProject project;

    @Parameter(property = "cognitiveJava.format", defaultValue = "none")
    private String format = "none";

    @Parameter(property = "cognitiveJava.agent", defaultValue = "false")
    private boolean agent;

    @Parameter(property = "cognitiveJava.failuresOnly")
    private @Nullable String failuresOnly;

    @Parameter(property = "cognitiveJava.omitRedundancy")
    private @Nullable String omitRedundancy;

    @Parameter(property = "cognitiveJava.output")
    private @Nullable File output;

    @Parameter(property = "cognitiveJava.junit", defaultValue = "true")
    private String junit = "true";

    @Parameter(property = "cognitiveJava.junitReport")
    private @Nullable File junitReport;

    @Parameter(property = "cognitiveJava.threshold", defaultValue = "15")
    private String threshold = "15";

    @Parameter
    private List<String> excludes = new ArrayList<>();

    @Parameter(property = "cognitiveJava.excludes")
    private @Nullable String excludesProperty;

    @Parameter
    private List<String> excludeClasses = new ArrayList<>();

    @Parameter(property = "cognitiveJava.excludeClasses")
    private @Nullable String excludeClassesProperty;

    @Parameter
    private List<String> excludeAnnotations = new ArrayList<>();

    @Parameter(property = "cognitiveJava.excludeAnnotations")
    private @Nullable String excludeAnnotationsProperty;

    @Parameter(property = "cognitiveJava.useDefaultExclusions", defaultValue = "true")
    private boolean useDefaultExclusions = true;

    public CognitiveJavaCheckMojo() {
        this(Main::run);
    }

    CognitiveJavaCheckMojo(CognitiveJavaRunner runner) {
        this.runner = Objects.requireNonNull(runner, "runner");
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Path executionRoot = executionRoot();
        MavenProject project = project();
        if (!project.getBasedir().toPath().normalize().equals(executionRoot)) {
            getLog().debug("Skipping cognitive-java check for non-root project " + project.getArtifactId());
            return;
        }
        runCheck(executionRoot);
    }

    private void runCheck(Path executionRoot) throws MojoExecutionException, MojoFailureException {
        try {
            String[] args = reportArgs(executionRoot);
            try (var out = MavenLoggingPrintStreams.standardOut(getLog());
                 var err = MavenLoggingPrintStreams.standardErr(getLog())) {
                int exit = runner.run(args, executionRoot, out, err);
                handleExitCode(exit);
            }
        } catch (MojoFailureException | MojoExecutionException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new MojoExecutionException(ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new MojoExecutionException("Failed to execute cognitive-java", ex);
        }
    }

    private String[] reportArgs(Path executionRoot) {
        List<String> args = new ArrayList<>();
        args.add("--format");
        args.add(validatedFormat());
        if (agent) {
            args.add("--agent");
        }
        addOptionalBooleanArgument(args, "--failures-only", failuresOnly);
        addOptionalBooleanArgument(args, "--omit-redundancy", omitRedundancy);
        addRepeated(args, "--exclude", excludesProperty, excludes);
        addRepeated(args, "--exclude-class", excludeClassesProperty, excludeClasses);
        addRepeated(args, "--exclude-annotation", excludeAnnotationsProperty, excludeAnnotations);
        if (!useDefaultExclusions) {
            args.add("--use-default-exclusions=false");
        }
        if (output != null) {
            args.add("--output");
            args.add(configuredPath(executionRoot, output).toString());
        }
        args.add("--threshold");
        args.add(threshold);
        if (junitEnabled()) {
            args.add("--junit-report");
            args.add(junitReportPath(executionRoot).toString());
        }
        return args.toArray(String[]::new);
    }

    private boolean junitEnabled() {
        return switch (junit) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("cognitiveJava.junit must be true or false");
        };
    }

    private String validatedFormat() {
        String trimmed = format.trim();
        if (!format.equals(trimmed) || !VALID_FORMATS.contains(trimmed)) {
            throw new IllegalArgumentException("cognitiveJava.format must be one of: toon, json, text, junit, none");
        }
        return trimmed;
    }

    private static void addOptionalBooleanArgument(List<String> args, String option, @Nullable String value) {
        if (value == null) {
            return;
        }
        args.add(option + "=" + value);
    }

    private static void addRepeated(List<String> args, String option, @Nullable String propertyValue, List<String> values) {
        for (String value : configuredValues(propertyValue, values)) {
            args.add(option);
            args.add(value);
        }
    }

    private static List<String> configuredValues(@Nullable String propertyValue, List<String> values) {
        List<String> configured = new ArrayList<>();
        configured.addAll(commaSeparatedPropertyValues(propertyValue));
        configured.addAll(configuredListValues(values));
        return configured;
    }

    private static List<String> commaSeparatedPropertyValues(@Nullable String propertyValue) {
        if (propertyValue == null) {
            return List.of();
        }
        return splitEscapedCommaValues(propertyValue).stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static List<String> splitEscapedCommaValues(String propertyValue) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < propertyValue.length(); index++) {
            char character = propertyValue.charAt(index);
            if (character == '\\' && index + 1 < propertyValue.length() && propertyValue.charAt(index + 1) == ',') {
                current.append(',');
                index++;
                continue;
            }
            if (character == ',') {
                values.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(character);
        }
        values.add(current.toString());
        return values;
    }

    private static List<String> configuredListValues(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private Path junitReportPath(Path executionRoot) {
        File configured = junitReport;
        if (configured != null) {
            return configuredPath(executionRoot, configured);
        }
        return executionRoot.resolve("target/cognitive-java/TEST-cognitive-java.xml").normalize();
    }

    private static Path configuredPath(Path executionRoot, File configured) {
        Path path = configured.toPath().normalize();
        return path.isAbsolute() ? path : executionRoot.resolve(path).normalize();
    }

    private static void handleExitCode(int exit) throws MojoExecutionException, MojoFailureException {
        if (exit == 2) {
            throw new MojoFailureException("cognitive-java threshold exceeded");
        }
        if (exit != 0) {
            throw new MojoExecutionException("cognitive-java check failed with exit " + exit);
        }
    }

    private Path executionRoot() {
        java.io.File multiModuleRoot = session().getRequest().getMultiModuleProjectDirectory();
        if (multiModuleRoot != null) {
            return multiModuleRoot.toPath().normalize();
        }
        return project().getBasedir().toPath().normalize();
    }

    private MavenSession session() {
        return Objects.requireNonNull(session, "Maven session must be injected");
    }

    private MavenProject project() {
        return Objects.requireNonNull(project, "Maven project must be injected");
    }

    @FunctionalInterface
    interface CognitiveJavaRunner {
        int run(String[] args, Path projectRoot, PrintStream out, PrintStream err) throws Exception;
    }
}

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

@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, aggregator = true, threadSafe = true)
public class CognitiveJavaCheckMojo extends AbstractMojo {

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
            try (var out = MavenLoggingPrintStreams.standardOut(getLog());
                 var err = MavenLoggingPrintStreams.standardErr(getLog())) {
                int exit = runner.run(reportArgs(executionRoot), executionRoot, out, err);
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
        args.add(format);
        if (agent) {
            args.add("--agent");
        }
        addOptionalBooleanArgument(args, "--failures-only", failuresOnly);
        addOptionalBooleanArgument(args, "--omit-redundancy", omitRedundancy);
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

    private static void addOptionalBooleanArgument(List<String> args, String option, @Nullable String value) {
        if (value == null) {
            return;
        }
        args.add(option + "=" + value);
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

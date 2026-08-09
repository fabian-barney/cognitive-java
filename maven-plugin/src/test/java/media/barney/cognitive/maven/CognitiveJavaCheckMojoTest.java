package media.barney.cognitive.maven;

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CognitiveJavaCheckMojoTest {

    @TempDir
    Path tempDir;

    @Test
    void skipsNonRootProject() throws Exception {
        Path root = tempDir.resolve("root");
        Path module = root.resolve("module-a");
        Files.createDirectories(module);

        RecordingRunner runner = new RecordingRunner();
        CognitiveJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root"), project(module, "module-a")), root));
        setField(mojo, "project", project(module, "module-a"));

        mojo.execute();

        assertFalse(runner.invoked);
    }

    @Test
    void usesDefaultReportConfiguration() throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        RecordingRunner runner = new RecordingRunner();
        CognitiveJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));

        mojo.execute();

        assertTrue(runner.invoked);
        assertEquals(root, runner.projectRoot);
        assertEquals(List.of(
                "--format",
                "none",
                "--threshold",
                "8",
                "--junit-report",
                root.resolve("target/cognitive-java/TEST-cognitive-java.xml").toString()
        ), List.of(runner.args));
    }

    @Test
    void usesConfiguredReportControls() throws Exception {
        Path root = tempDir.resolve("root");
        Path output = root.resolve("target/cognitive-java/report.json");

        RecordingRunner runner = new RecordingRunner();
        CognitiveJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));
        setField(mojo, "format", "json");
        setField(mojo, "agent", true);
        setField(mojo, "failuresOnly", "false");
        setField(mojo, "omitRedundancy", "true");
        setField(mojo, "output", output.toFile());
        setField(mojo, "junitReport", root.resolve("target/cognitive-java/custom-junit.xml").toFile());
        setField(mojo, "threshold", "15");

        mojo.execute();

        assertEquals(List.of(
                "--format",
                "json",
                "--agent",
                "--failures-only=false",
                "--omit-redundancy=true",
                "--output",
                output.toString(),
                "--threshold",
                "15",
                "--junit-report",
                root.resolve("target/cognitive-java/custom-junit.xml").toString()
        ), List.of(runner.args));
    }

    @Test
    void usesConfiguredExclusionControls() throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        RecordingRunner runner = new RecordingRunner();
        CognitiveJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));
        setField(mojo, "excludesProperty", "module-a/**, module-b/**");
        setField(mojo, "excludes", List.of("**/custom/**"));
        setField(mojo, "excludeClassesProperty", ".*MapperImpl$, demo.Name{1\\,3}$, demo.\\d+$, trailing\\");
        setField(mojo, "excludeClasses", List.of("demo.Other{1,3}$"));
        setField(mojo, "excludeAnnotationsProperty", "Generated");
        setField(mojo, "excludeAnnotations", List.of("com.acme.Generated"));
        setField(mojo, "useDefaultExclusions", false);

        mojo.execute();

        assertEquals(List.of(
                "--format",
                "none",
                "--exclude",
                "module-a/**",
                "--exclude",
                "module-b/**",
                "--exclude",
                "**/custom/**",
                "--exclude-class",
                ".*MapperImpl$",
                "--exclude-class",
                "demo.Name{1,3}$",
                "--exclude-class",
                "demo.\\d+$",
                "--exclude-class",
                "trailing\\",
                "--exclude-class",
                "demo.Other{1,3}$",
                "--exclude-annotation",
                "Generated",
                "--exclude-annotation",
                "com.acme.Generated",
                "--use-default-exclusions=false",
                "--threshold",
                "8",
                "--junit-report",
                root.resolve("target/cognitive-java/TEST-cognitive-java.xml").toString()
        ), List.of(runner.args));
    }

    @Test
    void usesConfiguredSourceRoots() throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root.resolve("src/custom/java"));
        Files.createDirectories(root.resolve("module-a/generated/java"));

        RecordingRunner runner = new RecordingRunner();
        CognitiveJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));
        setField(mojo, "sourceRootsProperty", "src/custom/java");
        setField(mojo, "sourceRoots", List.of("module-a/generated/java"));

        mojo.execute();

        assertEquals(List.of(
                "--format",
                "none",
                "--source-root",
                "src/custom/java",
                "--source-root",
                "module-a/generated/java",
                "--threshold",
                "8",
                "--junit-report",
                root.resolve("target/cognitive-java/TEST-cognitive-java.xml").toString()
        ), List.of(runner.args));
    }

    @Test
    void disablesJunitReport() throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        RecordingRunner runner = new RecordingRunner();
        CognitiveJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));
        setField(mojo, "junit", "false");

        mojo.execute();

        assertEquals(List.of("--format", "none", "--threshold", "8"), List.of(runner.args));
    }

    @Test
    void resolvesConfiguredRelativeReportPathsAgainstExecutionRoot() throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        RecordingRunner runner = new RecordingRunner();
        CognitiveJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));
        setField(mojo, "output", Path.of("target/cognitive-java/report.json").toFile());
        setField(mojo, "junitReport", Path.of("target/cognitive-java/custom-junit.xml").toFile());

        mojo.execute();

        assertEquals(List.of(
                "--format",
                "none",
                "--output",
                root.resolve("target/cognitive-java/report.json").toString(),
                "--threshold",
                "8",
                "--junit-report",
                root.resolve("target/cognitive-java/custom-junit.xml").toString()
        ), List.of(runner.args));
    }

    @Test
    void fallsBackToProjectBasedirWhenSessionHasNoMultiModuleRoot() throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        RecordingRunner runner = new RecordingRunner();
        CognitiveJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), null));
        setField(mojo, "project", project(root, "root"));

        mojo.execute();

        assertEquals(root, runner.projectRoot);
    }

    @Test
    void loggerStreamsCarryUserVisibleOutputWithoutWritingDirectlyToSystemStreams() throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        RecordingRunner runner = new RecordingRunner();
        runner.stdoutLine = "primary report";
        runner.stderrLine = "diagnostic";
        RecordingLog log = new RecordingLog();
        CognitiveJavaCheckMojo mojo = mojo(runner, log);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));

        ByteArrayOutputStream systemOut = new ByteArrayOutputStream();
        ByteArrayOutputStream systemErr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        try {
            System.setOut(new PrintStream(systemOut, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(systemErr, true, StandardCharsets.UTF_8));
            mojo.execute();
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        assertEquals(List.of("primary report"), log.infoMessages);
        assertEquals(List.of("diagnostic"), log.warnMessages);
        assertEquals("", utf8(systemOut));
        assertEquals("", utf8(systemErr));
    }

    @Test
    void exitCodeTwoFailsTheBuild() throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        RecordingRunner runner = new RecordingRunner();
        runner.exitCode = 2;
        CognitiveJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));

        MojoFailureException ex = assertThrows(MojoFailureException.class, mojo::execute);

        assertEquals("cognitive-java threshold exceeded", ex.getMessage());
    }

    @Test
    void nonZeroExitCodeRaisesExecutionError() throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        RecordingRunner runner = new RecordingRunner();
        runner.exitCode = 1;
        CognitiveJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));

        MojoExecutionException ex = assertThrows(MojoExecutionException.class, mojo::execute);

        assertEquals("cognitive-java check failed with exit 1", ex.getMessage());
    }

    @Test
    void invalidJunitPropertyFailsWithUsefulMessage() throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        CognitiveJavaCheckMojo mojo = mojo(new RecordingRunner());
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));
        setField(mojo, "junit", "sometimes");

        MojoExecutionException ex = assertThrows(MojoExecutionException.class, mojo::execute);

        assertEquals("cognitiveJava.junit must be true or false", ex.getMessage());
    }

    @Test
    void invalidFormatIsLoggedAndFailsExecution() throws Exception {
        Path root = writeSimpleSource();

        RecordingLog log = new RecordingLog();
        CognitiveJavaCheckMojo mojo = realMojo(log);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));
        setField(mojo, "format", "yaml");

        MojoExecutionException ex = assertThrows(MojoExecutionException.class, mojo::execute);

        assertEquals("cognitiveJava.format must be one of: toon, json, text, junit, none", ex.getMessage());
        assertTrue(log.warnMessages.isEmpty());
        assertTrue(log.infoMessages.isEmpty());
    }

    @Test
    void invalidThresholdIsLoggedAndFailsExecution() throws Exception {
        Path root = writeSimpleSource();

        RecordingLog log = new RecordingLog();
        CognitiveJavaCheckMojo mojo = realMojo(log);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));
        setField(mojo, "threshold", "zero");

        MojoExecutionException ex = assertThrows(MojoExecutionException.class, mojo::execute);

        assertEquals("cognitive-java check failed with exit 1", ex.getMessage());
        assertTrue(log.warnMessages.stream().anyMatch(message -> message.contains("--threshold requires a positive integer")));
        assertTrue(log.infoMessages.stream().anyMatch(message -> message.contains("Usage:")));
    }

    @Test
    void invalidBooleanPropertyIsLoggedAndFailsExecution() throws Exception {
        Path root = writeSimpleSource();

        RecordingLog log = new RecordingLog();
        CognitiveJavaCheckMojo mojo = realMojo(log);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));
        setField(mojo, "failuresOnly", "sometimes");

        MojoExecutionException ex = assertThrows(MojoExecutionException.class, mojo::execute);

        assertEquals("cognitive-java check failed with exit 1", ex.getMessage());
        assertTrue(log.warnMessages.stream().anyMatch(message -> message.contains("--failures-only requires true or false when assigned")));
        assertTrue(log.infoMessages.stream().anyMatch(message -> message.contains("Usage:")));
    }

    @Test
    void invalidReportPathIsLoggedAndFailsExecution() throws Exception {
        Path root = writeSimpleSource();

        RecordingLog log = new RecordingLog();
        CognitiveJavaCheckMojo mojo = realMojo(log);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));
        setField(mojo, "output", Path.of("..", "outside.txt").toFile());

        MojoExecutionException ex = assertThrows(MojoExecutionException.class, mojo::execute);

        assertEquals("cognitive-java check failed with exit 1", ex.getMessage());
        assertTrue(log.warnMessages.stream().anyMatch(message -> message.contains("--output must stay inside the project root")));
    }

    @Test
    void unexpectedExceptionsAreWrapped() throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        RecordingRunner runner = new RecordingRunner();
        runner.failure = new IllegalStateException("boom");
        CognitiveJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));

        MojoExecutionException ex = assertThrows(MojoExecutionException.class, mojo::execute);

        assertEquals("Failed to execute cognitive-java", ex.getMessage());
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    private Path writeSimpleSource() throws Exception {
        Path root = tempDir.resolve("root");
        Path sourceRoot = root.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Files.writeString(sourceRoot.resolve("Sample.java"), """
                package demo;

                class Sample {
                    int alpha(boolean value) {
                        if (value) {
                            return 1;
                        }
                        return 0;
                    }
                }
                """);
        return root;
    }

    private static MavenSession session(List<MavenProject> projects, @Nullable Path multiModuleRoot) {
        DefaultMavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setMultiModuleProjectDirectory(multiModuleRoot == null ? null : multiModuleRoot.toFile());

        List<MavenProject> reactorProjects = new ArrayList<>(projects);
        MavenSession session = new MavenSession(null, request, new DefaultMavenExecutionResult(), reactorProjects);
        session.setProjects(reactorProjects);
        return session;
    }

    private static MavenProject project(Path basedir, String artifactId) {
        MavenProject project = new MavenProject();
        project.setArtifactId(artifactId);
        project.setFile(basedir.resolve("pom.xml").toFile());
        return project;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static CognitiveJavaCheckMojo mojo(CognitiveJavaCheckMojo.CognitiveJavaRunner runner) {
        return mojo(runner, new RecordingLog());
    }

    private static CognitiveJavaCheckMojo mojo(CognitiveJavaCheckMojo.CognitiveJavaRunner runner, RecordingLog log) {
        CognitiveJavaCheckMojo mojo = new CognitiveJavaCheckMojo(runner);
        mojo.setLog(log);
        return mojo;
    }

    private static CognitiveJavaCheckMojo realMojo(RecordingLog log) {
        CognitiveJavaCheckMojo mojo = new CognitiveJavaCheckMojo();
        mojo.setLog(log);
        return mojo;
    }

    private static String utf8(ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
    }

    private static final class RecordingRunner implements CognitiveJavaCheckMojo.CognitiveJavaRunner {
        private boolean invoked;
        private String[] args = new String[0];
        private @Nullable Path projectRoot;
        private int exitCode;
        private @Nullable Exception failure;
        private @Nullable String stdoutLine;
        private @Nullable String stderrLine;

        @Override
        public int run(String[] args, Path projectRoot, PrintStream out, PrintStream err) throws Exception {
            invoked = true;
            this.args = args.clone();
            this.projectRoot = projectRoot;
            if (stdoutLine != null) {
                out.println(stdoutLine);
            }
            if (stderrLine != null) {
                err.println(stderrLine);
            }
            if (failure != null) {
                throw failure;
            }
            return exitCode;
        }
    }

    private static final class RecordingLog implements Log {
        private final List<String> debugMessages = new ArrayList<>();
        private final List<String> infoMessages = new ArrayList<>();
        private final List<String> warnMessages = new ArrayList<>();
        private final List<String> errorMessages = new ArrayList<>();

        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public void debug(CharSequence content) {
            debugMessages.add(content.toString());
        }

        @Override
        public void debug(CharSequence content, Throwable error) {
            debug(content);
        }

        @Override
        public void debug(Throwable error) {
            debug(error.toString());
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(CharSequence content) {
            infoMessages.add(content.toString());
        }

        @Override
        public void info(CharSequence content, Throwable error) {
            info(content);
        }

        @Override
        public void info(Throwable error) {
            info(error.toString());
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void warn(CharSequence content) {
            warnMessages.add(content.toString());
        }

        @Override
        public void warn(CharSequence content, Throwable error) {
            warn(content);
        }

        @Override
        public void warn(Throwable error) {
            warn(error.toString());
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void error(CharSequence content) {
            errorMessages.add(content.toString());
        }

        @Override
        public void error(CharSequence content, Throwable error) {
            error(content);
        }

        @Override
        public void error(Throwable error) {
            error(error.toString());
        }
    }
}

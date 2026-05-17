package media.barney.cognitive.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CognitiveJavaGradlePluginTest {

    @TempDir
    Path tempDir;

    @Test
    void applyRegistersVerificationTaskForJavaProjects() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path source = writeSource(projectRoot);
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CognitiveJavaGradlePlugin.class);

        CognitiveJavaCheckTask checkTask =
                (CognitiveJavaCheckTask) project.getTasks().getByName("cognitive-java-check");
        CognitiveJavaExtension extension = project.getExtensions().getByType(CognitiveJavaExtension.class);

        assertEquals("verification", checkTask.getGroup());
        assertEquals("Runs the cognitive-java Cognitive Complexity gate.", checkTask.getDescription());
        assertEquals(15, extension.getThreshold().get());
        assertFalse(extension.getAgent().get());
        assertEquals("none", extension.getFormat().get());
        assertFalse(extension.getFailuresOnly().get());
        assertFalse(extension.getOmitRedundancy().get());
        assertFalse(extension.getOutput().isPresent());
        assertTrue(extension.getJunit().get());
        assertTrue(extension.getJunitReport().get().getAsFile().toPath().normalize().toString()
                .replace('\\', '/')
                .endsWith("build/reports/cognitive-java/TEST-cognitive-java.xml"));
        assertEquals(15, checkTask.getThreshold().get());
        assertFalse(checkTask.getAgent().get());
        assertEquals("none", checkTask.getFormat().get());
        assertFalse(checkTask.getFailuresOnly().get());
        assertFalse(checkTask.getOmitRedundancy().get());
        assertFalse(checkTask.getOutput().isPresent());
        assertTrue(checkTask.getJunit().get());
        assertTrue(checkTask.getJunitReport().get().getAsFile().toPath().normalize().toString()
                .replace('\\', '/')
                .endsWith("build/reports/cognitive-java/TEST-cognitive-java.xml"));
        assertTrue(checkTask.getJunitReportOutput().isPresent());
        assertTrue(checkTask.getAnalysisSources().getFiles().contains(source.toFile()));
        Set<String> dependencyNames = checkTask.getTaskDependencies().getDependencies(checkTask).stream()
                .map(Task::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of(), dependencyNames);
    }

    @Test
    void configuredExtensionReportControlsFlowToCheckTask() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CognitiveJavaGradlePlugin.class);
        CognitiveJavaExtension extension = project.getExtensions().getByType(CognitiveJavaExtension.class);
        Path output = tempDir.resolve("build/reports/cognitive-java/report.json");
        Path junitReport = tempDir.resolve("build/reports/cognitive-java/custom-junit.xml");
        extension.getThreshold().set(9);
        extension.getFormat().set("json");
        extension.getAgent().set(true);
        extension.getFailuresOnly().set(false);
        extension.getOmitRedundancy().set(true);
        extension.getOutput().fileValue(output.toFile());
        extension.getJunit().set(false);
        extension.getJunitReport().fileValue(junitReport.toFile());

        CognitiveJavaCheckTask checkTask =
                (CognitiveJavaCheckTask) project.getTasks().getByName("cognitive-java-check");

        assertEquals(9, checkTask.getThreshold().get());
        assertEquals("json", checkTask.getFormat().get());
        assertTrue(checkTask.getAgent().get());
        assertFalse(checkTask.getFailuresOnly().get());
        assertTrue(checkTask.getOmitRedundancy().get());
        assertEquals(output.normalize(), checkTask.getOutput().get().getAsFile().toPath().normalize());
        assertFalse(checkTask.getJunit().get());
        assertEquals(junitReport.normalize(), checkTask.getJunitReport().get().getAsFile().toPath().normalize());
        assertFalse(checkTask.getJunitReportOutput().isPresent());
    }

    @Test
    void directlyRegisteredCheckTaskHasReportControlDefaults() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        CognitiveJavaCheckTask checkTask =
                project.getTasks().register("custom-cognitive-java-check", CognitiveJavaCheckTask.class).get();

        assertEquals(15, checkTask.getThreshold().get());
        assertFalse(checkTask.getAgent().get());
        assertEquals("none", checkTask.getFormat().get());
        assertFalse(checkTask.getFailuresOnly().get());
        assertFalse(checkTask.getOmitRedundancy().get());
        assertFalse(checkTask.getOutput().isPresent());
        assertTrue(checkTask.getJunit().get());
        assertTrue(checkTask.getJunitReport().get().getAsFile().toPath().normalize().toString()
                .replace('\\', '/')
                .endsWith("build/reports/cognitive-java/custom-cognitive-java-check/TEST-cognitive-java.xml"));
        assertTrue(checkTask.getJunitReportOutput().isPresent());
    }

    @Test
    void agentExtensionComposesPrimaryDefaultsWhenControlsAreUnset() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CognitiveJavaGradlePlugin.class);
        project.getExtensions().getByType(CognitiveJavaExtension.class).getAgent().set(true);

        CognitiveJavaCheckTask checkTask =
                (CognitiveJavaCheckTask) project.getTasks().getByName("cognitive-java-check");
        CognitiveJavaExtension extension = project.getExtensions().getByType(CognitiveJavaExtension.class);

        assertEquals("toon", extension.getFormat().get());
        assertTrue(extension.getFailuresOnly().get());
        assertTrue(extension.getOmitRedundancy().get());
        assertEquals("toon", checkTask.getFormat().get());
        assertTrue(checkTask.getFailuresOnly().get());
        assertTrue(checkTask.getOmitRedundancy().get());
    }

    @Test
    void taskAgentFalseOverridesExtensionAgentComposedDefaults() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CognitiveJavaGradlePlugin.class);
        project.getExtensions().getByType(CognitiveJavaExtension.class).getAgent().set(true);
        CognitiveJavaCheckTask checkTask =
                (CognitiveJavaCheckTask) project.getTasks().getByName("cognitive-java-check");
        checkTask.getAgent().set(false);

        assertEquals("none", checkTask.getFormat().get());
        assertFalse(checkTask.getFailuresOnly().get());
        assertFalse(checkTask.getOmitRedundancy().get());
    }

    @Test
    void configuredTaskReportControlsOverrideExtensionDefaults() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CognitiveJavaGradlePlugin.class);
        CognitiveJavaCheckTask checkTask =
                (CognitiveJavaCheckTask) project.getTasks().getByName("cognitive-java-check");
        Path output = tempDir.resolve("build/reports/cognitive-java/task-report.json");
        Path junitReport = tempDir.resolve("build/reports/cognitive-java/task-junit.xml");
        checkTask.getThreshold().set(8);
        checkTask.getAgent().set(true);
        checkTask.getFormat().set("json");
        checkTask.getFailuresOnly().set(false);
        checkTask.getOmitRedundancy().set(true);
        checkTask.getOutput().fileValue(output.toFile());
        checkTask.getJunit().set(false);
        checkTask.getJunitReport().fileValue(junitReport.toFile());

        assertEquals(8, checkTask.getThreshold().get());
        assertEquals("json", checkTask.getFormat().get());
        assertTrue(checkTask.getAgent().get());
        assertFalse(checkTask.getFailuresOnly().get());
        assertTrue(checkTask.getOmitRedundancy().get());
        assertEquals(output.normalize(), checkTask.getOutput().get().getAsFile().toPath().normalize());
        assertFalse(checkTask.getJunit().get());
        assertEquals(junitReport.normalize(), checkTask.getJunitReport().get().getAsFile().toPath().normalize());
    }

    @Test
    void runCheckAnalyzesConfiguredSourcesAndWritesDefaultJunit() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path source = writeSource(projectRoot);

        CognitiveJavaCheckTask task = project.getTasks().register("cognitive-java-check", CognitiveJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getAnalysisSources().from(source);

        task.runCheck();

        Path junitReport = projectRoot.resolve("build/reports/cognitive-java/TEST-cognitive-java.xml");
        assertTrue(Files.exists(junitReport));
        assertTrue(Files.readString(junitReport).contains("<testsuites tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\""));
        assertTrue(Files.exists(executionMarkerPath(task)));
    }

    @Test
    void movedJunitReportDeletesOwnedRememberedDefaultSidecar() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Path defaultJunitReport = projectRoot.resolve("build/reports/cognitive-java/TEST-cognitive-java.xml");
        Path customJunitReport = projectRoot.resolve("build/reports/cognitive-java/custom-junit.xml");
        CognitiveJavaCheckTask firstTask = newCheckTask(projectRoot);
        firstTask.runCheck();
        assertTrue(Files.exists(defaultJunitReport));

        CognitiveJavaCheckTask secondTask = newCheckTask(projectRoot);
        secondTask.getJunitReport().fileValue(customJunitReport.toFile());

        secondTask.runCheck();

        assertTrue(Files.exists(customJunitReport));
        assertFalse(Files.exists(defaultJunitReport));
    }

    @Test
    void disabledJunitDeletesOwnedRememberedDefaultSidecar() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Path defaultJunitReport = projectRoot.resolve("build/reports/cognitive-java/TEST-cognitive-java.xml");
        CognitiveJavaCheckTask firstTask = newCheckTask(projectRoot);
        firstTask.runCheck();
        assertTrue(Files.exists(defaultJunitReport));

        CognitiveJavaCheckTask secondTask = newCheckTask(projectRoot);
        secondTask.getJunit().set(false);

        secondTask.runCheck();

        assertFalse(Files.exists(defaultJunitReport));
    }

    @Test
    void primaryOutputCleanupFollowsConfiguredOutputPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path oldOutput = projectRoot.resolve("build/reports/cognitive-java/old-report.json");
        Path newOutput = projectRoot.resolve("build/reports/cognitive-java/new-report.json");

        CognitiveJavaCheckTask firstTask = newCheckTask(projectRoot);
        firstTask.getFormat().set("json");
        firstTask.getOutput().fileValue(oldOutput.toFile());
        firstTask.runCheck();
        assertTrue(Files.exists(oldOutput));

        CognitiveJavaCheckTask secondTask = newCheckTask(projectRoot);
        secondTask.getFormat().set("json");
        secondTask.getOutput().fileValue(newOutput.toFile());
        secondTask.runCheck();
        assertFalse(Files.exists(oldOutput));
        assertTrue(Files.exists(newOutput));

        CognitiveJavaCheckTask thirdTask = newCheckTask(projectRoot);
        thirdTask.runCheck();
        assertFalse(Files.exists(newOutput));
    }

    @Test
    void runCheckRejectsCollidingReportPaths() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        Path collision = projectRoot.resolve("build/reports/cognitive-java/collision.xml");
        task.getOutput().fileValue(collision.toFile());
        task.getJunitReport().fileValue(collision.toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output and junitReport must not point to the same file"));
    }

    @Test
    void runCheckRejectsDirectoryReportPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path outputDirectory = projectRoot.resolve("reports/output.json");
        Files.createDirectories(outputDirectory);
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        task.getFormat().set("json");
        task.getOutput().fileValue(outputDirectory.toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a directory"));
    }

    @Test
    void runCheckRejectsFilesystemRootReportPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        task.getFormat().set("json");
        task.getOutput().fileValue(projectRoot.getRoot().toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a filesystem root"));
    }

    @Test
    void runCheckRejectsProjectRootReportPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        task.getFormat().set("json");
        task.getOutput().fileValue(projectRoot.toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to the Gradle project root"));
    }

    @Test
    void runCheckRejectsReportPathOutsideProjectRoot() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        task.getFormat().set("json");
        task.getOutput().fileValue(projectRoot.resolveSibling("outside-report.json").toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must stay inside the Gradle project root"));
    }

    @Test
    void runCheckRejectsReportPathOutsideAnalysisRoot() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path analysisRoot = projectRoot.resolve("analysis");
        Files.createDirectories(analysisRoot);
        Path source = analysisRoot.resolve("src/main/java/demo/Sample.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, sampleSource());
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        task.getAnalysisRoot().fileValue(analysisRoot.toFile());
        task.getAnalysisSources().setFrom(source);
        task.getFormat().set("json");
        task.getOutput().fileValue(projectRoot.resolve("build/reports/cognitive-java/outside-analysis.json").toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must stay inside the analysisRoot root"));
    }

    @Test
    void runCheckRejectsInternalExecutionMarkerPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        task.getFormat().set("json");
        task.getOutput().fileValue(projectRoot.resolve("build/tmp/cognitive-java/cognitive-java-check/execution.marker").toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a cognitive-java internal task file"));
    }

    @Test
    void runCheckRejectsInternalStatePathAlias() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeTrue(!isWindows(), "This alias test requires filesystem symlinks");
        Path statePath = projectRoot.resolve(".gradle/cognitive-java/root/other-task/primary-output.path");
        Files.createDirectories(statePath.getParent());
        Files.writeString(statePath, "state");
        Path alias = Files.createSymbolicLink(projectRoot.resolve("state-report.xml"), statePath);
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        task.getFormat().set("json");
        task.getOutput().fileValue(alias.toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a cognitive-java internal task file"));
    }

    @Test
    void reportStateLockIsSharedAcrossTasksInProjectCache() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        CognitiveJavaCheckTask firstTask = newCheckTask(projectRoot, "first-cognitive-java-check");
        CognitiveJavaCheckTask secondTask = newCheckTask(projectRoot, "second-cognitive-java-check");
        Path expectedLockPath = projectRoot.resolve(".gradle/cognitive-java/state.lock").toAbsolutePath().normalize();

        assertEquals(expectedLockPath, stateLockPath(firstTask));
        assertEquals(expectedLockPath, stateLockPath(secondTask));
    }

    @Test
    void rememberedStateUsesGradleProjectCacheDir() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Path projectCacheDir = projectRoot.resolve("custom-project-cache");
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        project.getGradle().getStartParameter().setProjectCacheDir(projectCacheDir.toFile());
        CognitiveJavaCheckTask task = project.getTasks().register("cognitive-java-check", CognitiveJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getAnalysisSources().from(writeSource(projectRoot));

        task.runCheck();

        Path statePath = junitReportStatePath(task);
        assertTrue(Files.exists(statePath));
        assertTrue(statePath.startsWith(projectCacheDir.resolve("cognitive-java")));
        assertFalse(Files.exists(projectRoot.resolve(".gradle/cognitive-java/root/cognitive-java-check/junit-report.path")));
    }

    @Test
    void likelyCaseInsensitiveFallbackOnlyMatchesWindows() {
        String original = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Windows 11");
            assertTrue(CognitiveJavaCheckTask.isLikelyCaseInsensitiveOs());
            System.setProperty("os.name", "Mac OS X");
            assertFalse(CognitiveJavaCheckTask.isLikelyCaseInsensitiveOs());
            System.setProperty("os.name", "Linux");
            assertFalse(CognitiveJavaCheckTask.isLikelyCaseInsensitiveOs());
        } finally {
            if (original == null) {
                System.clearProperty("os.name");
            } else {
                System.setProperty("os.name", original);
            }
        }
    }

    private Path writeSource(Path projectRoot) throws IOException {
        Path source = projectRoot.resolve("src/main/java/demo/Sample.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, sampleSource());
        return source;
    }

    private String sampleSource() {
        return """
                package demo;

                class Sample {
                    int alpha(boolean value) {
                        if (value) {
                            return 1;
                        }
                        return 0;
                    }
                }
                """;
    }

    private CognitiveJavaCheckTask newCheckTask(Path projectRoot) throws IOException {
        return newCheckTask(projectRoot, "cognitive-java-check");
    }

    private CognitiveJavaCheckTask newCheckTask(Path projectRoot, String name) throws IOException {
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CognitiveJavaCheckTask task = project.getTasks().register(name, CognitiveJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getAnalysisSources().from(writeSource(projectRoot));
        return task;
    }

    private void assumeHardLinksAvailable(Path directory) throws Exception {
        Path target = Files.createTempFile(directory, ".cognitive-java-hard-link-target-", ".tmp");
        Path link = target.resolveSibling(target.getFileName() + ".link");
        try {
            Files.createLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            assumeTrue(false, "Hard links are unavailable: " + exception.getMessage());
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(target);
        }
    }

    private Path stateLockPath(CognitiveJavaCheckTask task) throws Exception {
        Method stateLockPath = CognitiveJavaCheckTask.class.getDeclaredMethod("stateLockPath");
        stateLockPath.setAccessible(true);
        return (Path) stateLockPath.invoke(task);
    }

    private Path junitReportStatePath(CognitiveJavaCheckTask task) throws Exception {
        Method junitReportStatePath = CognitiveJavaCheckTask.class.getDeclaredMethod("junitReportStatePath");
        junitReportStatePath.setAccessible(true);
        return (Path) junitReportStatePath.invoke(task);
    }

    private Path executionMarkerPath(CognitiveJavaCheckTask task) throws Exception {
        Method executionMarkerPath = CognitiveJavaCheckTask.class.getDeclaredMethod("executionMarkerPath");
        executionMarkerPath.setAccessible(true);
        return (Path) executionMarkerPath.invoke(task);
    }

    private String ownership(Path reportPath) throws Exception {
        BasicFileAttributes attributes = Files.readAttributes(reportPath, BasicFileAttributes.class);
        return "link\t"
                + attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS) + "\t"
                + attributes.size();
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}

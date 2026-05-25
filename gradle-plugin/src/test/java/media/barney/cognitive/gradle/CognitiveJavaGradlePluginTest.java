package media.barney.cognitive.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
        assertFalse(extension.getFormat().isPresent());
        assertFalse(extension.getFailuresOnly().isPresent());
        assertFalse(extension.getOmitRedundancy().isPresent());
        assertFalse(extension.getOutput().isPresent());
        assertTrue(extension.getJunit().get());
        assertTrue(extension.getJunitReport().get().getAsFile().toPath().normalize().toString()
                .replace('\\', '/')
                .endsWith("build/reports/cognitive-java/TEST-cognitive-java.xml"));
        assertEquals(List.of(), extension.getSourceRoots().get());
        assertEquals(List.of(), extension.getExcludes().get());
        assertEquals(List.of(), extension.getExcludeClasses().get());
        assertEquals(List.of(), extension.getExcludeAnnotations().get());
        assertTrue(extension.getUseDefaultExclusions().get());
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
        assertEquals(List.of(), checkTask.getSourceRoots().get());
        assertEquals(List.of(), checkTask.getExcludes().get());
        assertEquals(List.of(), checkTask.getExcludeClasses().get());
        assertEquals(List.of(), checkTask.getExcludeAnnotations().get());
        assertTrue(checkTask.getUseDefaultExclusions().get());
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
        extension.getSourceRoots().set(List.of("src/custom/java"));
        extension.getExcludes().set(List.of("module-a/**"));
        extension.getExcludeClasses().set(List.of(".*MapperImpl$"));
        extension.getExcludeAnnotations().set(List.of("Generated"));
        extension.getUseDefaultExclusions().set(false);

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
        assertEquals(List.of("src/custom/java"), checkTask.getSourceRoots().get());
        assertEquals(List.of("module-a/**"), checkTask.getExcludes().get());
        assertEquals(List.of(".*MapperImpl$"), checkTask.getExcludeClasses().get());
        assertEquals(List.of("Generated"), checkTask.getExcludeAnnotations().get());
        assertFalse(checkTask.getUseDefaultExclusions().get());
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
        assertEquals(List.of(), checkTask.getSourceRoots().get());
        assertEquals(List.of(), checkTask.getExcludes().get());
        assertEquals(List.of(), checkTask.getExcludeClasses().get());
        assertEquals(List.of(), checkTask.getExcludeAnnotations().get());
        assertTrue(checkTask.getUseDefaultExclusions().get());
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

        assertFalse(extension.getFormat().isPresent());
        assertFalse(extension.getFailuresOnly().isPresent());
        assertFalse(extension.getOmitRedundancy().isPresent());
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
    void explicitExtensionFormatIsPreservedWhenTaskAgentOverridesDefault() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CognitiveJavaGradlePlugin.class);
        CognitiveJavaExtension extension = project.getExtensions().getByType(CognitiveJavaExtension.class);
        extension.getAgent().set(true);
        extension.getFormat().set("toon");
        CognitiveJavaCheckTask checkTask =
                (CognitiveJavaCheckTask) project.getTasks().getByName("cognitive-java-check");
        checkTask.getAgent().set(false);

        assertEquals("toon", checkTask.getFormat().get());
    }

    @Test
    void explicitExtensionPrimaryFlagsArePreservedWhenTaskAgentOverridesDefault() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CognitiveJavaGradlePlugin.class);
        CognitiveJavaExtension extension = project.getExtensions().getByType(CognitiveJavaExtension.class);
        extension.getAgent().set(true);
        extension.getFailuresOnly().set(true);
        extension.getOmitRedundancy().set(true);
        CognitiveJavaCheckTask checkTask =
                (CognitiveJavaCheckTask) project.getTasks().getByName("cognitive-java-check");
        checkTask.getAgent().set(false);

        assertTrue(checkTask.getFailuresOnly().get());
        assertTrue(checkTask.getOmitRedundancy().get());
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
        checkTask.getSourceRoots().set(List.of("src/custom/java"));
        checkTask.getExcludes().set(List.of("task/**"));
        checkTask.getExcludeClasses().set(List.of("demo.Generated"));
        checkTask.getExcludeAnnotations().set(List.of("Generated"));
        checkTask.getUseDefaultExclusions().set(false);

        assertEquals(8, checkTask.getThreshold().get());
        assertEquals("json", checkTask.getFormat().get());
        assertTrue(checkTask.getAgent().get());
        assertFalse(checkTask.getFailuresOnly().get());
        assertTrue(checkTask.getOmitRedundancy().get());
        assertEquals(output.normalize(), checkTask.getOutput().get().getAsFile().toPath().normalize());
        assertFalse(checkTask.getJunit().get());
        assertEquals(junitReport.normalize(), checkTask.getJunitReport().get().getAsFile().toPath().normalize());
        assertEquals(List.of("src/custom/java"), checkTask.getSourceRoots().get());
        assertEquals(List.of("task/**"), checkTask.getExcludes().get());
        assertEquals(List.of("demo.Generated"), checkTask.getExcludeClasses().get());
        assertEquals(List.of("Generated"), checkTask.getExcludeAnnotations().get());
        assertFalse(checkTask.getUseDefaultExclusions().get());
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
    void runCheckUsesConfiguredSourceRoots() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path customRoot = projectRoot.resolve("src/custom/java/demo");
        Files.createDirectories(customRoot);
        Files.writeString(customRoot.resolve("Sample.java"), """
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

        CognitiveJavaCheckTask task = project.getTasks().register("cognitive-java-check", CognitiveJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getSourceRoots().set(List.of("src/custom/java"));

        task.runCheck();

        Path junitReport = projectRoot.resolve("build/reports/cognitive-java/TEST-cognitive-java.xml");
        assertTrue(Files.exists(junitReport));
        assertTrue(Files.readString(junitReport).contains("src/custom/java/demo/Sample.java"));
    }

    @Test
    void configuredSourceRootInputsTrackResolvedDirectories() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path customRoot = projectRoot.resolve("src/custom/java/demo");
        Files.createDirectories(customRoot);
        Files.writeString(customRoot.resolve("Sample.java"), sampleSource());

        CognitiveJavaCheckTask task = project.getTasks().register("cognitive-java-check", CognitiveJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getSourceRoots().set(List.of("src/custom/java"));

        assertEquals(
                List.of(projectRoot.resolve("src/custom/java").toAbsolutePath().normalize()),
                task.getConfiguredSourceRootInputs().get().stream()
                        .map(file -> file.toPath().toAbsolutePath().normalize())
                        .toList()
        );
    }

    @Test
    void runCheckRejectsConfiguredSourceRootsOutsideAnalysisRootWithConfiguredValue() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        task.getSourceRoots().set(List.of("../outside"));

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exceptionMessage(exception).contains("../outside"));
    }

    @Test
    void runCheckRejectsMissingConfiguredSourceRootsWithResolvedPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        task.getSourceRoots().set(List.of("src/missing/java"));

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exceptionMessage(exception).contains("src/missing/java"));
        assertTrue(exceptionMessage(exception).contains(
                projectRoot.resolve("src/missing/java").toAbsolutePath().normalize().toString()
        ));
    }

    @Test
    void runCheckRejectsConfiguredSymlinkSourceRoots() throws Exception {
        assumeTrue(!isWindows(), "This symlink test requires filesystem symlinks");
        Path projectRoot = tempDir.toRealPath();
        Path realSourceRoot = projectRoot.resolve("src/custom/java");
        Files.createDirectories(realSourceRoot);
        Path linkedSourceRoot = Files.createSymbolicLink(projectRoot.resolve("linked-source-root"), realSourceRoot);
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        task.getSourceRoots().set(List.of(linkedSourceRoot.getFileName().toString()));

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exceptionMessage(exception).contains("must not point to or traverse a symlink"));
    }

    @Test
    void runCheckAppliesConfiguredSourceExclusions() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path source = projectRoot.resolve("src/main/java/demo/Sample.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package demo;

                class Sample {
                    int alpha(boolean a, boolean b) {
                        if (a) {
                            if (b) {
                                return 2;
                            }
                            return 1;
                        }
                        return 0;
                    }
                }
                """);

        CognitiveJavaCheckTask task = project.getTasks().register("cognitive-java-check", CognitiveJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getAnalysisSources().from(source);
        task.getThreshold().set(1);
        task.getFormat().set("none");
        task.getAgent().set(false);
        task.getFailuresOnly().set(false);
        task.getOmitRedundancy().set(false);
        task.getUseDefaultExclusions().set(false);
        task.getExcludes().set(List.of("src/main/java/demo/**"));

        task.runCheck();

        Path junitReport = projectRoot.resolve("build/reports/cognitive-java/TEST-cognitive-java.xml");
        assertTrue(Files.readString(junitReport)
                .contains("<testsuites tests=\"0\" failures=\"0\" errors=\"0\" skipped=\"0\""));
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

        assertTrue(exceptionMessage(exception).contains("output and junitReport must not point to the same file"));
    }

    @Test
    void runCheckRejectsAliasedCollidingReportPaths() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Path target = projectRoot.resolve("build/reports/cognitive-java/collision.xml");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "existing");
        Path alias = target.resolveSibling("collision-alias.xml");
        Files.createLink(alias, target);
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        task.getOutput().fileValue(target.toFile());
        task.getJunitReport().fileValue(alias.toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exceptionMessage(exception).contains("output and junitReport must not point to the same file"));
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

        assertTrue(exceptionMessage(exception).contains("output must not point to a directory"));
    }

    @Test
    void runCheckRejectsFilesystemRootReportPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        task.getFormat().set("json");
        task.getOutput().fileValue(projectRoot.getRoot().toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exceptionMessage(exception).contains("output must not point to a filesystem root"));
    }

    @Test
    void runCheckRejectsFilesystemRootBeforeCollisionCheck() throws Exception {
        List<Path> roots = new ArrayList<>();
        FileSystems.getDefault().getRootDirectories().forEach(roots::add);
        assumeTrue(roots.size() >= 2, "This test requires multiple filesystem roots");
        Path projectRoot = tempDir.toRealPath();
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        task.getFormat().set("json");
        task.getOutput().fileValue(roots.get(0).toFile());
        task.getJunitReport().fileValue(roots.get(1).toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exceptionMessage(exception).contains("output must not point to a filesystem root"));
    }

    @Test
    void runCheckRejectsProjectRootReportPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        task.getFormat().set("json");
        task.getOutput().fileValue(projectRoot.toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exceptionMessage(exception).contains("output must not point to the Gradle project root"));
    }

    @Test
    void runCheckRejectsReportPathOutsideProjectRoot() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        task.getFormat().set("json");
        task.getOutput().fileValue(projectRoot.resolveSibling("outside-report.json").toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exceptionMessage(exception).contains("output must stay inside the Gradle project root"));
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

        assertTrue(exceptionMessage(exception).contains("output must stay inside the analysisRoot root"));
    }

    @Test
    void runCheckRejectsInternalExecutionMarkerPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        task.getFormat().set("json");
        task.getOutput().fileValue(projectRoot.resolve("build/tmp/cognitive-java/cognitive-java-check/execution.marker").toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exceptionMessage(exception).contains("output must not point to a cognitive-java internal task file"));
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

        assertTrue(exceptionMessage(exception).contains("output must not point to a cognitive-java internal task file"));
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
    void analysisRootPathInputTracksConfiguredRoot() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        Path analysisRoot = projectRoot.resolve("analysis-root");
        Files.createDirectories(analysisRoot);

        task.getAnalysisRoot().fileValue(analysisRoot.toFile());

        assertEquals(analysisRoot.toAbsolutePath().normalize().toString(), task.getAnalysisRootPathInput().get());
    }

    @Test
    void failedRunKeepsRememberedOutputWhenConfiguredPathMoves() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path source = writeSource(projectRoot);
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
        Files.writeString(source, """
                package demo;

                class Sample {
                    int alpha(boolean value) {
                        if (value) {
                            return 1
                        }
                        return 0;
                    }
                }
                """);

        GradleException exception = assertThrows(GradleException.class, secondTask::runCheck);

        assertTrue(exceptionMessage(exception).contains("cognitive-java-check failed with exit 1"));
        assertTrue(Files.exists(oldOutput));
        assertFalse(Files.exists(newOutput));
    }

    @Test
    void rememberChangedReportStateRecordsNewChangedReports() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        Path output = projectRoot.resolve("build/reports/cognitive-java/report.json");
        Path junit = projectRoot.resolve("build/reports/cognitive-java/report.xml");
        Object missing = invoke(task, "reportSnapshot", new Class<?>[]{Path.class}, new Object[]{null});
        Files.createDirectories(output.getParent());
        Files.writeString(output, "{}");
        Files.writeString(junit, "<testsuites/>");

        invoke(task, "rememberChangedReportState",
                new Class<?>[]{Path.class, Path.class, missing.getClass(), missing.getClass()},
                new Object[]{output, junit, missing, missing});

        assertTrue(Files.isRegularFile(outputStatePath(task)));
        assertTrue(Files.isRegularFile(junitReportStatePath(task)));
    }

    @Test
    void rememberChangedReportStateDeletesMovedUnrememberedReports() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        Path oldOutput = projectRoot.resolve("build/reports/cognitive-java/old-report.json");
        Path newOutput = projectRoot.resolve("build/reports/cognitive-java/new-report.json");
        Files.createDirectories(oldOutput.getParent());
        Files.writeString(oldOutput, "{}");
        invoke(task, "rememberOutputPath", new Class<?>[]{Path.class}, new Object[]{oldOutput});
        String rememberedState = Files.readString(outputStatePath(task));
        Object missing = invoke(task, "reportSnapshot", new Class<?>[]{Path.class}, new Object[]{null});
        Files.writeString(newOutput, "{\"fresh\":true}");

        invoke(task, "rememberChangedReportState",
                new Class<?>[]{Path.class, Path.class, missing.getClass(), missing.getClass()},
                new Object[]{newOutput, null, missing, missing});

        assertFalse(Files.exists(newOutput));
        assertEquals(rememberedState, Files.readString(outputStatePath(task)));
    }

    @Test
    void rememberedOutputDetectsOtherOwnerLink() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Path report = projectRoot.resolve("build/reports/cognitive-java/report.json");
        Files.createDirectories(report.getParent());
        Files.writeString(report, "{}");
        CognitiveJavaCheckTask firstTask = newCheckTask(projectRoot, "first-cognitive-java-check");
        CognitiveJavaCheckTask secondTask = newCheckTask(projectRoot, "second-cognitive-java-check");

        invoke(firstTask, "rememberOutputPath", new Class<?>[]{Path.class}, new Object[]{report});
        invoke(secondTask, "rememberOutputPath", new Class<?>[]{Path.class}, new Object[]{report});
        Object rememberedOutput = invoke(firstTask, "rememberedOutputPath", new Class<?>[]{}, new Object[]{});

        assertNotNull(rememberedOutput);
        assertTrue((boolean) invoke(
                firstTask,
                "hasOtherOwnerLink",
                new Class<?>[]{rememberedOutput.getClass()},
                new Object[]{rememberedOutput}
        ));
    }

    @Test
    void caseSensitivityDetectionCachesResults() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        Path candidate = projectRoot.resolve("Build/Reports/Cognitive-Java/report.xml");

        boolean detected = invokeBoolean(task, "isCaseInsensitive", Path.class, candidate);
        boolean cached = invokeBoolean(task, "isCaseInsensitive", Path.class, candidate);
        boolean rootFallback = invokeBoolean(task, "cachedRootCaseSensitivity", Path.class, projectRoot);

        assertEquals(detected, cached);
        assertEquals(CognitiveJavaCheckTask.isLikelyCaseInsensitiveOs(), rootFallback);
    }

    @Test
    void sameCaseInsensitiveFileNameRespectsCachedCaseSensitivity() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        CognitiveJavaCheckTask task = newCheckTask(projectRoot);
        Path parent = projectRoot.resolve("build/reports/cognitive-java");
        Files.createDirectories(parent);

        caseSensitivityByDirectory(task).put(parent.toAbsolutePath().normalize(), Boolean.FALSE);
        assertFalse(invokeBoolean(
                task,
                "sameCaseInsensitiveFileName",
                String.class,
                "report.xml",
                String.class,
                "REPORT.xml",
                Path.class,
                parent
        ));

        caseSensitivityByDirectory(task).put(parent.toAbsolutePath().normalize(), Boolean.TRUE);
        assertTrue(invokeBoolean(
                task,
                "sameCaseInsensitiveFileName",
                String.class,
                "report.xml",
                String.class,
                "REPORT.xml",
                Path.class,
                parent
        ));
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

    private Path outputStatePath(CognitiveJavaCheckTask task) throws Exception {
        Method outputStatePath = CognitiveJavaCheckTask.class.getDeclaredMethod("outputStatePath");
        outputStatePath.setAccessible(true);
        return (Path) outputStatePath.invoke(task);
    }

    private Path executionMarkerPath(CognitiveJavaCheckTask task) throws Exception {
        Method executionMarkerPath = CognitiveJavaCheckTask.class.getDeclaredMethod("executionMarkerPath");
        executionMarkerPath.setAccessible(true);
        return (Path) executionMarkerPath.invoke(task);
    }

    private String exceptionMessage(Throwable exception) {
        String message = exception.getMessage();
        assertNotNull(message);
        return message;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    @SuppressWarnings("unchecked")
    private Map<Path, Boolean> caseSensitivityByDirectory(CognitiveJavaCheckTask task) throws Exception {
        var field = CognitiveJavaCheckTask.class.getDeclaredField("caseSensitivityByDirectory");
        field.setAccessible(true);
        return (Map<Path, Boolean>) field.get(task);
    }

    private boolean invokeBoolean(CognitiveJavaCheckTask task,
                                  String methodName,
                                  Class<?> firstParameterType,
                                  Object firstArgument) throws Exception {
        Method method = CognitiveJavaCheckTask.class.getDeclaredMethod(methodName, firstParameterType);
        method.setAccessible(true);
        return (boolean) method.invoke(task, firstArgument);
    }

    private boolean invokeBoolean(CognitiveJavaCheckTask task,
                                  String methodName,
                                  Class<?> firstParameterType,
                                  Object firstArgument,
                                  Class<?> secondParameterType,
                                  Object secondArgument) throws Exception {
        Method method = CognitiveJavaCheckTask.class.getDeclaredMethod(
                methodName,
                firstParameterType,
                secondParameterType
        );
        method.setAccessible(true);
        return (boolean) method.invoke(task, firstArgument, secondArgument);
    }

    private boolean invokeBoolean(CognitiveJavaCheckTask task,
                                  String methodName,
                                  Class<?> firstParameterType,
                                  Object firstArgument,
                                  Class<?> secondParameterType,
                                  Object secondArgument,
                                  Class<?> thirdParameterType,
                                  Object thirdArgument) throws Exception {
        Method method = CognitiveJavaCheckTask.class.getDeclaredMethod(
                methodName,
                firstParameterType,
                secondParameterType,
                thirdParameterType
        );
        method.setAccessible(true);
        return (boolean) method.invoke(task, firstArgument, secondArgument, thirdArgument);
    }

    private Object invoke(CognitiveJavaCheckTask task,
                          String methodName,
                          Class<?>[] parameterTypes,
                          Object[] arguments) throws Exception {
        Method method = CognitiveJavaCheckTask.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(task, arguments);
    }
}

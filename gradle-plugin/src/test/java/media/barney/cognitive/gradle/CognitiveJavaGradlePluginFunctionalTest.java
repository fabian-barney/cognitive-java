package media.barney.cognitive.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CognitiveJavaGradlePluginFunctionalTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    @Test
    void singleModuleProjectRunsCognitiveJavaCheckAndWritesDefaultJunit() throws Exception {
        writeSingleModuleProject();

        BuildResult result = runBuild("cognitive-java-check");

        assertEquals(TaskOutcome.SUCCESS, result.task(":cognitive-java-check").getOutcome());
        assertTrue(Files.exists(tempDir.resolve("build/reports/cognitive-java/TEST-cognitive-java.xml")));
        assertEquals(List.of("TEST-cognitive-java.xml"), reportFileNames("build/reports/cognitive-java"));
        assertFalse(Files.exists(tempDir.resolve("build/reports/jacoco/test/jacocoTestReport.xml")));
        assertFalse(result.getOutput().contains("Cognitive Complexity Report"));
        assertFalse(result.getOutput().contains("\"status\""));
        assertFalse(result.getOutput().contains("<testsuites"));
    }

    @Test
    void rootTaskAggregatesSubprojectSourcesForMultiModuleBuilds() throws Exception {
        writeFile("settings.gradle.kts", """
                rootProject.name = "workspace"
                include("app", "lib")
                """);
        writeFile("build.gradle.kts", """
                plugins {
                    id("media.barney.cognitive-java")
                }

                subprojects {
                    apply(plugin = "java")
                }
                """);
        writeFile("app/src/main/java/demo/app/AppSample.java", """
                package demo.app;

                public class AppSample {
                    public int alpha() {
                        return 1;
                    }
                }
                """);
        writeFile("lib/src/main/java/demo/lib/LibSample.java", """
                package demo.lib;

                public class LibSample {
                    public int beta() {
                        return 2;
                    }
                }
                """);

        BuildResult result = runBuild("cognitive-java-check");

        assertEquals(TaskOutcome.SUCCESS, result.task(":cognitive-java-check").getOutcome());
        assertTrue(Files.exists(tempDir.resolve("build/reports/cognitive-java/TEST-cognitive-java.xml")));
        assertFalse(Files.exists(tempDir.resolve("app/build/reports/jacoco/test/jacocoTestReport.xml")));
        assertFalse(Files.exists(tempDir.resolve("lib/build/reports/jacoco/test/jacocoTestReport.xml")));
    }

    @Test
    void singleModuleProjectReusesConfigurationCache() throws Exception {
        writeSingleModuleProject();

        BuildResult first = runBuild("--configuration-cache", "cognitive-java-check");
        BuildResult second = runBuild("--configuration-cache", "cognitive-java-check");

        assertTrue(first.getOutput().contains("Configuration cache entry stored."));
        assertTrue(second.getOutput().contains("Configuration cache entry reused."));
        TaskOutcome outcome = second.task(":cognitive-java-check").getOutcome();
        assertTrue(outcome == TaskOutcome.SUCCESS || outcome == TaskOutcome.UP_TO_DATE);
    }

    @Test
    void configuredReportControlsReuseConfigurationCache() throws Exception {
        writeSingleModuleProject("""

                cognitiveJava {
                    threshold.set(8)
                    agent.set(true)
                    format.set("json")
                    failuresOnly.set(false)
                    omitRedundancy.set(true)
                    output.set(layout.buildDirectory.file("reports/cognitive-java/report.json"))
                    junit.set(false)
                    junitReport.set(layout.buildDirectory.file("reports/cognitive-java/custom-junit.xml"))
                }
                """);

        BuildResult first = runBuild("--configuration-cache", "cognitive-java-check");
        BuildResult second = runBuild("--configuration-cache", "cognitive-java-check");

        assertTrue(first.getOutput().contains("Configuration cache entry stored."));
        assertTrue(second.getOutput().contains("Configuration cache entry reused."));
        TaskOutcome outcome = second.task(":cognitive-java-check").getOutcome();
        assertTrue(outcome == TaskOutcome.SUCCESS || outcome == TaskOutcome.UP_TO_DATE);
        assertTrue(Files.exists(tempDir.resolve("build/reports/cognitive-java/report.json")));
        assertFalse(Files.exists(tempDir.resolve("build/reports/cognitive-java/custom-junit.xml")));
        assertFalse(Files.exists(tempDir.resolve("build/reports/cognitive-java/TEST-cognitive-java.xml")));
    }

    @Test
    void configuredReportControlsWritePrimaryReportAndFullJunitSidecar() throws Exception {
        writeSingleModuleProject("""

                cognitiveJava {
                    threshold.set(8)
                    format.set("json")
                    agent.set(true)
                    failuresOnly.set(false)
                    omitRedundancy.set(true)
                    output.set(layout.buildDirectory.file("reports/cognitive-java/report.json"))
                    junit.set(true)
                    junitReport.set(layout.buildDirectory.file("reports/cognitive-java/custom-junit.xml"))
                }
                """);

        BuildResult result = runBuild("cognitive-java-check");

        Path primary = tempDir.resolve("build/reports/cognitive-java/report.json");
        Path junit = tempDir.resolve("build/reports/cognitive-java/custom-junit.xml");
        String primaryReport = Files.readString(primary);
        String junitReport = Files.readString(junit);
        assertEquals(TaskOutcome.SUCCESS, result.task(":cognitive-java-check").getOutcome());
        assertTrue(Files.exists(primary));
        assertTrue(Files.exists(junit));
        assertFalse(Files.exists(tempDir.resolve("build/reports/cognitive-java/TEST-cognitive-java.xml")));
        assertTrue(primaryReport.contains("\"status\": \"passed\""));
        assertTrue(primaryReport.contains("\"threshold\": 8"));
        assertTrue(primaryReport.contains("\"method\": \"alpha\""));
        assertFalse(primaryReport.contains("      \"status\":"));
        assertTrue(junitReport.contains("<testsuites tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\""));
        assertTrue(junitReport.contains("<property name=\"status\" value=\"passed\"/>"));
    }

    @Test
    void disabledJunitRemovesStaleSidecarAndDoesNotWriteNewSidecar() throws Exception {
        Path defaultJunit = tempDir.resolve("build/reports/cognitive-java/TEST-cognitive-java.xml");
        writeSingleModuleProject();
        BuildResult firstResult = runBuild("cognitive-java-check");
        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":cognitive-java-check").getOutcome());
        assertTrue(Files.exists(defaultJunit));
        writeSingleModuleProject("""

                cognitiveJava {
                    junit.set(false)
                }
                """);

        BuildResult result = runBuild("cognitive-java-check");

        assertEquals(TaskOutcome.SUCCESS, result.task(":cognitive-java-check").getOutcome());
        assertFalse(Files.exists(defaultJunit));
        assertFalse(result.getOutput().contains("<testsuites"));
    }

    @Test
    void primaryOutputCleanupFollowsConfiguredOutputPath() throws Exception {
        Path oldOutput = tempDir.resolve("build/reports/cognitive-java/old-report.json");
        Path newOutput = tempDir.resolve("build/reports/cognitive-java/new-report.json");
        writeSingleModuleProject("""

                cognitiveJava {
                    format.set("json")
                    output.set(layout.buildDirectory.file("reports/cognitive-java/old-report.json"))
                }
                """);
        BuildResult firstResult = runBuild("cognitive-java-check");
        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":cognitive-java-check").getOutcome());
        assertTrue(Files.exists(oldOutput));
        writeSingleModuleProject("""

                cognitiveJava {
                    format.set("json")
                    output.set(layout.buildDirectory.file("reports/cognitive-java/new-report.json"))
                }
                """);
        BuildResult secondResult = runBuild("cognitive-java-check");
        assertEquals(TaskOutcome.SUCCESS, secondResult.task(":cognitive-java-check").getOutcome());
        assertFalse(Files.exists(oldOutput));
        assertTrue(Files.exists(newOutput));
        writeSingleModuleProject();

        BuildResult thirdResult = runBuild("cognitive-java-check");

        assertEquals(TaskOutcome.SUCCESS, thirdResult.task(":cognitive-java-check").getOutcome());
        assertFalse(Files.exists(newOutput));
    }

    @Test
    void invalidReportPathDoesNotDeletePreviousOutput() throws Exception {
        Path oldOutput = tempDir.resolve("build/reports/cognitive-java/old-report.json");
        writeSingleModuleProject("""

                cognitiveJava {
                    format.set("json")
                    output.set(layout.buildDirectory.file("reports/cognitive-java/old-report.json"))
                }
                """);
        BuildResult firstResult = runBuild("cognitive-java-check");
        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":cognitive-java-check").getOutcome());
        assertTrue(Files.exists(oldOutput));
        writeSingleModuleProject("""

                cognitiveJava {
                    output.set(layout.buildDirectory.file("reports/cognitive-java/collision.xml"))
                    junitReport.set(layout.buildDirectory.file("reports/cognitive-java/collision.xml"))
                }
                """);

        BuildResult secondResult = runBuildAndFail("cognitive-java-check");

        assertTrue(secondResult.getOutput().contains("output and junitReport must not point to the same file"));
        assertTrue(Files.exists(oldOutput));
    }

    @Test
    void reportPathsMustNotUseInternalTaskFiles() throws Exception {
        writeSingleModuleProject("""

                cognitiveJava {
                    output.set(layout.buildDirectory.file("tmp/cognitive-java/cognitive-java-check/execution.marker"))
                }
                """);

        BuildResult result = runBuildAndFail("cognitive-java-check");

        assertTrue(result.getOutput().contains("output must not point to a cognitive-java internal task file"));
    }

    private BuildResult runBuild(String... arguments) {
        return gradleRunner(arguments).build();
    }

    private BuildResult runBuildAndFail(String... arguments) {
        return gradleRunner(arguments).buildAndFail();
    }

    private GradleRunner gradleRunner(String... arguments) {
        List<String> gradleArguments = new ArrayList<>();
        gradleArguments.add("-Dgradle.user.home=" + tempDir.resolve("gradle-user-home"));
        gradleArguments.add("-Dorg.gradle.daemon=false");
        gradleArguments.addAll(List.of(arguments));
        return GradleRunner.create()
                .withProjectDir(tempDir.toFile())
                .withArguments(gradleArguments)
                .withPluginClasspath();
    }

    private void writeSingleModuleProject() throws IOException {
        writeSingleModuleProject("");
    }

    private void writeSingleModuleProject(String extraBuildScript) throws IOException {
        writeFile("settings.gradle.kts", "rootProject.name = \"demo\"");
        writeFile("build.gradle.kts", """
                plugins {
                    java
                    id("media.barney.cognitive-java")
                }
                """ + extraBuildScript);
        writeFile("src/main/java/demo/Sample.java", """
                package demo;

                public class Sample {
                    public int alpha(boolean value) {
                        if (value) {
                            return 1;
                        }
                        return 0;
                    }
                }
                """);
    }

    private void writeFile(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private List<String> reportFileNames(String relativePath) throws IOException {
        try (var files = Files.list(tempDir.resolve(relativePath))) {
            return files
                    .map(path -> path.getFileName().toString())
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }
}

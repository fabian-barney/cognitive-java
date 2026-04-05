package media.barney.cognitivejava.gradle;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CognitiveJavaGradlePluginTest {

    @TempDir
    Path tempDir;

    @Test
    void applyRegistersVerificationTaskForJavaProjects() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path source = projectRoot.resolve("src/main/java/demo/Sample.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package demo;

                class Sample {
                    int alpha() {
                        return 1;
                    }
                }
                """);

        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CognitiveJavaGradlePlugin.class);

        CognitiveJavaCheckTask checkTask =
                (CognitiveJavaCheckTask) project.getTasks().getByName("cognitive-java-check");

        assertEquals("verification", checkTask.getGroup());
        assertEquals("Runs the cognitive-java Cognitive Complexity gate.", checkTask.getDescription());
        assertTrue(checkTask.getAnalysisSources().getFiles().contains(source.toFile()));
    }

    @Test
    void runCheckAnalyzesConfiguredSources() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path source = projectRoot.resolve("src/main/java/demo/Sample.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package demo;

                class Sample {
                    int alpha() {
                        return 1;
                    }
                }
                """);

        CognitiveJavaCheckTask task =
                project.getTasks().register("cognitive-java-check", CognitiveJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getAnalysisSources().from(source);

        task.runCheck();

        assertTrue(Files.exists(source));
    }
}

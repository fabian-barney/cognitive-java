package media.barney.cognitivejava.gradle;

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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CognitiveJavaGradlePluginFunctionalTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    @Test
    void singleModuleProjectRunsCognitiveJavaCheck() throws Exception {
        writeFile("settings.gradle.kts", "rootProject.name = \"demo\"");
        writeFile("build.gradle.kts", """
                plugins {
                    java
                    id("media.barney.cognitive-java")
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
                    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
                }

                tasks.test {
                    useJUnitPlatform()
                }
                """);
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
        writeFile("src/test/java/demo/SampleTest.java", """
                package demo;

                import org.junit.jupiter.api.Test;

                import static org.junit.jupiter.api.Assertions.assertEquals;

                class SampleTest {
                    @Test
                    void alphaReturnsOneForTrue() {
                        assertEquals(1, new Sample().alpha(true));
                    }
                }
                """);

        BuildResult result = runBuild("cognitive-java-check");

        assertEquals(TaskOutcome.SUCCESS, result.task(":cognitive-java-check").getOutcome());
        assertFalse(Files.exists(tempDir.resolve("build/reports/jacoco/test/jacocoTestReport.xml")));
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

                    repositories {
                        mavenCentral()
                    }

                    dependencies {
                        "testImplementation"("org.junit.jupiter:junit-jupiter:5.10.2")
                        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
                    }

                    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
                        useJUnitPlatform()
                    }
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
        writeFile("app/src/test/java/demo/app/AppSampleTest.java", """
                package demo.app;

                import org.junit.jupiter.api.Test;

                import static org.junit.jupiter.api.Assertions.assertEquals;

                class AppSampleTest {
                    @Test
                    void alphaReturnsOne() {
                        assertEquals(1, new AppSample().alpha());
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
        writeFile("lib/src/test/java/demo/lib/LibSampleTest.java", """
                package demo.lib;

                import org.junit.jupiter.api.Test;

                import static org.junit.jupiter.api.Assertions.assertEquals;

                class LibSampleTest {
                    @Test
                    void betaReturnsTwo() {
                        assertEquals(2, new LibSample().beta());
                    }
                }
                """);

        BuildResult result = runBuild("cognitive-java-check");

        assertEquals(TaskOutcome.SUCCESS, result.task(":cognitive-java-check").getOutcome());
        assertFalse(Files.exists(tempDir.resolve("app/build/reports/jacoco/test/jacocoTestReport.xml")));
        assertFalse(Files.exists(tempDir.resolve("lib/build/reports/jacoco/test/jacocoTestReport.xml")));
    }

    private BuildResult runBuild(String... arguments) {
        List<String> gradleArguments = new ArrayList<>();
        gradleArguments.add("-Dgradle.user.home=" + tempDir.resolve("gradle-user-home"));
        gradleArguments.add("-Dorg.gradle.daemon=false");
        gradleArguments.addAll(List.of(arguments));
        return GradleRunner.create()
                .withProjectDir(tempDir.toFile())
                .withArguments(gradleArguments)
                .withPluginClasspath()
                .build();
    }

    private void writeFile(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}

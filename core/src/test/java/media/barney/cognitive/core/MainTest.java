package media.barney.cognitive.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

    @TempDir
    Path tempDir;

    @Test
    void helpWritesUsageToStdout() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = Main.run(new String[]{"--help"}, tempDir, new PrintStream(out), new PrintStream(err));

        assertEquals(0, exit);
        assertTrue(utf8(out).contains("Usage:"));
        assertTrue(utf8(out).contains("cognitive-java --changed"));
    }

    @Test
    void mainProcessExitsZeroForHelp() throws Exception {
        Process process = new ProcessBuilder(
                "java",
                "-cp",
                System.getProperty("java.class.path"),
                "media.barney.cognitive.core.Main",
                "--help"
        ).directory(tempDir.toFile()).start();

        assertEquals(0, process.waitFor());
    }

    @Test
    void mainProcessExitsNonZeroForUnknownOption() throws Exception {
        Process process = new ProcessBuilder(
                "java",
                "-cp",
                System.getProperty("java.class.path"),
                "media.barney.cognitive.core.Main",
                "--build-tool"
        ).directory(tempDir.toFile()).start();

        assertEquals(1, process.waitFor());
    }

    @Test
    void explicitFileArgsAreAnalyzed() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Files.writeString(sourceRoot.resolve("Sample.java"), """
                package demo;
                class Sample {
                    int alpha(boolean a) {
                        if (a) {
                            return 1;
                        }
                        return 0;
                    }
                }
                """);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = Main.run(
                new String[]{"src/main/java/demo/Sample.java"},
                tempDir,
                new PrintStream(out),
                new PrintStream(err)
        );

        assertEquals(0, exit);
        assertTrue(utf8(out).contains("Sample"));
        assertTrue(utf8(out).contains("alpha"));
    }

    @Test
    void directoryArgAnalyzesJavaFilesUnderThatDirectorySourceTrees() throws Exception {
        Path moduleRoot = tempDir.resolve("module-a");
        Path sourceRoot = moduleRoot.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Files.writeString(sourceRoot.resolve("Sample.java"), """
                package demo;
                class Sample {
                    int alpha(boolean a, boolean b) {
                        if (a && b) {
                            return 1;
                        }
                        return 0;
                    }
                }
                """);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = Main.run(new String[]{"module-a"}, tempDir, new PrintStream(out), new PrintStream(err));

        assertEquals(0, exit);
        assertTrue(utf8(out).contains("Sample"));
        assertTrue(utf8(out).contains("alpha"));
    }

    @Test
    void maxCognitiveComplexityReturnsLargestScore() {
        List<MethodMetrics> metrics = List.of(
                new MethodMetrics("alpha", "demo.Sample", 1),
                new MethodMetrics("beta", "demo.Sample", 5),
                new MethodMetrics("gamma", "demo.Sample", 3)
        );

        assertEquals(5, Main.maxCognitiveComplexity(metrics));
    }

    private static String utf8(ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
    }
}

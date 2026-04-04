package media.barney.cognitivejava.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliApplicationTest {

    @TempDir
    Path tempDir;

    @Test
    void parseErrorsReturnUsageAndExitOne() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = new CliApplication(tempDir, new PrintStream(out), new PrintStream(err))
                .execute(new String[]{"--changed", "src/main/java/demo/Sample.java"});

        assertEquals(1, exit);
        assertTrue(utf8(out).contains("Usage:"));
        assertTrue(utf8(err).contains("--changed cannot be combined with file arguments"));
    }

    @Test
    void returnsZeroWhenNoFilesAreFound() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int exit = new CliApplication(tempDir, new PrintStream(out), new PrintStream(new ByteArrayOutputStream()))
                .execute(new String[0]);

        assertEquals(0, exit);
        assertTrue(utf8(out).contains("No Java files to analyze."));
    }

    @Test
    void explicitFileProducesCognitiveComplexityReport() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Path source = sourceRoot.resolve("Sample.java");
        Files.writeString(source, """
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

        int exit = new CliApplication(tempDir, new PrintStream(out), new PrintStream(err))
                .execute(new String[]{"src/main/java/demo/Sample.java"});

        assertEquals(0, exit);
        assertTrue(utf8(out).contains("Cognitive Complexity Report"));
        assertTrue(utf8(out).contains("alpha"));
        assertTrue(utf8(out).contains("demo.Sample"));
        assertFalse(utf8(err).contains("threshold exceeded"));
    }

    @Test
    void directoryArgAnalyzesNestedSourceRoots() throws Exception {
        Path moduleRoot = tempDir.resolve("module-a");
        Path sourceRoot = moduleRoot.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Files.writeString(sourceRoot.resolve("Sample.java"), """
                package demo;

                class Sample {
                    int alpha() {
                        return switch (1) {
                            case 1 -> 1;
                            default -> 0;
                        };
                    }
                }
                """);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = new CliApplication(tempDir, new PrintStream(out), new PrintStream(err))
                .execute(new String[]{"module-a"});

        assertEquals(0, exit);
        assertTrue(utf8(out).contains("alpha"));
        assertTrue(utf8(out).contains("demo.Sample"));
    }

    @Test
    void thresholdFailureUsesCognitiveComplexityLimitTwentyFive() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Files.writeString(sourceRoot.resolve("Sample.java"), nestedIfSource(7));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = new CliApplication(tempDir, new PrintStream(out), new PrintStream(err))
                .execute(new String[]{"src/main/java/demo/Sample.java"});

        assertEquals(2, exit);
        assertTrue(utf8(out).contains("alpha"));
        assertTrue(utf8(err).contains("Cognitive Complexity threshold exceeded: 28 > 25"));
    }

    @Test
    void thresholdExceededIsStrictlyGreaterThanTwentyFive() {
        assertFalse(CliApplication.thresholdExceeded(25));
        assertTrue(CliApplication.thresholdExceeded(26));
    }

    @Test
    void syntaxErrorsFailAnalysis() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Files.writeString(sourceRoot.resolve("Broken.java"), """
                package demo;

                class Broken {
                    int alpha(boolean value) {
                        if (value) {
                            return 1;
                        // missing closing braces
                """);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = new CliApplication(tempDir, new PrintStream(out), new PrintStream(err))
                .execute(new String[]{"src/main/java/demo/Broken.java"});

        assertEquals(1, exit);
        assertFalse(utf8(out).contains("Cognitive Complexity Report"));
        assertTrue(utf8(err).contains("Broken.java"));
    }

    @Test
    void missingExplicitFileFailsFast() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = new CliApplication(tempDir, new PrintStream(out), new PrintStream(err))
                .execute(new String[]{"src/main/java/demo/Missing.java"});

        assertEquals(1, exit);
        assertFalse(utf8(out).contains("Cognitive Complexity Report"));
        assertTrue(utf8(err).contains("Path does not exist"));
    }

    @Test
    void missingExplicitDirectoryFailsFast() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = new CliApplication(tempDir, new PrintStream(out), new PrintStream(err))
                .execute(new String[]{"module-a"});

        assertEquals(1, exit);
        assertFalse(utf8(out).contains("Cognitive Complexity Report"));
        assertTrue(utf8(err).contains("Path does not exist"));
    }

    private static String nestedIfSource(int count) {
        StringBuilder builder = new StringBuilder();
        builder.append("package demo;\n\nclass Sample {\n");
        builder.append("    int alpha(boolean value) {\n");
        for (int index = 0; index < count; index++) {
            builder.append("        ".repeat(index + 1)).append("if (value) {\n");
        }
        builder.append("        ".repeat(count + 1)).append("return 1;\n");
        for (int index = count - 1; index >= 0; index--) {
            builder.append("        ".repeat(index + 1)).append("}\n");
        }
        builder.append("        return 0;\n");
        builder.append("    }\n");
        builder.append("}\n");
        return builder.toString();
    }

    private static String utf8(ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
    }
}

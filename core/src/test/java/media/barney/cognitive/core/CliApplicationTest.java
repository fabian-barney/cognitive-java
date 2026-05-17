package media.barney.cognitive.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
                .execute(new String[]{"--format", "text"});

        assertEquals(0, exit);
        assertTrue(utf8(out).contains("Cognitive Complexity Report"));
        assertTrue(utf8(out).contains("Status: passed"));
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
                .execute(new String[]{"--format", "text", "src/main/java/demo/Sample.java"});

        assertEquals(0, exit);
        assertTrue(utf8(out).contains("Cognitive Complexity Report"));
        assertTrue(utf8(out).contains("alpha"));
        assertTrue(utf8(out).contains("src/main/java/demo/Sample.java"));
        assertFalse(utf8(err).contains("threshold exceeded"));
    }

    @Test
    void outputWritesPrimaryReportInsteadOfStdout() throws Exception {
        writeSimpleSource();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = new CliApplication(tempDir, new PrintStream(out), new PrintStream(err))
                .execute(new String[]{
                        "--format=json",
                        "--output=reports/primary.json",
                        "src/main/java/demo/Sample.java"
                });

        assertEquals(0, exit);
        assertEquals("", utf8(out));
        String report = Files.readString(tempDir.resolve("reports/primary.json"));
        assertTrue(report.contains("\"status\": \"passed\""));
        assertTrue(report.contains("\"src\": \"src/main/java/demo/Sample.java\""));
    }

    @Test
    void junitSidecarIsCompleteEvenWhenPrimaryReportIsFailuresOnly() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Files.writeString(sourceRoot.resolve("Sample.java"), nestedIfSource(7));
        Files.writeString(sourceRoot.resolve("Ok.java"), """
                package demo;

                class Ok {
                    int beta() {
                        return 1;
                    }
                }
                """);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = new CliApplication(tempDir, new PrintStream(out), new PrintStream(err), fixedClock())
                .execute(new String[]{
                        "--format=json",
                        "--failures-only",
                        "--junit-report=reports/junit.xml",
                        "src/main/java/demo/Sample.java",
                        "src/main/java/demo/Ok.java"
                });

        assertEquals(2, exit);
        assertTrue(utf8(out).contains("\"method\": \"alpha\""));
        assertFalse(utf8(out).contains("\"method\": \"beta\""));
        String junit = Files.readString(tempDir.resolve("reports/junit.xml"));
        assertTrue(junit.contains("tests=\"2\""));
        assertTrue(junit.contains("alpha:4"));
        assertTrue(junit.contains("beta:4"));
        assertTrue(junit.contains("time=\"0.250000\""));
    }

    @Test
    void noneFormatWritesEmptyPrimaryFile() throws Exception {
        writeSimpleSource();

        int exit = new CliApplication(
                tempDir,
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()))
                .execute(new String[]{
                        "--format=none",
                        "--output=reports/empty.txt",
                        "src/main/java/demo/Sample.java"
                });

        assertEquals(0, exit);
        assertEquals("", Files.readString(tempDir.resolve("reports/empty.txt")));
    }

    @Test
    void reportPathsCannotEscapeProjectRoot() throws Exception {
        writeSimpleSource();

        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = new CliApplication(tempDir, new PrintStream(new ByteArrayOutputStream()), new PrintStream(err))
                .execute(new String[]{
                        "--output=../outside.txt",
                        "src/main/java/demo/Sample.java"
                });

        assertEquals(1, exit);
        assertTrue(utf8(err).contains("output must stay inside the project root"));
        assertFalse(Files.exists(tempDir.resolveSibling("outside.txt")));
    }

    @Test
    void reportPathCollisionsFailBeforeWriting() throws Exception {
        writeSimpleSource();

        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = new CliApplication(tempDir, new PrintStream(new ByteArrayOutputStream()), new PrintStream(err))
                .execute(new String[]{
                        "--output=reports/result.xml",
                        "--junit-report=reports/result.xml",
                        "src/main/java/demo/Sample.java"
                });

        assertEquals(1, exit);
        assertTrue(utf8(err).contains("output and junitReport must not point to the same file"));
        assertFalse(Files.exists(tempDir.resolve("reports/result.xml")));
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
                .execute(new String[]{"--format", "text", "module-a"});

        assertEquals(0, exit);
        assertTrue(utf8(out).contains("alpha"));
        assertTrue(utf8(out).contains("module-a/src/main/java/demo/Sample.java"));
    }

    @Test
    void changedModeAnalyzesModifiedJavaFilesUnderProductionRoots() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Path source = sourceRoot.resolve("Sample.java");
        Files.writeString(source, """
                package demo;

                class Sample {
                    int alpha() {
                        return 1;
                    }
                }
                """);

        runGit("init");
        runGit("config", "user.name", "Test User");
        runGit("config", "user.email", "test@example.com");
        runGit("add", ".");
        runGit("commit", "-m", "init");

        Files.writeString(source, """
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

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = new CliApplication(tempDir, new PrintStream(out), new PrintStream(err))
                .execute(new String[]{"--format", "text", "--changed"});

        assertEquals(0, exit);
        assertTrue(utf8(out).contains("alpha"));
        assertTrue(utf8(out).contains("src/main/java/demo/Sample.java"));
        assertEquals("", utf8(err));
    }

    @Test
    void thresholdFailureUsesCognitiveComplexityLimitFifteen() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Files.writeString(sourceRoot.resolve("Sample.java"), nestedIfSource(7));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = new CliApplication(tempDir, new PrintStream(out), new PrintStream(err))
                .execute(new String[]{"--format", "text", "src/main/java/demo/Sample.java"});

        assertEquals(2, exit);
        assertTrue(utf8(out).contains("alpha"));
        assertTrue(utf8(err).contains("Cognitive Complexity threshold exceeded: 28 > 15"));
    }

    @Test
    void thresholdExceededIsStrictlyGreaterThanFifteen() {
        assertFalse(CliApplication.thresholdExceeded(15, 15));
        assertTrue(CliApplication.thresholdExceeded(16, 15));
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

    private Path writeSimpleSource() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Path source = sourceRoot.resolve("Sample.java");
        Files.writeString(source, """
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
        return source;
    }

    private static java.util.function.LongSupplier fixedClock() {
        return new java.util.function.LongSupplier() {
            private boolean first = true;

            @Override
            public long getAsLong() {
                if (first) {
                    first = false;
                    return 1_000_000_000L;
                }
                return 1_250_000_000L;
            }
        };
    }

    private static String utf8(ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
    }

    private void runGit(String... args) throws Exception {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(tempDir.toString());
        command.addAll(List.of(args));

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        assertEquals(0, exit, output);
    }
}

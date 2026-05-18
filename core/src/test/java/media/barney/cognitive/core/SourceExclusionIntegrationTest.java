package media.barney.cognitive.core;

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

class SourceExclusionIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultGeneratedPathExclusionsPreventThresholdFailures() throws Exception {
        writeGeneratedSource();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = Main.run(
                new String[]{"--format", "json", "--threshold", "1"},
                tempDir,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8)
        );

        String report = out.toString(StandardCharsets.UTF_8);
        assertEquals(0, exit);
        assertTrue(report.contains("\"excludedFiles\": 1"));
        assertFalse(report.contains("\"method\": \"alpha\""));
    }

    @Test
    void disabledDefaultsAllowGeneratedSourcesToFailThresholds() throws Exception {
        writeGeneratedSource();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = Main.run(
                new String[]{"--format=none", "--threshold", "1", "--use-default-exclusions=false"},
                tempDir,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8)
        );

        assertEquals(2, exit);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Cognitive Complexity threshold exceeded"));
    }

    @Test
    void annotationExclusionsApplyToGeneratedClasses() throws Exception {
        writeAnnotatedGeneratedSource();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int exit = Main.run(
                new String[]{"--format", "json", "--exclude-annotation", "Generated", "--threshold", "1"},
                tempDir,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)
        );

        String report = out.toString(StandardCharsets.UTF_8);
        assertEquals(0, exit);
        assertTrue(report.contains("\"excludedClasses\": 1"));
        assertFalse(report.contains("\"method\": \"beta\""));
    }

    @Test
    void agentPrimaryReportOmitsExclusionAuditWhileJunitSidecarKeepsIt() throws Exception {
        writeGeneratedSource();
        Path junit = tempDir.resolve("reports/junit.xml");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int exit = Main.run(
                new String[]{"--agent", "--format=json", "--junit-report=reports/junit.xml"},
                tempDir,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)
        );

        assertEquals(0, exit);
        assertFalse(out.toString(StandardCharsets.UTF_8).contains("\"exclusions\""));
        assertTrue(Files.readString(junit).contains("exclusion.excludedFiles"));
    }

    private void writeGeneratedSource() throws Exception {
        Path source = tempDir.resolve("src/main/java/demo/generated/Sample.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, nestedIfSource("Sample", "alpha"));
    }

    private void writeAnnotatedGeneratedSource() throws Exception {
        Path source = tempDir.resolve("src/main/java/demo/Annotated.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package demo;

                @javax.annotation.processing.Generated("tool")
                class Annotated {
                    int beta(boolean value) {
                        if (value) {
                            if (value) {
                                return 1;
                            }
                        }
                        return 0;
                    }
                }
                """);
    }

    private static String nestedIfSource(String className, String methodName) {
        StringBuilder builder = new StringBuilder();
        builder.append("package demo.generated;\n\nclass ").append(className).append(" {\n");
        builder.append("    int ").append(methodName).append("(boolean value) {\n");
        for (int index = 0; index < 7; index++) {
            builder.append("        ".repeat(index + 1)).append("if (value) {\n");
        }
        builder.append("        ".repeat(8)).append("return 1;\n");
        for (int index = 6; index >= 0; index--) {
            builder.append("        ".repeat(index + 1)).append("}\n");
        }
        builder.append("        return 0;\n");
        builder.append("    }\n");
        builder.append("}\n");
        return builder.toString();
    }
}

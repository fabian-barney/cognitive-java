package media.barney.cognitive.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class CliArgumentsParserTest {

    @Test
    void noArgsMeansAllSourceFiles() {
        CliArguments args = CliArgumentsParser.parse(new String[0]);

        assertEquals(CliMode.ALL_SRC, args.mode());
        assertEquals(ReportFormat.TOON, args.reportFormat());
        assertEquals(15, args.threshold());
        assertEquals(List.of(), args.fileArgs());
    }

    @Test
    void changedFlagMeansChangedSourceFiles() {
        CliArguments args = CliArgumentsParser.parse(new String[]{"--changed"});

        assertEquals(CliMode.CHANGED_SRC, args.mode());
        assertEquals(List.of(), args.fileArgs());
    }

    @Test
    void helpPrintsUsageMode() {
        CliArguments args = CliArgumentsParser.parse(new String[]{"--help"});

        assertEquals(CliMode.HELP, args.mode());
    }

    @Test
    void fileNamesMeanExplicitFiles() {
        CliArguments args = CliArgumentsParser.parse(new String[]{
                "src/main/java/demo/A.java",
                "src/main/java/demo/B.java"
        });

        assertEquals(CliMode.EXPLICIT_FILES, args.mode());
        assertEquals(List.of("src/main/java/demo/A.java", "src/main/java/demo/B.java"), args.fileArgs());
    }

    @Test
    void changedCannotBeCombinedWithFiles() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--changed", "src/main/java/demo/A.java"}));
    }

    @Test
    void unknownOptionsFailParsing() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--build-tool", "gradle"}));

        assertEquals("Unknown option: --build-tool", error.getMessage());
    }

    @Test
    void parsesInlineAndSeparatedReportOptions() {
        CliArguments args = CliArgumentsParser.parse(new String[]{
                "--format=json",
                "--output", "reports/primary.json",
                "--junit-report=reports/junit.xml",
                "--threshold", "9",
                "src/main/java/demo/A.java"
        });

        assertEquals(ReportFormat.JSON, args.reportFormat());
        assertEquals("reports/primary.json", args.outputPath());
        assertEquals("reports/junit.xml", args.junitReportPath());
        assertEquals(9, args.threshold());
        assertEquals(List.of("src/main/java/demo/A.java"), args.fileArgs());
    }

    @Test
    void parsesAllSupportedReportFormats() {
        assertEquals(ReportFormat.TOON, ReportFormat.parse("toon"));
        assertEquals(ReportFormat.JSON, ReportFormat.parse("JSON"));
        assertEquals(ReportFormat.TEXT, ReportFormat.parse("text"));
        assertEquals(ReportFormat.JUNIT, ReportFormat.parse("junit"));
        assertEquals(ReportFormat.NONE, ReportFormat.parse("none"));
    }

    @Test
    void unknownReportFormatFails() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ReportFormat.parse("yaml"));

        assertEquals("Unknown report format: yaml", error.getMessage());
    }

    @Test
    void agentEnablesCompactFailureDefaults() {
        CliArguments args = CliArgumentsParser.parse(new String[]{"--agent"});

        assertEquals(ReportFormat.TOON, args.reportFormat());
        assertEquals(true, args.agent());
        assertEquals(true, args.failuresOnly());
        assertEquals(true, args.omitRedundancy());
    }

    @Test
    void explicitBooleanValuesOverrideAgentDefaults() {
        CliArguments args = CliArgumentsParser.parse(new String[]{
                "--agent",
                "--format=text",
                "--failures-only=false",
                "--omit-redundancy=false"
        });

        assertEquals(ReportFormat.TEXT, args.reportFormat());
        assertEquals(false, args.failuresOnly());
        assertEquals(false, args.omitRedundancy());
    }

    @Test
    void booleanValuesMustBeStrictLowercase() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--failures-only=True"}));

        assertEquals("--failures-only requires true or false when assigned", error.getMessage());
    }

    @Test
    void duplicateScalarOptionsFail() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--format", "json", "--format=text"}));

        assertEquals("--format can only be provided once", error.getMessage());
    }

    @Test
    void thresholdMustBePositiveInteger() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--threshold", "1.5"}));

        assertEquals("--threshold requires a positive integer", error.getMessage());
    }

    @Test
    void missingSeparatedOptionValueCannotConsumeAnotherOption() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--output", "--format", "json"}));

        assertEquals("--output requires a path", error.getMessage());
    }
}

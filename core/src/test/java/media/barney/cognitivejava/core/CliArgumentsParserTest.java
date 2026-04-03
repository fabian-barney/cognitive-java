package media.barney.cognitivejava.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class CliArgumentsParserTest {

    @Test
    void noArgsMeansAllSourceFiles() {
        CliArguments args = CliArgumentsParser.parse(new String[0]);

        assertEquals(CliMode.ALL_SRC, args.mode());
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
}

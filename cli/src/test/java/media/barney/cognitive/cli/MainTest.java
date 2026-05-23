package media.barney.cognitive.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

    @TempDir
    Path tempDir;

    @Test
    void helpDelegatesToCoreMain() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = Main.run(
                new String[]{"--help"},
                tempDir,
                new PrintStream(out, false, StandardCharsets.UTF_8),
                new PrintStream(err, false, StandardCharsets.UTF_8));

        assertEquals(0, exit);
        assertTrue(utf8(out).contains("Usage:"));
        assertTrue(utf8(out).contains("cognitive-java --changed"));
        assertEquals("", utf8(err));
    }

    private static String utf8(ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
    }
}

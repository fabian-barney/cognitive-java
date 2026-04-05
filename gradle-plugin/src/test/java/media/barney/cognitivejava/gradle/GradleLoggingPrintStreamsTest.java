package media.barney.cognitivejava.gradle;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class GradleLoggingPrintStreamsTest {

    @Test
    void standardOutFlushesLinesWithoutThrowing() {
        Project project = ProjectBuilder.builder().build();

        assertDoesNotThrow(() -> {
            try (PrintStream stream = GradleLoggingPrintStreams.standardOut(project.getLogger())) {
                stream.print("alpha");
                stream.print('\r');
                stream.print('\n');
                stream.print("beta");
                stream.flush();
            }
        });
    }

    @Test
    void standardErrFlushesLinesWithoutThrowing() {
        Project project = ProjectBuilder.builder().build();

        assertDoesNotThrow(() -> {
            try (PrintStream stream = GradleLoggingPrintStreams.standardErr(project.getLogger())) {
                stream.println("warning");
            }
        });
    }
}

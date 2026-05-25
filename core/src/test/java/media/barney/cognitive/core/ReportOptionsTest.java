package media.barney.cognitive.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jspecify.annotations.Nullable;

class ReportOptionsTest {

    @Test
    void sameNamedPathsRejectsMissingFileName(@TempDir Path tempDir) throws Exception {
        assertFalse(invokeSameNamedPaths(null, Path.of("report.xml"), tempDir));
    }

    @Test
    void sameNamedPathsMatchesIdenticalNames(@TempDir Path tempDir) throws Exception {
        assertTrue(invokeSameNamedPaths(Path.of("report.xml"), Path.of("report.xml"), tempDir));
    }

    private static boolean invokeSameNamedPaths(@Nullable Path firstFileName, Path secondFileName, Path parent)
            throws Exception {
        Method method = ReportOptions.class.getDeclaredMethod("sameNamedPaths", Path.class, Path.class, Path.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, firstFileName, secondFileName, parent);
    }
}

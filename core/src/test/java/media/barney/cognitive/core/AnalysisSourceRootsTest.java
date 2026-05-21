package media.barney.cognitive.core;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisSourceRootsTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsConfiguredSourceRootsThatAreSymbolicLinks() throws Exception {
        Path sourceRoot = tempDir.resolve("src/custom/java");
        Files.createDirectories(sourceRoot);
        Path link = tempDir.resolve("linked-source-root");
        try {
            Files.createSymbolicLink(link, sourceRoot);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable on this platform");
        }

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> AnalysisSourceRoots.resolveConfiguredSourceRoots(tempDir, List.of("linked-source-root")));

        assertTrue(Objects.requireNonNull(error.getMessage()).contains("must not point to or traverse a symlink"));
    }

    @Test
    void rejectsConfiguredSourceRootsThatTraverseSymlinkSegments() throws Exception {
        Path realParent = tempDir.resolve("real-parent/src/custom/java");
        Files.createDirectories(realParent);
        Path linkParent = tempDir.resolve("linked-parent");
        try {
            Files.createSymbolicLink(linkParent, tempDir.resolve("real-parent"));
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable on this platform");
        }

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> AnalysisSourceRoots.resolveConfiguredSourceRoots(tempDir, List.of("linked-parent/src/custom/java")));

        assertTrue(Objects.requireNonNull(error.getMessage()).contains("must not point to or traverse a symlink"));
    }

    @Test
    void rejectsBlankExplicitPathsWithExplicitPathMessage() throws Exception {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> AnalysisSourceRoots.resolveExplicitPath(tempDir, "   "));

        assertEquals("Path must not be blank", error.getMessage());
    }
}

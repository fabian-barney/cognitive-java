package media.barney.cognitive.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionSourceRootsTest {

    @Test
    void detectsPathsUnderProductionSourceRoots() {
        assertTrue(ProductionSourceRoots.isUnderProductionSourceRoot(
                Path.of("module-a", "src", "main", "java", "demo", "Sample.java")));
        assertTrue(ProductionSourceRoots.isUnderProductionSourceRoot(
                Path.of("src", "main", "java", "demo", "Nested", "Sample.java")));
    }

    @Test
    void rejectsPathsOutsideProductionSourceRoots() {
        assertFalse(ProductionSourceRoots.isUnderProductionSourceRoot(
                Path.of("module-a", "src", "test", "java", "demo", "Sample.java")));
        assertFalse(ProductionSourceRoots.isUnderProductionSourceRoot(
                Path.of("demo", "Sample.java")));
    }
}

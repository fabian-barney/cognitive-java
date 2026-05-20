package media.barney.cognitive.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceFileFinderTest {

    @TempDir
    Path tempDir;

    @Test
    void findsAllJavaFilesUnderProductionSourceRootsOnly() throws Exception {
        Path rootSrc = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(rootSrc);
        Path inRootSrc = rootSrc.resolve("Sample.java");
        Files.writeString(inRootSrc, "class Sample {}\n");

        Path nestedModuleSrc = tempDir.resolve("module-a/src/main/java/demo");
        Files.createDirectories(nestedModuleSrc);
        Path inNestedModuleSrc = nestedModuleSrc.resolve("NestedSample.java");
        Files.writeString(inNestedModuleSrc, "class NestedSample {}\n");

        Path skippedBuildSrc = tempDir.resolve("build/src/main/java/demo");
        Files.createDirectories(skippedBuildSrc);
        Path skippedBuildFile = skippedBuildSrc.resolve("Generated.java");
        Files.writeString(skippedBuildFile, "class Generated {}\n");

        Path generatedSrc = tempDir.resolve("build/generated/src/demo");
        Files.createDirectories(generatedSrc);
        Path generated = generatedSrc.resolve("Generated.java");
        Files.writeString(generated, "class Generated {}\n");

        Path outOfSrc = tempDir.resolve("other/Elsewhere.java");
        Files.createDirectories(outOfSrc.getParent());
        Files.writeString(outOfSrc, "class Elsewhere {}\n");

        List<Path> files = SourceFileFinder.findAllJavaFilesUnderSourceRoots(tempDir);
        List<Path> expected = new ArrayList<>(List.of(inRootSrc, inNestedModuleSrc));
        expected.sort(Path::compareTo);

        assertEquals(expected, files);
    }

    @Test
    void acceptsADirectoryThatIsAlreadyAProductionSourceRoot() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Path source = sourceRoot.resolve("Sample.java");
        Files.writeString(source, "class Sample {}\n");

        List<Path> files = SourceFileFinder.findAllJavaFilesUnderSourceRoots(tempDir.resolve("src/main/java"));

        assertEquals(List.of(source), files);
    }

    @Test
    void findsAllJavaFilesUnderConfiguredSourceRoots() throws Exception {
        Path customRoot = tempDir.resolve("src/custom/java/demo");
        Files.createDirectories(customRoot);
        Path source = customRoot.resolve("Custom.java");
        Files.writeString(source, "class Custom {}\n");

        Path otherRoot = tempDir.resolve("module-a/generated/java/demo");
        Files.createDirectories(otherRoot);
        Path otherSource = otherRoot.resolve("Generated.java");
        Files.writeString(otherSource, "class Generated {}\n");

        List<Path> files = SourceFileFinder.findAllJavaFiles(AnalysisSourceRoots.resolveConfiguredSourceRoots(
                tempDir,
                List.of("src/custom/java", "module-a/generated/java")));

        assertEquals(List.of(otherSource, source), files);
    }

    @Test
    void findsJavaFilesWhenConfiguredDirectoryContainsAConfiguredSourceRoot() throws Exception {
        Path sourceRoot = tempDir.resolve("module-a/src/custom/java/demo");
        Files.createDirectories(sourceRoot);
        Path source = sourceRoot.resolve("Custom.java");
        Files.writeString(source, "class Custom {}\n");

        List<Path> files = SourceFileFinder.findJavaFilesUnderConfiguredDirectory(
                tempDir.resolve("module-a"),
                List.of(tempDir.resolve("module-a/src/custom/java").toAbsolutePath().normalize())
        );

        assertEquals(List.of(source.toAbsolutePath().normalize()), files);
    }

    @Test
    void findsJavaFilesWhenConfiguredDirectoryIsInsideAConfiguredSourceRoot() throws Exception {
        Path sourceRoot = tempDir.resolve("module-a/src/custom/java/demo");
        Files.createDirectories(sourceRoot);
        Path source = sourceRoot.resolve("Custom.java");
        Files.writeString(source, "class Custom {}\n");

        List<Path> files = SourceFileFinder.findJavaFilesUnderConfiguredDirectory(
                tempDir.resolve("module-a/src/custom/java/demo"),
                List.of(tempDir.resolve("module-a/src/custom/java").toAbsolutePath().normalize())
        );

        assertEquals(List.of(source.toAbsolutePath().normalize()), files);
    }

    @Test
    void doesNotFollowSymlinkedDirectoriesDuringSourceDiscovery() throws Exception {
        Path realRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(realRoot);
        Files.writeString(realRoot.resolve("Sample.java"), "class Sample {}\n");

        Path linkedParent = tempDir.resolve("linked");
        Files.createDirectories(linkedParent);
        Path symlink = linkedParent.resolve("src-link");
        try {
            Files.createSymbolicLink(symlink, tempDir.resolve("src"));
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable on this platform");
        }

        List<Path> files = SourceFileFinder.findAllJavaFilesUnderSourceRoots(tempDir);

        assertEquals(List.of(realRoot.resolve("Sample.java")), files);
    }
}

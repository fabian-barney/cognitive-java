package media.barney.cognitive.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceExclusionMatcherTest {

    @TempDir
    Path tempDir;

    @Test
    void sourceExclusionOptionsTreatNullListsAsEmpty() throws ReflectiveOperationException {
        SourceExclusionOptions options = SourceExclusionOptions.class
                .getDeclaredConstructor(List.class, List.class, List.class, boolean.class)
                .newInstance(null, null, null, true);

        assertEquals(List.of(), options.excludes());
        assertEquals(List.of(), options.excludeClasses());
        assertEquals(List.of(), options.excludeAnnotations());
        assertTrue(options.useDefaultExclusions());
    }

    @Test
    void defaultPathExclusionsMatchGeneratedDirectorySegmentsAndJavaGen() {
        Path generated = tempDir.resolve("src/main/java/demo/generated/Sample.java");
        Path generatedSources = tempDir.resolve("module/src/main/generated-sources/demo/Sample.java");
        Path javaGen = tempDir.resolve("module/src/main/java-gen/demo/Sample.java");
        Path handwritten = tempDir.resolve("src/main/java/demo/GeneratedName.java");
        SourceExclusionAudit.Builder audit = SourceExclusionAudit.builder();

        List<Path> included = SourceExclusionMatcher.filterFiles(
                tempDir,
                List.of(generated, generatedSources, javaGen, handwritten),
                SourceExclusionOptions.defaults(),
                audit
        );

        SourceExclusionAudit result = audit.build();
        assertEquals(List.of(handwritten), included);
        assertEquals(4, result.discoveredFiles());
        assertEquals(1, result.analyzedFiles());
        assertEquals(3, result.excludedFileCount());
    }

    @Test
    void disabledDefaultsKeepGeneratedPathsUnlessUserGlobMatches() {
        Path generated = tempDir.resolve("src/main/java/demo/generated/Sample.java");
        Path javaGen = tempDir.resolve("src/main/java-gen/demo/Sample.java");
        SourceExclusionAudit.Builder audit = SourceExclusionAudit.builder();

        List<Path> included = SourceExclusionMatcher.filterFiles(
                tempDir,
                List.of(generated, javaGen),
                new SourceExclusionOptions(List.of(), List.of(), List.of(), false),
                audit
        );

        assertEquals(List.of(generated, javaGen), included);
        assertEquals(0, audit.build().excludedFileCount());
    }

    @Test
    void userGlobExclusionsUseNormalizedRelativePaths() {
        Path moduleSource = tempDir.resolve("module-a/src/main/java/demo/Sample.java");
        Path otherSource = tempDir.resolve("module-b/src/main/java/demo/Sample.java");
        SourceExclusionAudit.Builder audit = SourceExclusionAudit.builder();

        List<Path> included = SourceExclusionMatcher.filterFiles(
                tempDir,
                List.of(moduleSource, otherSource),
                new SourceExclusionOptions(List.of("module-a/**"), List.of(), List.of(), false),
                audit
        );

        assertEquals(List.of(otherSource), included);
        assertEquals(1, audit.build().excludedFileCount());
    }

    @Test
    void userGlobExclusionsSupportDoubleStarSingleStarAndQuestionMark() {
        Path numbered = tempDir.resolve("module/src/main/java/demo/Sample1.java");
        Path named = tempDir.resolve("module/src/main/java/demo/SampleName.java");
        Path nested = tempDir.resolve("module/src/main/java/demo/nested/Sample2.java");
        SourceExclusionAudit.Builder audit = SourceExclusionAudit.builder();

        List<Path> included = SourceExclusionMatcher.filterFiles(
                tempDir,
                List.of(numbered, named, nested),
                new SourceExclusionOptions(
                        List.of("**/demo/Sample?.java", "**/demo/*.java"),
                        List.of(),
                        List.of(),
                        false
                ),
                audit
        );

        assertEquals(List.of(nested), included);
        assertEquals(2, audit.build().excludedFileCount());
    }

    @Test
    void defaultClassRegexesAreGeneratedFocused() {
        SourceExclusionMatcher matcher = SourceExclusionMatcher.create(tempDir, SourceExclusionOptions.defaults());

        assertTrue(matcher.classExclusionReason("demo.generated.Sample", List.of()).isPresent());
        assertTrue(matcher.classExclusionReason("demo.SampleMapperImpl", List.of()).isPresent());
        assertTrue(matcher.classExclusionReason("demo.DaggerComponent", List.of()).isPresent());
        assertTrue(matcher.classExclusionReason("demo.Hilt_Service", List.of()).isPresent());
        assertTrue(matcher.classExclusionReason("demo.AutoValue_User", List.of()).isPresent());
        assertFalse(matcher.classExclusionReason("demo.QueryParser", List.of()).isPresent());
        assertFalse(matcher.classExclusionReason("demo.BaseListener", List.of()).isPresent());
        assertFalse(matcher.classExclusionReason("demo.ImmutableUser", List.of()).isPresent());
    }

    @Test
    void generatedAnnotationDefaultMatchesSimpleNameFromAnyPackage() {
        SourceExclusionMatcher matcher = SourceExclusionMatcher.create(tempDir, SourceExclusionOptions.defaults());

        assertTrue(matcher.classExclusionReason("demo.Sample", List.of("Generated")).isPresent());
        assertTrue(matcher.classExclusionReason("demo.Sample", List.of("javax.annotation.Generated")).isPresent());
        assertTrue(matcher.classExclusionReason("demo.Sample", List.of("jakarta.annotation.Generated")).isPresent());
        assertTrue(matcher.classExclusionReason("demo.Sample", List.of("com.acme.Generated")).isPresent());
        assertFalse(matcher.classExclusionReason("demo.Sample", List.of("com.acme.GeneratedValue")).isPresent());
    }

    @Test
    void userQualifiedAnnotationMatchesCapturedSimpleName() {
        SourceExclusionMatcher matcher = SourceExclusionMatcher.create(
                tempDir,
                new SourceExclusionOptions(List.of(), List.of(), List.of("com.acme.Generated"), false)
        );

        assertTrue(matcher.classExclusionReason("demo.Sample", List.of("Generated")).isPresent());
    }
}

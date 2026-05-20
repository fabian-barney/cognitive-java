package media.barney.cognitive.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class AnalysisSourceRoots {

    private AnalysisSourceRoots() {
    }

    static List<Path> discoverDefaultSourceRoots(Path analysisRoot) throws IOException {
        if (!Files.isDirectory(analysisRoot)) {
            return List.of();
        }

        List<Path> sourceRoots = new ArrayList<>();
        Files.walkFileTree(analysisRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(analysisRoot) && ProductionSourceRoots.isSkippableDirectory(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (ProductionSourceRoots.isProductionSourceRoot(dir)) {
                    sourceRoots.add(dir.toAbsolutePath().normalize());
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        sourceRoots.sort(Comparator.naturalOrder());
        return sourceRoots;
    }

    static List<Path> resolveConfiguredSourceRoots(Path analysisRoot, List<String> configuredSourceRoots) throws IOException {
        Path normalizedAnalysisRoot = analysisRoot.toAbsolutePath().normalize();
        Set<Path> resolvedRoots = new LinkedHashSet<>();
        for (String configuredSourceRoot : configuredSourceRoots) {
            String trimmed = configuredSourceRoot.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("--source-root requires a path");
            }
            Path candidate = Path.of(trimmed);
            Path resolved = candidate.isAbsolute()
                    ? candidate.toAbsolutePath().normalize()
                    : normalizedAnalysisRoot.resolve(candidate).normalize();
            if (!resolved.startsWith(normalizedAnalysisRoot)) {
                throw new IllegalArgumentException("--source-root must stay inside the analysis root: "
                        + configuredSourceRoot);
            }
            if (!Files.isDirectory(resolved)) {
                throw new IllegalArgumentException("--source-root must be an existing directory: "
                        + configuredSourceRoot);
            }
            resolvedRoots.add(resolved);
        }
        return resolvedRoots.stream().sorted().toList();
    }

    static boolean isUnderAnySourceRoot(Path path, List<Path> sourceRoots) {
        Path normalized = path.toAbsolutePath().normalize();
        for (Path sourceRoot : sourceRoots) {
            if (normalized.startsWith(sourceRoot)) {
                return true;
            }
        }
        return false;
    }
}

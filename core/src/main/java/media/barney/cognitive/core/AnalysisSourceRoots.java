package media.barney.cognitive.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
        Path realAnalysisRoot = normalizedAnalysisRoot.toRealPath();
        Set<Path> resolvedRoots = new LinkedHashSet<>();
        for (String configuredSourceRoot : configuredSourceRoots) {
            Path resolved = resolvePath(normalizedAnalysisRoot, configuredSourceRoot, "--source-root requires a path");
            validateConfiguredSourceRoot(normalizedAnalysisRoot, realAnalysisRoot, configuredSourceRoot, resolved);
            resolvedRoots.add(resolved);
        }
        return resolvedRoots.stream().sorted().toList();
    }

    static Path resolveExplicitPath(Path analysisRoot, String configuredPath) throws IOException {
        Path normalizedAnalysisRoot = analysisRoot.toAbsolutePath().normalize();
        Path realAnalysisRoot = normalizedAnalysisRoot.toRealPath();
        Path resolved = resolvePath(normalizedAnalysisRoot, configuredPath, "Path must not be blank");
        ensureInsideAnalysisRoot(normalizedAnalysisRoot, "Path", configuredPath, resolved);
        ensureExistingPath(configuredPath, resolved);
        ensureNoTraversedSymlink(normalizedAnalysisRoot, "Path", configuredPath, resolved);
        ensureRealPathInsideAnalysisRoot(realAnalysisRoot, "Path", configuredPath, resolved);
        return resolved;
    }

    private static Path resolvePath(Path normalizedAnalysisRoot, String configuredPath, String blankMessage) {
        String trimmed = configuredPath.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(blankMessage);
        }
        Path candidate = Path.of(trimmed);
        return candidate.isAbsolute()
                ? candidate.toAbsolutePath().normalize()
                : normalizedAnalysisRoot.resolve(candidate).normalize();
    }

    private static void validateConfiguredSourceRoot(
            Path normalizedAnalysisRoot,
            Path realAnalysisRoot,
            String configuredSourceRoot,
            Path resolved
    ) throws IOException {
        ensureInsideAnalysisRoot(normalizedAnalysisRoot, "--source-root", configuredSourceRoot, resolved);
        ensureNoTraversedSymlink(normalizedAnalysisRoot, "--source-root", configuredSourceRoot, resolved);
        ensureExistingDirectory(configuredSourceRoot, resolved);
        ensureRealPathInsideAnalysisRoot(realAnalysisRoot, "--source-root", configuredSourceRoot, resolved);
    }

    private static void ensureInsideAnalysisRoot(
            Path normalizedAnalysisRoot,
            String pathLabel,
            String configuredPath,
            Path resolved
    ) {
        if (!resolved.startsWith(normalizedAnalysisRoot)) {
            throw new IllegalArgumentException(pathLabel + " must stay inside the analysis root: "
                    + configuredPath);
        }
    }

    private static void ensureNoTraversedSymlink(
            Path normalizedAnalysisRoot,
            String pathLabel,
            String configuredPath,
            Path resolved
    )
            throws IOException {
        if (containsSymbolicLink(normalizedAnalysisRoot, resolved)) {
            throw new IllegalArgumentException(pathLabel + " must not point to or traverse a symlink: "
                    + configuredPath);
        }
    }

    private static void ensureExistingPath(String configuredPath, Path resolved) {
        if (!Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Path does not exist: " + configuredPath);
        }
    }

    private static void ensureExistingDirectory(String configuredSourceRoot, Path resolved) {
        if (!Files.isDirectory(resolved, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("--source-root must be an existing directory: "
                    + configuredSourceRoot);
        }
    }

    private static void ensureRealPathInsideAnalysisRoot(
            Path realAnalysisRoot,
            String pathLabel,
            String configuredPath,
            Path resolved
    )
            throws IOException {
        if (!resolved.toRealPath().startsWith(realAnalysisRoot)) {
            throw new IllegalArgumentException(pathLabel + " must stay inside the analysis root: "
                    + configuredPath);
        }
    }

    static boolean containsSymbolicLink(Path root, Path path) throws IOException {
        Path current = root;
        for (Path segment : root.relativize(path)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                return true;
            }
        }
        return false;
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

package media.barney.cognitive.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

record ReportOptions(
        ReportFormat format,
        boolean failuresOnly,
        boolean omitRedundancy,
        @Nullable Path outputPath,
        @Nullable Path junitReportPath
) {
    static ReportOptions create(Path analysisRoot,
                                ReportFormat format,
                                boolean failuresOnly,
                                boolean omitRedundancy,
                                @Nullable String outputPath,
                                @Nullable String junitReportPath) {
        Path root = analysisRoot.toAbsolutePath().normalize();
        Path output = resolveReportPath(root, "output", outputPath);
        Path junit = resolveReportPath(root, "junitReport", junitReportPath);
        if (output != null && junit != null && sameReportTarget(output, junit)) {
            throw new IllegalArgumentException("output and junitReport must not point to the same file");
        }
        return new ReportOptions(format, failuresOnly, omitRedundancy, output, junit);
    }

    private static @Nullable Path resolveReportPath(Path root, String name, @Nullable String configuredPath) {
        if (configuredPath == null) {
            return null;
        }
        Path path = root.resolve(configuredPath).toAbsolutePath().normalize();
        validateReportPath(root, name, path);
        return path;
    }

    private static void validateReportPath(Path root, String name, Path path) {
        if (path.getFileName() == null) {
            throw new IllegalArgumentException(name + " must not point to a filesystem root");
        }
        if (path.equals(root)) {
            throw new IllegalArgumentException(name + " must not point to the project root");
        }
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException(name + " must stay inside the project root");
        }
        if (Files.isDirectory(path)) {
            throw new IllegalArgumentException(name + " must not point to a directory");
        }
        if (resolvesOutsideRoot(root, path)) {
            throw new IllegalArgumentException(name + " must stay inside the project root");
        }
    }

    private static boolean resolvesOutsideRoot(Path root, Path path) {
        Path rootReal = realPathForComparison(root);
        Path pathReal = realPathForComparison(path);
        return rootReal != null && pathReal != null && !pathReal.startsWith(rootReal);
    }

    private static boolean sameReportTarget(Path first, Path second) {
        if (first.equals(second)) {
            return true;
        }
        try {
            return sameExistingFile(first, second)
                    || sameRealPath(first, second)
                    || sameParentAndFileName(first, second);
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean sameExistingFile(Path first, Path second) throws IOException {
        return Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second);
    }

    private static boolean sameParentAndFileName(Path first, Path second) throws IOException {
        Path firstParent = first.getParent();
        Path secondParent = second.getParent();
        return sameParent(firstParent, secondParent) && sameFileName(first, second, firstParent);
    }

    private static boolean sameParent(@Nullable Path firstParent, @Nullable Path secondParent) throws IOException {
        return (firstParent == null || secondParent == null)
                ? Objects.equals(firstParent, secondParent)
                : sameNonNullParent(firstParent, secondParent);
    }

    private static boolean sameNonNullParent(Path firstParent, Path secondParent) throws IOException {
        return firstParent.equals(secondParent)
                || sameAliasedParent(firstParent, secondParent);
    }

    private static boolean sameAliasedParent(Path firstParent, Path secondParent) throws IOException {
        return sameExistingFile(firstParent, secondParent)
                || sameRealPath(firstParent, secondParent)
                || sameCaseInsensitivePath(firstParent, secondParent);
    }

    private static boolean sameRealPath(Path first, Path second) {
        Path firstRealPath = realPathForComparison(first);
        Path secondRealPath = realPathForComparison(second);
        return firstRealPath != null && firstRealPath.equals(secondRealPath);
    }

    private static @Nullable Path realPathForComparison(Path path) {
        return realPathForComparison(path, 0);
    }

    private static @Nullable Path realPathForComparison(Path path, int symlinkDepth) {
        if (symlinkDepth > 8) {
            return null;
        }
        Path normalized = path.toAbsolutePath().normalize();
        try {
            if (Files.isSymbolicLink(normalized)) {
                return symbolicLinkTargetForComparison(normalized, symlinkDepth);
            }
            if (Files.exists(normalized)) {
                return normalized.toRealPath();
            }
            Path existing = nearestExistingPath(normalized);
            if (existing != null) {
                return existing.toRealPath().resolve(existing.relativize(normalized)).normalize();
            }
        } catch (IOException | SecurityException exception) {
            return null;
        }
        return null;
    }

    private static @Nullable Path symbolicLinkTargetForComparison(Path link, int symlinkDepth) throws IOException {
        Path target = Files.readSymbolicLink(link);
        Path resolved = link.resolveSibling(target);
        return realPathForComparison(resolved, symlinkDepth + 1);
    }

    private static @Nullable Path nearestExistingPath(Path path) {
        return ancestors(path).filter(Files::exists).findFirst().orElse(null);
    }

    private static boolean sameFileName(Path first, Path second, @Nullable Path parent) {
        String firstName = first.getFileName().toString();
        String secondName = second.getFileName().toString();
        return firstName.equals(secondName)
                || (firstName.equalsIgnoreCase(secondName) && isCaseInsensitive(parent));
    }

    private static boolean isCaseInsensitive(@Nullable Path path) {
        Path directory = nearestExistingDirectory(path);
        return directory == null ? isLikelyCaseInsensitiveOs() : directoryIsCaseInsensitive(directory);
    }

    private static boolean directoryIsCaseInsensitive(Path directory) {
        try {
            Path probe = Files.createTempFile(directory, ".cognitive-java-case-", ".tmp");
            try {
                return caseVariantExists(probe);
            } finally {
                Files.deleteIfExists(probe);
            }
        } catch (IOException | SecurityException exception) {
            return isLikelyCaseInsensitiveOs();
        }
    }

    private static @Nullable Path nearestExistingDirectory(@Nullable Path path) {
        Path start = path == null ? Path.of(".").toAbsolutePath().normalize() : path.toAbsolutePath().normalize();
        return ancestors(start).filter(Files::isDirectory).findFirst().orElse(null);
    }

    private static Stream<Path> ancestors(Path path) {
        return Stream.iterate(path, Objects::nonNull, Path::getParent);
    }

    private static boolean caseVariantExists(Path probe) {
        Path variant = probe.resolveSibling(probe.getFileName().toString().toUpperCase(Locale.ROOT));
        return !probe.getFileName().toString().equals(variant.getFileName().toString()) && Files.exists(variant);
    }

    static boolean isLikelyCaseInsensitiveOs() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.startsWith("windows");
    }

    private static boolean sameCaseInsensitivePath(Path first, Path second) {
        return first.toString().equalsIgnoreCase(second.toString()) && isCaseInsensitive(first);
    }
}

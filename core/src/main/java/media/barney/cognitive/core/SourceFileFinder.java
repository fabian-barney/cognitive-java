package media.barney.cognitive.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

final class SourceFileFinder {

    private SourceFileFinder() {
    }

    static List<Path> findAllJavaFilesUnderSourceRoots(Path projectRoot) throws IOException {
        if (!Files.isDirectory(projectRoot)) {
            return List.of();
        }
        return findAllJavaFiles(AnalysisSourceRoots.discoverDefaultSourceRoots(projectRoot));
    }

    static List<Path> findAllJavaFiles(List<Path> sourceRoots) throws IOException {
        Set<Path> javaFiles = new LinkedHashSet<>();
        for (Path sourceRoot : sourceRoots) {
            javaFiles.addAll(findJavaFilesRecursively(sourceRoot));
        }
        return javaFiles.stream().sorted(Comparator.naturalOrder()).toList();
    }

    static List<Path> findJavaFilesUnderConfiguredDirectory(Path directory, List<Path> configuredSourceRoots) throws IOException {
        Set<Path> javaFiles = new LinkedHashSet<>();
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        for (Path sourceRoot : configuredSourceRoots) {
            if (sourceRoot.startsWith(normalizedDirectory)) {
                javaFiles.addAll(findJavaFilesRecursively(sourceRoot));
            } else if (normalizedDirectory.startsWith(sourceRoot)) {
                javaFiles.addAll(findJavaFilesRecursively(normalizedDirectory));
            }
        }
        return javaFiles.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private static List<Path> findJavaFilesRecursively(Path sourceRoot) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            return List.of();
        }
        List<Path> javaFiles = new ArrayList<>();
        try (var stream = Files.walk(sourceRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.toAbsolutePath().normalize())
                    .forEach(javaFiles::add);
        }
        return javaFiles;
    }
}

package media.barney.cognitivejava.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ChangedFileDetector {

    private ChangedFileDetector() {
    }

    static List<Path> changedJavaFiles(Path projectRoot) throws IOException, InterruptedException {
        return changedJavaFiles(projectRoot, ChangedFileDetector::startGitStatus);
    }

    static List<Path> changedJavaFiles(Path projectRoot, GitStatusProcessStarter processStarter)
            throws IOException, InterruptedException {
        Process process = processStarter.start(projectRoot);
        byte[] output = readAllOutput(process.getInputStream());
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("git status failed: " + new String(output, StandardCharsets.UTF_8));
        }

        List<Path> files = parseStatusOutput(projectRoot, output);
        files.sort(Path::compareTo);
        return files;
    }

    static List<Path> changedJavaFilesUnderSourceRoots(Path projectRoot) throws IOException, InterruptedException {
        return changedJavaFiles(projectRoot).stream()
                .filter(ProductionSourceRoots::isUnderProductionSourceRoot)
                .toList();
    }

    private static Process startGitStatus(Path projectRoot) throws IOException {
        return new ProcessBuilder(
                "git",
                "-C",
                projectRoot.toString(),
                "status",
                "--porcelain=v1",
                "-z",
                "--untracked-files=all")
                .redirectErrorStream(true)
                .start();
    }

    private static byte[] readAllOutput(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = inputStream.read(chunk)) >= 0) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private static List<Path> parseStatusOutput(Path root, byte[] output) {
        List<Path> files = new ArrayList<>();
        int index = 0;
        while (index < output.length) {
            if (index + 3 >= output.length) {
                throw new IllegalStateException("Unexpected git status output");
            }
            char indexStatus = (char) output[index];
            char workTreeStatus = (char) output[index + 1];
            if (output[index + 2] != ' ') {
                throw new IllegalStateException("Unexpected git status output");
            }
            int pathStart = index + 3;
            int pathEnd = nextNullIndex(output, pathStart);
            String path = new String(output, pathStart, pathEnd - pathStart, StandardCharsets.UTF_8);
            index = pathEnd + 1;

            if (isRenameOrCopy(indexStatus, workTreeStatus)) {
                index = nextNullIndex(output, index) + 1;
            }

            if (isRelevantStatus(indexStatus, workTreeStatus) && isJavaPath(path)) {
                files.add(root.resolve(path).normalize());
            }
        }
        return files;
    }

    private static int nextNullIndex(byte[] output, int start) {
        for (int index = start; index < output.length; index++) {
            if (output[index] == 0) {
                return index;
            }
        }
        throw new IllegalStateException("Unexpected git status output");
    }

    private static boolean isRenameOrCopy(char indexStatus, char workTreeStatus) {
        return indexStatus == 'R'
                || indexStatus == 'C'
                || workTreeStatus == 'R'
                || workTreeStatus == 'C';
    }

    private static boolean isRelevantStatus(char indexStatus, char workTreeStatus) {
        return (indexStatus == '?' && workTreeStatus == '?')
                || isRelevantCode(indexStatus)
                || isRelevantCode(workTreeStatus);
    }

    private static boolean isRelevantCode(char status) {
        return status == 'A'
                || status == 'M'
                || status == 'R'
                || status == 'C';
    }

    private static boolean isJavaPath(String path) {
        return path.endsWith(".java");
    }

    @FunctionalInterface
    interface GitStatusProcessStarter {
        Process start(Path projectRoot) throws IOException;
    }
}

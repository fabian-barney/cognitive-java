package media.barney.cognitive.core;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class ChangedFileDetector {

    private static final Duration GIT_STATUS_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_CAPTURED_OUTPUT_BYTES = 64 * 1024;

    private ChangedFileDetector() {
    }

    static List<Path> changedJavaFiles(Path projectRoot) throws IOException, InterruptedException {
        return changedJavaFiles(projectRoot, ChangedFileDetector::startGitStatus);
    }

    static List<Path> changedJavaFiles(Path projectRoot, GitStatusProcessStarter processStarter)
            throws IOException, InterruptedException {
        Process process = processStarter.start(projectRoot);
        CapturedOutput stdout = capture(process.getInputStream(), "cognitive-java-git-stdout");
        CapturedOutput stderr = capture(process.getErrorStream(), "cognitive-java-git-stderr");
        int exit = waitFor(process, stdout, stderr);
        if (exit != 0) {
            throw new IOException("git status failed with exit " + exit + formatCapturedOutput(stdout, stderr));
        }
        ensureCompleteStatusOutput(stdout, stderr);

        List<Path> files = parseStatusOutput(projectRoot, stdout.bytes());
        files.sort(Path::compareTo);
        return files;
    }

    static List<Path> changedJavaFilesUnderSourceRoots(Path projectRoot) throws IOException, InterruptedException {
        List<Path> sourceRoots = AnalysisSourceRoots.discoverDefaultSourceRoots(projectRoot);
        return changedJavaFilesUnderSourceRoots(projectRoot, sourceRoots);
    }

    static List<Path> changedJavaFilesUnderSourceRoots(Path projectRoot, List<Path> sourceRoots)
            throws IOException, InterruptedException {
        return changedJavaFiles(projectRoot).stream()
                .filter(path -> AnalysisSourceRoots.isUnderAnySourceRoot(path, sourceRoots))
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
                .start();
    }

    private static CapturedOutput capture(java.io.InputStream inputStream, String threadName) {
        CapturedOutput output = new CapturedOutput(inputStream);
        output.start(threadName);
        return output;
    }

    private static int waitFor(Process process, CapturedOutput stdout, CapturedOutput stderr)
            throws IOException, InterruptedException {
        if (!process.waitFor(GIT_STATUS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            if (!process.waitFor(TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IOException("git status timed out after " + GIT_STATUS_TIMEOUT
                        + " and could not be terminated within " + TERMINATION_TIMEOUT
                        + formatCapturedOutput(stdout, stderr));
            }
            stdout.await();
            stderr.await();
            throw new IOException("git status timed out after " + GIT_STATUS_TIMEOUT
                    + formatCapturedOutput(stdout, stderr));
        }
        stdout.await();
        stderr.await();
        return process.exitValue();
    }

    private static String formatCapturedOutput(CapturedOutput stdout, CapturedOutput stderr) {
        List<String> details = new ArrayList<>();
        String stdoutText = stdout.text();
        if (!stdoutText.isBlank()) {
            details.add("stdout: " + stdoutText);
        }
        String stderrText = stderr.text();
        if (!stderrText.isBlank()) {
            details.add("stderr: " + stderrText);
        }
        if (details.isEmpty()) {
            return "";
        }
        return " (" + String.join("; ", details) + ")";
    }

    private static void ensureCompleteStatusOutput(CapturedOutput stdout, CapturedOutput stderr) throws IOException {
        if (stdout.isTruncated()) {
            throw new IOException("could not fully capture git status output; refusing incomplete changed-file detection"
                    + formatCapturedOutput(stdout, stderr));
        }
    }

    private static List<Path> parseStatusOutput(Path root, byte[] output) throws IOException {
        List<Path> files = new ArrayList<>();
        int index = 0;
        while (index < output.length) {
            if (index + 3 >= output.length) {
                throw new IOException("Unexpected git status output");
            }
            char indexStatus = (char) output[index];
            char workTreeStatus = (char) output[index + 1];
            if (output[index + 2] != ' ') {
                throw new IOException("Unexpected git status output");
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

    private static int nextNullIndex(byte[] output, int start) throws IOException {
        for (int index = start; index < output.length; index++) {
            if (output[index] == 0) {
                return index;
            }
        }
        throw new IOException("Unexpected git status output");
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

    private static final class CapturedOutput {
        private final java.io.InputStream inputStream;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final Object lock = new Object();
        private Thread thread = new Thread(() -> { }, "cognitive-java-git-output");
        private boolean truncated;

        private CapturedOutput(java.io.InputStream inputStream) {
            this.inputStream = inputStream;
        }

        private void start(String threadName) {
            thread = new Thread(() -> {
                try (inputStream) {
                    byte[] chunk = new byte[4096];
                    int read;
                    while ((read = inputStream.read(chunk)) >= 0) {
                        append(chunk, read);
                    }
                } catch (IOException ignored) {
                    markTruncated();
                }
            }, threadName);
            thread.setDaemon(true);
            thread.start();
        }

        private void await() throws InterruptedException {
            if (thread != null) {
                thread.join();
            }
        }

        private byte[] bytes() {
            synchronized (lock) {
                return buffer.toByteArray();
            }
        }

        private boolean isTruncated() {
            synchronized (lock) {
                return truncated;
            }
        }

        private String text() {
            byte[] capturedBytes;
            boolean wasTruncated;
            synchronized (lock) {
                capturedBytes = buffer.toByteArray();
                wasTruncated = truncated;
            }
            String text = new String(capturedBytes, StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) {
                return wasTruncated ? "[output truncated]" : "";
            }
            return wasTruncated ? text + " [output truncated]" : text;
        }

        private void append(byte[] chunk, int read) {
            synchronized (lock) {
                int remaining = MAX_CAPTURED_OUTPUT_BYTES - buffer.size();
                if (remaining > 0) {
                    buffer.write(chunk, 0, Math.min(read, remaining));
                }
                if (read > remaining) {
                    truncated = true;
                }
            }
        }

        private void markTruncated() {
            synchronized (lock) {
                truncated = true;
            }
        }
    }
}

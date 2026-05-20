package media.barney.cognitive.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChangedFileDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void findsModifiedAndUntrackedJavaFiles() throws Exception {
        run(tempDir, "git", "init");
        run(tempDir, "git", "config", "user.email", "test@example.com");
        run(tempDir, "git", "config", "user.name", "test");

        Path src = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(src);
        Path tracked = src.resolve("Tracked.java");
        Files.writeString(tracked, "class Tracked {}\n");

        run(tempDir, "git", "add", ".");
        run(tempDir, "git", "commit", "-m", "init");

        Files.writeString(tracked, "class Tracked { int x = 1; }\n");
        Path untracked = src.resolve("NewFile.java");
        Files.writeString(untracked, "class NewFile {}\n");
        Files.writeString(tempDir.resolve("README.md"), "ignore me\n");

        List<Path> changed = ChangedFileDetector.changedJavaFiles(tempDir);

        assertEquals(List.of(
                tempDir.resolve("src/main/java/demo/NewFile.java"),
                tempDir.resolve("src/main/java/demo/Tracked.java")
        ), changed);
    }

    @Test
    void includesGitErrorOutputWhenStatusFails() {
        IOException error = assertThrows(IOException.class,
                () -> ChangedFileDetector.changedJavaFiles(tempDir));

        assertTrue(Objects.requireNonNull(error.getMessage()).contains("not a git repository"));
    }

    @Test
    void filtersChangedFilesToSourceTreesOnly() throws Exception {
        run(tempDir, "git", "init");
        run(tempDir, "git", "config", "user.email", "test@example.com");
        run(tempDir, "git", "config", "user.name", "test");

        Path mainSrc = tempDir.resolve("src/main/java/demo");
        Path moduleTestSrc = tempDir.resolve("module-a/src/test/java/demo");
        Path nestedMainSrc = tempDir.resolve("module-b/src/main/java/demo");
        Path nonSourceTree = tempDir.resolve("test/cognitive-java");
        Files.createDirectories(mainSrc);
        Files.createDirectories(moduleTestSrc);
        Files.createDirectories(nestedMainSrc);
        Files.createDirectories(nonSourceTree);

        Path tracked = mainSrc.resolve("Tracked.java");
        Files.writeString(tracked, "class Tracked {}\n");
        run(tempDir, "git", "add", ".");
        run(tempDir, "git", "commit", "-m", "init");

        Files.writeString(tracked, "class Tracked { int x = 1; }\n");
        Path nested = moduleTestSrc.resolve("NestedChanged.java");
        Files.writeString(nested, "class NestedChanged {}\n");
        Path nestedMain = nestedMainSrc.resolve("NestedMainChanged.java");
        Files.writeString(nestedMain, "class NestedMainChanged {}\n");
        Files.writeString(nonSourceTree.resolve("ChangedFileDetectorTest.java"), "class ChangedFileDetectorTest {}\n");

        List<Path> changed = ChangedFileDetector.changedJavaFilesUnderSourceRoots(tempDir);

        assertEquals(List.of(
                tempDir.resolve("module-b/src/main/java/demo/NestedMainChanged.java"),
                tempDir.resolve("src/main/java/demo/Tracked.java")
        ), changed);
    }

    @Test
    void parsesUntrackedEntriesFromPorcelainZOutput() {
        assertEquals(List.of(tempDir.resolve("src/main/java/demo/NewFile.java")),
                parseStatus("?? src/main/java/demo/NewFile.java\0"));
    }

    @Test
    void renameRecordsUseTheDestinationPath() {
        assertEquals(List.of(tempDir.resolve("src/main/java/demo/New.java")),
                parseStatus("R  src/main/java/demo/New.java\0src/main/java/demo/Old.java\0"));
    }

    @Test
    void supportsChangedFilesWhoseNamesContainSpaces() throws Exception {
        run(tempDir, "git", "init");
        run(tempDir, "git", "config", "user.email", "test@example.com");
        run(tempDir, "git", "config", "user.name", "test");

        Path src = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(src);
        Path tracked = src.resolve("My File.java");
        Files.writeString(tracked, "class Sample {}\n");

        run(tempDir, "git", "add", ".");
        run(tempDir, "git", "commit", "-m", "init");

        Files.writeString(tracked, "class Sample { int x = 1; }\n");

        List<Path> changed = ChangedFileDetector.changedJavaFilesUnderSourceRoots(tempDir);

        assertEquals(List.of(tracked), changed);
    }

    @Test
    void ignoresDeletedJavaFiles() throws Exception {
        run(tempDir, "git", "init");
        run(tempDir, "git", "config", "user.email", "test@example.com");
        run(tempDir, "git", "config", "user.name", "test");

        Path src = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(src);
        Path tracked = src.resolve("Tracked.java");
        Files.writeString(tracked, "class Tracked {}\n");

        run(tempDir, "git", "add", ".");
        run(tempDir, "git", "commit", "-m", "init");

        Files.delete(tracked);

        List<Path> changed = ChangedFileDetector.changedJavaFilesUnderSourceRoots(tempDir);

        assertEquals(List.of(), changed);
    }

    @Test
    void drainsProcessOutputBeforeWaitingForExit() throws Exception {
        List<Path> changed = ChangedFileDetector.changedJavaFiles(tempDir,
                ignored -> new ReadBeforeWaitProcess("?? src/main/java/demo/NewFile.java\0"));

        assertEquals(List.of(tempDir.resolve("src/main/java/demo/NewFile.java")), changed);
    }

    @Test
    void filtersChangedFilesToConfiguredSourceRootsOnly() throws Exception {
        run(tempDir, "git", "init");
        run(tempDir, "git", "config", "user.email", "test@example.com");
        run(tempDir, "git", "config", "user.name", "test");

        Path customRoot = tempDir.resolve("src/custom/java/demo");
        Path defaultRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(customRoot);
        Files.createDirectories(defaultRoot);
        Path customSource = customRoot.resolve("Custom.java");
        Path defaultSource = defaultRoot.resolve("Default.java");
        Files.writeString(customSource, "class Custom {}\n");
        Files.writeString(defaultSource, "class Default {}\n");

        run(tempDir, "git", "add", ".");
        run(tempDir, "git", "commit", "-m", "init");

        Files.writeString(customSource, "class Custom { int x = 1; }\n");
        Files.writeString(defaultSource, "class Default { int x = 1; }\n");

        List<Path> changed = ChangedFileDetector.changedJavaFilesUnderSourceRoots(
                tempDir,
                List.of(tempDir.resolve("src/custom/java").toAbsolutePath().normalize()));

        assertEquals(List.of(customSource.toAbsolutePath().normalize()), changed);
    }

    @Test
    void timesOutNoisyGitProcessesWithUsefulContext() {
        IOException error = assertThrows(IOException.class,
                () -> ChangedFileDetector.changedJavaFiles(tempDir, ignored -> new TimeoutProcess()));

        assertTrue(Objects.requireNonNull(error.getMessage()).contains("git status timed out after"));
    }

    @Test
    void truncatesCapturedGitOutputOnFailure() {
        String noisy = "x".repeat(ChangedFileDetectorTestSupport.OUTPUT_LENGTH);

        IOException error = assertThrows(IOException.class,
                () -> ChangedFileDetector.changedJavaFiles(tempDir,
                        ignored -> new CompletedProcess(1, noisy, noisy)));

        assertTrue(Objects.requireNonNull(error.getMessage()).contains("git status failed with exit 1"));
        assertTrue(error.getMessage().contains("[output truncated]"));
    }

    private static void run(Path dir, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        if (process.waitFor() != 0) {
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException(output);
        }
    }

    private List<Path> parseStatus(String statusOutput) {
        return assertDoesNotThrow(() -> ChangedFileDetector.changedJavaFiles(tempDir,
                ignored -> new ReadBeforeWaitProcess(statusOutput)));
    }

    private static final class ReadBeforeWaitProcess extends Process {
        private final TrackingInputStream inputStream;

        private ReadBeforeWaitProcess(String output) {
            this.inputStream = new TrackingInputStream(output.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return inputStream;
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            if (!inputStream.fullyRead()) {
                throw new IllegalStateException("waitFor called before stdout was fully read");
            }
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
        }

        @Override
        public Process destroyForcibly() {
            return this;
        }

        @Override
        public boolean isAlive() {
            return false;
        }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean fullyRead;

        private TrackingInputStream(byte[] buffer) {
            super(buffer);
        }

        @Override
        public synchronized int read() {
            int value = super.read();
            fullyRead = value < 0 || pos >= count;
            return value;
        }

        @Override
        public synchronized int read(byte[] buffer, int offset, int length) {
            int read = super.read(buffer, offset, length);
            fullyRead = read < 0 || pos >= count;
            return read;
        }

        private boolean fullyRead() {
            return fullyRead;
        }
    }

    private static final class TimeoutProcess extends Process {
        private boolean destroyed;

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return destroyed ? 143 : 0;
        }

        @Override
        public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit) {
            return destroyed;
        }

        @Override
        public int exitValue() {
            return 143;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }

        @Override
        public Process destroyForcibly() {
            destroyed = true;
            return this;
        }

        @Override
        public boolean isAlive() {
            return !destroyed;
        }
    }

    private static final class CompletedProcess extends Process {
        private final ByteArrayInputStream stdout;
        private final ByteArrayInputStream stderr;
        private final int exitCode;

        private CompletedProcess(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = new ByteArrayInputStream(stdout.getBytes(StandardCharsets.UTF_8));
            this.stderr = new ByteArrayInputStream(stderr.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return stderr;
        }

        @Override
        public int waitFor() {
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit) {
            return true;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
        }

        @Override
        public Process destroyForcibly() {
            return this;
        }

        @Override
        public boolean isAlive() {
            return false;
        }
    }

    private static final class ChangedFileDetectorTestSupport {
        private static final int OUTPUT_LENGTH = 70_000;

        private ChangedFileDetectorTestSupport() {
        }
    }
}

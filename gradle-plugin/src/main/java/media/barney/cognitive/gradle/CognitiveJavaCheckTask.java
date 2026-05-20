package media.barney.cognitive.gradle;

import media.barney.cognitive.core.Main;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

public abstract class CognitiveJavaCheckTask extends DefaultTask {

    private static final String LINK_OWNERSHIP = "link";
    private static final String ENCODED_PATH_PREFIX = "path-base64\t";
    private static final int DEFAULT_THRESHOLD = 15;
    private static final int THRESHOLD_EXCEEDED_EXIT = 2;
    private static final ConcurrentMap<Path, ReentrantLock> IN_PROCESS_STATE_LOCKS = new ConcurrentHashMap<>();

    private final Provider<RegularFile> defaultJunitReport;
    private final Provider<RegularFile> executionMarker;
    private final RegularFileProperty junitReportState;
    private final RegularFileProperty outputState;
    private final RegularFileProperty stateLock;
    private final List<Provider<Directory>> internalExecutionMarkerRootProviders;
    private final List<Path> internalRememberedStateRootPaths;
    private final Path gradleProjectRootPath;
    private final Path projectCacheRootPath;
    private final Provider<String> absentString;
    private final Provider<RegularFile> absentRegularFile;
    private final Map<Path, Boolean> caseSensitivityByDirectory = new HashMap<>();
    private final Map<Path, Boolean> caseSensitivityByFileStore = new HashMap<>();

    public CognitiveJavaCheckTask() {
        gradleProjectRootPath = getProject().getProjectDir().toPath().toAbsolutePath().normalize();
        projectCacheRootPath = projectCacheRoot(getProject());
        absentString = getProject().getProviders().provider(() -> (String) null);
        absentRegularFile = getProject().getProviders().provider(() -> (RegularFile) null);
        defaultJunitReport = getProject().getProviders()
                .provider(this::defaultJunitReportRelativePath)
                .flatMap(path -> getProject().getLayout().getBuildDirectory().file(path));
        executionMarker = getProject().getLayout().getBuildDirectory()
                .file("tmp/cognitive-java/" + getName() + "/execution.marker");
        junitReportState = getProject().getObjects().fileProperty();
        junitReportState.fileValue(localStateFile("junit-report.path"));
        outputState = getProject().getObjects().fileProperty();
        outputState.fileValue(localStateFile("primary-output.path"));
        stateLock = getProject().getObjects().fileProperty();
        stateLock.fileValue(globalStateFile("state.lock"));
        internalExecutionMarkerRootProviders = getProject().getRootProject().getAllprojects().stream()
                .map(project -> project.getLayout().getBuildDirectory().dir("tmp/cognitive-java"))
                .toList();
        internalRememberedStateRootPaths = getProject().getRootProject().getAllprojects().stream()
                .flatMap(project -> {
                    Path stateRoot = projectCacheRoot(project).resolve("cognitive-java");
                    return Stream.of(stateRoot, stateRoot.resolve(projectStateName(project)));
                })
                .distinct()
                .toList();
        getThreshold().convention(DEFAULT_THRESHOLD);
        getAgent().convention(false);
        getFormat().convention(getAgent().map(agent -> agent ? "toon" : "none"));
        getFailuresOnly().convention(getAgent());
        getOmitRedundancy().convention(getAgent());
        getJunit().convention(true);
        getJunitReport().convention(defaultJunitReport);
        getSourceRoots().convention(List.of());
        getExcludes().convention(List.of());
        getExcludeClasses().convention(List.of());
        getExcludeAnnotations().convention(List.of());
        getUseDefaultExclusions().convention(true);
    }

    @Internal
    public abstract DirectoryProperty getAnalysisRoot();

    @Input
    public Provider<String> getAnalysisRootPathInput() {
        return getAnalysisRoot().map(directory ->
                directory.getAsFile().toPath().toAbsolutePath().normalize().toString());
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getAnalysisSources();

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getAnalysisMetadata();

    @Input
    public abstract Property<Integer> getThreshold();

    @Input
    public abstract Property<String> getFormat();

    @Input
    public abstract Property<Boolean> getAgent();

    @Input
    public abstract Property<Boolean> getFailuresOnly();

    @Input
    public abstract Property<Boolean> getOmitRedundancy();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getOutput();

    @Input
    public abstract Property<Boolean> getJunit();

    @Internal
    public abstract RegularFileProperty getJunitReport();

    @Input
    public abstract ListProperty<String> getSourceRoots();

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public Provider<List<File>> getConfiguredSourceRootInputs() {
        return getSourceRoots().map(sourceRoots -> resolvedConfiguredSourceRoots(sourceRoots).stream()
                .map(Path::toFile)
                .toList());
    }

    @Input
    public abstract ListProperty<String> getExcludes();

    @Input
    public abstract ListProperty<String> getExcludeClasses();

    @Input
    public abstract ListProperty<String> getExcludeAnnotations();

    @Input
    public abstract Property<Boolean> getUseDefaultExclusions();

    @Input
    @Optional
    public Provider<String> getDisabledJunitReportPathInput() {
        return getJunit().flatMap(enabled -> enabled
                ? absentString
                : getJunitReport().map(file -> file.getAsFile().toPath().toAbsolutePath().normalize().toString()));
    }

    @OutputFile
    @Optional
    public Provider<RegularFile> getJunitReportOutput() {
        return getJunit().flatMap(enabled -> enabled
                ? getJunitReport()
                : absentRegularFile);
    }

    @OutputFile
    public Provider<RegularFile> getExecutionMarkerOutput() {
        return executionMarker;
    }

    @TaskAction
    void runCheck() throws Exception {
        Path configuredOutputPath = outputPath();
        Path configuredJunitReportPath = junitReportPath();
        validateReportOptions(configuredOutputPath, configuredJunitReportPath);
        List<String> sourceArguments = sourceArguments();
        if (sourceArguments.isEmpty()) {
            runWithoutSourcesWithReportStateLock();
            writeExecutionMarker();
            getLogger().lifecycle("No Java files to analyze.");
            return;
        }
        int exit = runWithReportStateLock(sourceArguments, configuredOutputPath, configuredJunitReportPath);
        if (exit != 0) {
            throw new GradleException("cognitive-java-check failed with exit " + exit);
        }
        writeExecutionMarker();
    }

    private int runWithReportStateLock(
            List<String> sourceArguments,
            Path configuredOutputPath,
            Path configuredJunitReportPath
    ) throws Exception {
        Path lockPath = stateLockPath();
        return withReportStateLock(lockPath, () ->
                runAndRememberReports(sourceArguments, configuredOutputPath, configuredJunitReportPath));
    }

    private void runWithoutSourcesWithReportStateLock() throws Exception {
        withReportStateLock(stateLockPath(), () -> {
            cleanupReportsWithoutSources();
            return null;
        });
    }

    private int runAndRememberReports(
            List<String> sourceArguments,
            Path configuredOutputPath,
            Path configuredJunitReportPath
    ) throws Exception {
        ReportSnapshot outputBefore = reportSnapshot(configuredOutputPath);
        ReportSnapshot junitBefore = reportSnapshot(configuredJunitReportPath);
        try (var out = GradleLoggingPrintStreams.standardOut(getLogger());
             var err = GradleLoggingPrintStreams.standardErr(getLogger())) {
            int exit;
            try {
                exit = Main.run(
                        runnerArguments(sourceArguments, configuredOutputPath, configuredJunitReportPath),
                        getAnalysisRoot().get().getAsFile().toPath().toAbsolutePath().normalize(),
                        out,
                        err
                );
            } catch (Exception exception) {
                rememberChangedReportState(
                        configuredOutputPath,
                        configuredJunitReportPath,
                        outputBefore,
                        junitBefore
                );
                throw exception;
            }
            if (!reportsWereWritten(exit)) {
                rememberChangedReportState(
                        configuredOutputPath,
                        configuredJunitReportPath,
                        outputBefore,
                        junitBefore
                );
                return exit;
            }
            cleanupStaleReports(configuredOutputPath, configuredJunitReportPath);
            rememberReportState(configuredOutputPath, configuredJunitReportPath);
            return exit;
        }
    }

    private boolean reportsWereWritten(int exit) {
        return exit == 0 || exit == THRESHOLD_EXCEEDED_EXIT;
    }

    private String[] runnerArguments(
            List<String> sourceArguments,
            Path configuredOutputPath,
            Path configuredJunitReportPath
    ) {
        List<String> arguments = new ArrayList<>();
        arguments.add("--format");
        arguments.add(getFormat().get());
        arguments.add("--threshold");
        arguments.add(Integer.toString(getThreshold().get()));
        if (getAgent().get()) {
            arguments.add("--agent");
        }
        arguments.add("--failures-only=" + getFailuresOnly().get());
        arguments.add("--omit-redundancy=" + getOmitRedundancy().get());
        addRepeated(arguments, "--source-root", getSourceRoots().get());
        addRepeated(arguments, "--exclude", getExcludes().get());
        addRepeated(arguments, "--exclude-class", getExcludeClasses().get());
        addRepeated(arguments, "--exclude-annotation", getExcludeAnnotations().get());
        if (!getUseDefaultExclusions().get()) {
            arguments.add("--use-default-exclusions=false");
        }
        if (configuredOutputPath != null) {
            arguments.add("--output");
            arguments.add(configuredOutputPath.toString());
        }
        if (configuredJunitReportPath != null) {
            arguments.add("--junit-report");
            arguments.add(configuredJunitReportPath.toString());
        }
        arguments.addAll(sourceArguments);
        return arguments.toArray(String[]::new);
    }

    private static void addRepeated(List<String> arguments, String option, List<String> values) {
        for (String value : values) {
            arguments.add(option);
            arguments.add(value);
        }
    }

    private List<String> sourceArguments() {
        Path analysisRoot = analysisRootPath();
        return selectedSourceFiles().stream()
                .map(file -> analysisRoot.relativize(file).toString().replace('\\', '/'))
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private List<Path> selectedSourceFiles() {
        List<String> configuredSourceRoots = getSourceRoots().get();
        if (configuredSourceRoots.isEmpty()) {
            return getAnalysisSources().getFiles().stream()
                    .map(file -> file.toPath().toAbsolutePath().normalize())
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
        return configuredSourceRootJavaFiles(configuredSourceRoots);
    }

    private List<Path> configuredSourceRootJavaFiles(List<String> configuredSourceRoots) {
        LinkedHashSet<Path> javaFiles = new LinkedHashSet<>();
        for (Path sourceRoot : resolvedConfiguredSourceRoots(configuredSourceRoots)) {
            javaFiles.addAll(javaFilesUnder(sourceRoot));
        }
        return javaFiles.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private List<Path> resolvedConfiguredSourceRoots(List<String> configuredSourceRoots) {
        Path analysisRoot = analysisRootPath();
        LinkedHashSet<Path> sourceRoots = new LinkedHashSet<>();
        for (String configuredSourceRoot : configuredSourceRoots) {
            sourceRoots.add(resolveSourceRoot(analysisRoot, configuredSourceRoot));
        }
        return sourceRoots.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private Path resolveSourceRoot(Path analysisRoot, String configuredSourceRoot) {
        String trimmed = configuredSourceRoot.trim();
        if (trimmed.isEmpty()) {
            throw new GradleException("sourceRoots entries must not be blank");
        }
        Path candidate = Path.of(trimmed);
        Path resolved = candidate.isAbsolute()
                ? candidate.toAbsolutePath().normalize()
                : analysisRoot.resolve(candidate).normalize();
        if (!resolved.startsWith(analysisRoot)) {
            throw new GradleException("sourceRoots entry '" + configuredSourceRoot
                    + "' must stay inside the analysisRoot: " + resolved);
        }
        if (!Files.isDirectory(resolved)) {
            throw new GradleException("sourceRoots entry '" + configuredSourceRoot
                    + "' must point to an existing directory: " + resolved);
        }
        return resolved;
    }

    private List<Path> javaFilesUnder(Path sourceRoot) {
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException exception) {
            throw new GradleException("Failed to read source root: " + sourceRoot, exception);
        }
    }

    private void validateReportOptions(Path outputPath, Path junitReportPath) throws IOException {
        validateReportFormat(getFormat().get());
        validateThreshold(getThreshold().get());
        validateReportPaths(outputPath, junitReportPath);
    }

    private void validateReportFormat(String format) {
        if (format == null) {
            throw new GradleException("Unknown report format: null");
        }
        switch (format.toLowerCase(Locale.ROOT)) {
            case "toon", "json", "text", "junit", "none" -> {
                return;
            }
            default -> throw new GradleException("Unknown report format: " + format);
        }
    }

    private void validateThreshold(int threshold) {
        if (threshold <= 0) {
            throw new GradleException("Threshold must be a positive integer");
        }
    }

    private void validateReportPaths(Path outputPath, Path junitReportPath) throws IOException {
        if (outputPath != null && junitReportPath != null && sameReportTarget(outputPath, junitReportPath)) {
            throw new GradleException("output and junitReport must not point to the same file");
        }
        validateReportPath("output", outputPath);
        validateReportPath("junitReport", junitReportPath);
    }

    private void validateReportPath(String propertyName, Path reportPath) {
        if (reportPath == null) {
            return;
        }
        ensureFileLikeTarget(propertyName, reportPath);
        ensureInsideRoot(propertyName, "Gradle project", gradleProjectRoot(), reportPath);
        ensureInsideRoot(propertyName, "analysisRoot", analysisRootPath(), reportPath);
        ensureNotDirectory(propertyName, reportPath);
        if (isInternalTaskFile(reportPath)) {
            throw new GradleException(propertyName + " must not point to a cognitive-java internal task file: "
                    + reportPath);
        }
    }

    private void ensureFileLikeTarget(String propertyName, Path reportPath) {
        if (reportPath.getFileName() == null) {
            throw new GradleException(propertyName + " must not point to a filesystem root");
        }
    }

    private void ensureInsideRoot(String propertyName, String rootName, Path root, Path reportPath) {
        if (reportPath.equals(root)) {
            throw new GradleException(propertyName + " must not point to the " + rootName + " root");
        }
        if (!reportPath.startsWith(root) || resolvesOutsideRoot(root, reportPath)) {
            throw new GradleException(propertyName + " must stay inside the " + rootName + " root");
        }
    }

    private void ensureNotDirectory(String propertyName, Path reportPath) {
        if (Files.isDirectory(reportPath)) {
            throw new GradleException(propertyName + " must not point to a directory");
        }
    }

    private boolean resolvesOutsideRoot(Path root, Path reportPath) {
        Path rootReal = realPathForComparison(root);
        Path reportReal = realPathForComparison(reportPath);
        return rootReal != null && reportReal != null && !reportReal.startsWith(rootReal);
    }

    private boolean isInternalTaskFile(Path reportPath) {
        return isUnderAnyInternalRoot(reportPath) || sameFileAsExistingInternalFile(reportPath);
    }

    private boolean isUnderAnyInternalRoot(Path reportPath) {
        return internalTaskRoots().stream()
                .anyMatch(internalRoot -> isUnderInternalRoot(reportPath, internalRoot));
    }

    private List<Path> internalExecutionMarkerRoots() {
        return internalExecutionMarkerRootProviders.stream()
                .map(Provider::get)
                .map(directory -> directory.getAsFile().toPath().toAbsolutePath().normalize())
                .toList();
    }

    private List<Path> internalRememberedStateRoots() {
        return internalRememberedStateRootPaths;
    }

    private List<Path> internalTaskRoots() {
        return Stream.concat(internalExecutionMarkerRoots().stream(), internalRememberedStateRoots().stream())
                .distinct()
                .toList();
    }

    private boolean sameFileAsExistingInternalFile(Path reportPath) {
        if (!Files.exists(reportPath)) {
            return false;
        }
        return internalTaskRoots().stream()
                .anyMatch(internalRoot -> sameFileAsExistingInternalFile(reportPath, internalRoot));
    }

    private boolean sameFileAsExistingInternalFile(Path reportPath, Path internalRoot) {
        if (!Files.isDirectory(internalRoot)) {
            return false;
        }
        try (Stream<Path> candidates = Files.walk(internalRoot)) {
            return candidates
                    .filter(this::isInternalStateOrMarkerFile)
                    .filter(Files::isRegularFile)
                    .anyMatch(candidate -> sameFile(reportPath, candidate));
        } catch (IOException | SecurityException exception) {
            return false;
        }
    }

    private boolean isInternalStateOrMarkerFile(Path path) {
        return isInternalFileName(path, "execution.marker")
                || isInternalFileName(path, "primary-output.path")
                || isInternalFileName(path, "junit-report.path")
                || isInternalFileName(path, "state.lock");
    }

    private boolean isInternalFileName(Path path, String internalFileName) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }
        String name = fileName.toString();
        return name.equals(internalFileName)
                || sameCaseInsensitiveFileName(name, internalFileName, path.getParent());
    }

    private boolean sameFile(Path first, Path second) {
        try {
            return !first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize())
                    && Files.isSameFile(first, second);
        } catch (IOException | SecurityException exception) {
            return false;
        }
    }

    private boolean isUnderInternalRoot(Path reportPath, Path internalRoot) {
        return reportPath.startsWith(internalRoot) || realPathStartsWith(reportPath, internalRoot);
    }

    private boolean realPathStartsWith(Path reportPath, Path internalRoot) {
        Path realReportPath = realPathForComparison(reportPath);
        Path realInternalRoot = realPathForComparison(internalRoot);
        return realReportPath != null && realInternalRoot != null && realReportPath.startsWith(realInternalRoot);
    }

    private Path realPathForComparison(Path path) {
        return realPathForComparison(path, 0);
    }

    private Path realPathForComparison(Path path, int symlinkDepth) {
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

    private Path symbolicLinkTargetForComparison(Path link, int symlinkDepth) throws IOException {
        Path target = Files.readSymbolicLink(link);
        Path resolved = link.resolveSibling(target);
        return realPathForComparison(resolved, symlinkDepth + 1);
    }

    private Path nearestExistingPath(Path path) {
        Path current = path;
        while (current != null) {
            if (Files.exists(current)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private boolean sameCaseInsensitiveFileName(String firstName, String secondName, Path parent) {
        return firstName.equalsIgnoreCase(secondName) && isCaseInsensitive(parent);
    }

    private boolean isCaseInsensitive(Path path) {
        Path directory = nearestExistingDirectory(path);
        if (directory == null) {
            return isLikelyCaseInsensitiveOs();
        }
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        Boolean cachedDirectoryResult = caseSensitivityByDirectory.get(normalizedDirectory);
        if (cachedDirectoryResult != null) {
            return cachedDirectoryResult;
        }
        Boolean probed = probeCaseInsensitivity(normalizedDirectory);
        if (probed != null) {
            caseSensitivityByDirectory.put(normalizedDirectory, probed);
            return probed;
        }
        Boolean fileStoreResult = cachedFileStoreCaseSensitivity(normalizedDirectory);
        caseSensitivityByDirectory.put(normalizedDirectory, fileStoreResult);
        return fileStoreResult;
    }

    private Boolean probeCaseInsensitivity(Path directory) {
        try {
            Path probe = Files.createTempFile(directory, ".cognitive-java-case-", ".tmp");
            try {
                return caseVariantExists(probe);
            } finally {
                Files.deleteIfExists(probe);
            }
        } catch (IOException | SecurityException exception) {
            return null;
        }
    }

    private boolean cachedFileStoreCaseSensitivity(Path directory) {
        try {
            Path realDirectory = directory.toRealPath();
            FileStore fileStore = Files.getFileStore(realDirectory);
            Path fileStoreKey = realDirectory.getRoot() == null ? realDirectory : realDirectory.getRoot();
            Path cacheKey = fileStoreKey.toAbsolutePath().normalize();
            Boolean cached = caseSensitivityByFileStore.get(cacheKey);
            if (cached != null) {
                return cached;
            }
            boolean fallback = isLikelyCaseInsensitiveOs();
            caseSensitivityByFileStore.put(cacheKey, fallback);
            return fallback;
        } catch (IOException | SecurityException exception) {
            return isLikelyCaseInsensitiveOs();
        }
    }

    private Path nearestExistingDirectory(Path path) {
        Path start = path == null ? Path.of(".").toAbsolutePath().normalize() : path.toAbsolutePath().normalize();
        return ancestors(start).filter(Files::isDirectory).findFirst().orElse(null);
    }

    private Stream<Path> ancestors(Path path) {
        return Stream.iterate(path, Objects::nonNull, Path::getParent);
    }

    private boolean caseVariantExists(Path probe) {
        Path variant = probe.resolveSibling(probe.getFileName().toString().toUpperCase(Locale.ROOT));
        return !probe.getFileName().toString().equals(variant.getFileName().toString()) && Files.exists(variant);
    }

    static boolean isLikelyCaseInsensitiveOs() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.startsWith("windows");
    }

    private void cleanupReportsWithoutSources() throws Exception {
        deleteRememberedReport(rememberedOutputPath());
        deleteRememberedReport(rememberedJunitReportPath());
        deleteReportState(outputStatePath());
        deleteReportState(junitReportStatePath());
    }

    private void cleanupStaleReports(Path currentOutputPath, Path currentJunitReportPath) throws Exception {
        deleteMovedOutput(currentOutputPath, currentJunitReportPath);
        deleteMovedJunitReport(currentJunitReportPath, currentOutputPath);
        deleteDisabledJunitReport(currentOutputPath);
    }

    private void rememberReportState(Path currentOutputPath, Path currentJunitReportPath) throws Exception {
        rememberOutputPath(currentOutputPath);
        rememberJunitReportPath(currentJunitReportPath);
    }

    private void rememberChangedReportState(
            Path currentOutputPath,
            Path currentJunitReportPath,
            ReportSnapshot outputBefore,
            ReportSnapshot junitBefore
    ) throws Exception {
        RememberedReport rememberedOutput = rememberedOutputPath();
        RememberedReport rememberedJunitReport = rememberedJunitReportPath();
        deleteNewUnrememberedChangedReport(currentOutputPath, outputBefore, rememberedOutput);
        deleteNewUnrememberedChangedReport(currentJunitReportPath, junitBefore, rememberedJunitReport);
        if (shouldRememberChangedReport(currentOutputPath, outputBefore, rememberedOutput)) {
            rememberOutputPath(currentOutputPath);
        }
        if (shouldRememberChangedReport(currentJunitReportPath, junitBefore, rememberedJunitReport)) {
            rememberJunitReportPath(currentJunitReportPath);
        }
    }

    private void deleteNewUnrememberedChangedReport(
            Path reportPath,
            ReportSnapshot before,
            RememberedReport rememberedReport
    ) throws IOException {
        if (rememberedReport == null || before.exists()) {
            return;
        }
        if (isCurrentRememberedPath(rememberedReport, reportPath)) {
            return;
        }
        if (reportChanged(reportPath, before)) {
            Files.deleteIfExists(reportPath);
        }
    }

    private boolean shouldRememberChangedReport(
            Path reportPath,
            ReportSnapshot before,
            RememberedReport rememberedReport
    ) throws IOException {
        return reportChanged(reportPath, before)
                && (rememberedReport == null || isCurrentRememberedPath(rememberedReport, reportPath));
    }

    private boolean reportChanged(Path reportPath, ReportSnapshot before) throws IOException {
        return reportPath != null && !reportSnapshot(reportPath).equals(before);
    }

    private ReportSnapshot reportSnapshot(Path reportPath) throws IOException {
        if (reportPath == null || !Files.isRegularFile(reportPath)) {
            return ReportSnapshot.missing();
        }
        BasicFileAttributes attributes = Files.readAttributes(reportPath, BasicFileAttributes.class);
        return new ReportSnapshot(
                true,
                attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS),
                attributes.size()
        );
    }

    private Path stateLockPath() {
        return stateLock.get().getAsFile().toPath().toAbsolutePath().normalize();
    }

    private ReentrantLock inProcessStateLock(Path lockPath) {
        return IN_PROCESS_STATE_LOCKS.computeIfAbsent(lockPath, ignored -> new ReentrantLock());
    }

    private <T> T withReportStateLock(Path lockPath, LockedAction<T> action) throws Exception {
        Files.createDirectories(lockPath.getParent());
        ReentrantLock inProcessLock = inProcessStateLock(lockPath);
        inProcessLock.lock();
        try {
            try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                return action.run();
            }
        } finally {
            inProcessLock.unlock();
        }
    }

    private void writeExecutionMarker() throws Exception {
        Path markerPath = executionMarkerPath();
        Files.createDirectories(markerPath.getParent());
        Files.writeString(markerPath, "ok\n");
    }

    private Path executionMarkerPath() {
        return getExecutionMarkerOutput().get().getAsFile().toPath().toAbsolutePath().normalize();
    }

    private void deleteMovedOutput(Path currentPath, Path otherCurrentPath) throws Exception {
        RememberedReport rememberedReport = rememberedOutputPath();
        deleteRememberedOutputIfMoved(rememberedReport, currentPath, otherCurrentPath);
        deleteOutputStateIfUnset(currentPath);
    }

    private void deleteRememberedOutputIfMoved(
            RememberedReport rememberedReport,
            Path currentPath,
            Path otherCurrentPath
    ) throws Exception {
        if (shouldKeepRememberedReport(rememberedReport, currentPath, otherCurrentPath)) {
            return;
        }
        deleteRememberedReport(rememberedReport);
    }

    private boolean shouldKeepRememberedReport(
            RememberedReport rememberedReport,
            Path currentPath,
            Path otherCurrentPath
    ) throws IOException {
        return rememberedReport == null
                || isCurrentRememberedPath(rememberedReport, currentPath)
                || isCurrentRememberedPath(rememberedReport, otherCurrentPath);
    }

    private boolean isCurrentRememberedPath(RememberedReport rememberedReport, Path currentPath) throws IOException {
        return currentPath != null && sameReportTarget(rememberedReport.path(), currentPath);
    }

    private void deleteOutputStateIfUnset(Path currentPath) throws Exception {
        if (currentPath == null) {
            deleteReportState(outputStatePath());
        }
    }

    private void deleteMovedJunitReport(Path currentPath, Path otherCurrentPath) throws Exception {
        if (currentPath == null) {
            return;
        }
        RememberedReport rememberedReport = rememberedJunitReportPath();
        if (!shouldKeepRememberedReport(rememberedReport, currentPath, otherCurrentPath)) {
            deleteRememberedReport(rememberedReport);
        }
    }

    private Path outputPath() {
        if (!getOutput().isPresent()) {
            return null;
        }
        return getOutput().get().getAsFile().toPath().toAbsolutePath().normalize();
    }

    private Path junitReportPath() {
        if (!getJunit().get()) {
            return null;
        }
        return getJunitReport().get().getAsFile().toPath().toAbsolutePath().normalize();
    }

    private void deleteDisabledJunitReport(Path currentOutputPath) throws Exception {
        if (getJunit().get()) {
            return;
        }
        RememberedReport rememberedReport = rememberedJunitReportPath();
        if (!shouldKeepRememberedReport(rememberedReport, currentOutputPath, null)) {
            deleteRememberedReport(rememberedReport);
        }
        deleteReportState(junitReportStatePath());
    }

    private void deleteRememberedReport(RememberedReport rememberedReport) throws Exception {
        if (!isOwnedRememberedReport(rememberedReport)) {
            return;
        }
        Files.deleteIfExists(rememberedReport.path());
    }

    private boolean isOwnedRememberedReport(RememberedReport rememberedReport) throws Exception {
        if (!hasRegularRememberedReport(rememberedReport)) {
            return false;
        }
        if (!hasCurrentOwnerLink(rememberedReport)) {
            return false;
        }
        if (hasOtherOwnerLink(rememberedReport)) {
            return false;
        }
        return hasCurrentOwnership(rememberedReport);
    }

    private boolean hasRegularRememberedReport(RememberedReport rememberedReport) {
        return rememberedReport != null && Files.isRegularFile(rememberedReport.path());
    }

    private boolean hasCurrentOwnerLink(RememberedReport rememberedReport) throws IOException {
        if (!rememberedReport.ownership().startsWith(LINK_OWNERSHIP + "\t")) {
            return false;
        }
        if (!Files.exists(rememberedReport.ownerLink())) {
            return false;
        }
        return Files.isSameFile(rememberedReport.path(), rememberedReport.ownerLink());
    }

    private boolean hasCurrentOwnership(RememberedReport rememberedReport) throws Exception {
        return rememberedReport.ownership().equals(ownership(rememberedReport.path()));
    }

    private boolean hasOtherOwnerLink(RememberedReport rememberedReport) throws IOException {
        Path stateRoot = projectCacheRootPath.resolve("cognitive-java");
        if (!Files.isDirectory(stateRoot)) {
            return false;
        }
        try (Stream<Path> paths = Files.walk(stateRoot)) {
            for (Path path : paths.filter(this::isOwnerLink).toList()) {
                if (!path.equals(rememberedReport.ownerLink()) && sameExistingFile(path, rememberedReport.path())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isOwnerLink(Path path) {
        return path.getFileName() != null && path.getFileName().toString().endsWith(".owner");
    }

    private String defaultJunitReportRelativePath() {
        if ("cognitive-java-check".equals(getName())) {
            return "reports/cognitive-java/TEST-cognitive-java.xml";
        }
        return "reports/cognitive-java/" + getName() + "/TEST-cognitive-java.xml";
    }

    private void rememberOutputPath(Path path) throws Exception {
        if (path == null) {
            Files.deleteIfExists(outputStatePath());
            return;
        }
        rememberReportPath(outputStatePath(), path);
    }

    private RememberedReport rememberedOutputPath() throws Exception {
        return rememberedReportPath(outputStatePath());
    }

    private Path outputStatePath() {
        return outputState.get().getAsFile().toPath().toAbsolutePath().normalize();
    }

    private void rememberJunitReportPath(Path path) throws Exception {
        if (path == null) {
            return;
        }
        rememberReportPath(junitReportStatePath(), path);
    }

    private void rememberReportPath(Path statePath, Path reportPath) throws Exception {
        Files.createDirectories(statePath.getParent());
        Path ownerLink = ownerLinkPath(statePath);
        Files.deleteIfExists(ownerLink);
        String ownership = ownership(reportPath, ownerLink);
        if (ownership.isBlank()) {
            Files.deleteIfExists(statePath);
            return;
        }
        Files.writeString(statePath, encodeRememberedReportPath(reportPath) + "\n" + ownership + "\n");
    }

    private String encodeRememberedReportPath(Path reportPath) {
        String encoded = Base64.getEncoder()
                .encodeToString(reportPath.toString().getBytes(StandardCharsets.UTF_8));
        return ENCODED_PATH_PREFIX + encoded;
    }

    private String ownership(Path reportPath, Path ownerLink) throws Exception {
        try {
            Files.createLink(ownerLink, reportPath);
            return ownership(reportPath);
        } catch (IOException | SecurityException | UnsupportedOperationException exception) {
            getLogger().warn(
                    "cognitive-java could not remember ownership for {}; stale cleanup for that report path is disabled.",
                    reportPath);
            return "";
        }
    }

    private String ownership(Path reportPath) throws Exception {
        BasicFileAttributes attributes = Files.readAttributes(reportPath, BasicFileAttributes.class);
        return LINK_OWNERSHIP + "\t"
                + attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS) + "\t"
                + attributes.size();
    }

    private void deleteReportState(Path statePath) throws Exception {
        Files.deleteIfExists(ownerLinkPath(statePath));
        Files.deleteIfExists(statePath);
    }

    private Path ownerLinkPath(Path statePath) {
        String fileName = statePath.getFileName().toString();
        String ownerFileName = fileName.endsWith(".path")
                ? fileName.substring(0, fileName.length() - ".path".length()) + ".owner"
                : fileName + ".owner";
        return statePath.resolveSibling(ownerFileName);
    }

    private RememberedReport rememberedJunitReportPath() throws Exception {
        return rememberedReportPath(junitReportStatePath());
    }

    private RememberedReport rememberedReportPath(Path statePath) throws Exception {
        if (!Files.isRegularFile(statePath)) {
            return null;
        }
        return parseRememberedReport(statePath, Files.readAllLines(statePath));
    }

    private RememberedReport parseRememberedReport(Path statePath, List<String> lines) {
        if (!hasRememberedReport(lines)) {
            return null;
        }
        Path reportPath = parseRememberedReportPath(lines.get(0));
        if (reportPath == null) {
            return null;
        }
        return new RememberedReport(reportPath, lines.get(1), ownerLinkPath(statePath));
    }

    private Path parseRememberedReportPath(String line) {
        try {
            String path = line.startsWith(ENCODED_PATH_PREFIX)
                    ? decodeRememberedReportPath(line.substring(ENCODED_PATH_PREFIX.length()))
                    : line;
            return path == null ? null : Path.of(path).toAbsolutePath().normalize();
        } catch (IllegalArgumentException | SecurityException exception) {
            return null;
        }
    }

    private String decodeRememberedReportPath(String encoded) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean hasRememberedReport(List<String> lines) {
        return lines.size() >= 2 && !lines.get(0).isBlank() && !lines.get(1).isBlank();
    }

    private Path junitReportStatePath() {
        return junitReportState.get().getAsFile().toPath().toAbsolutePath().normalize();
    }

    private File localStateFile(String fileName) {
        return localStateRoot(getProject()).resolve(getName()).resolve(fileName).toFile();
    }

    private File globalStateFile(String fileName) {
        return projectCacheRoot(getProject()).resolve("cognitive-java").resolve(fileName).toFile();
    }

    private Path localStateRoot(Project project) {
        Path stateRoot = projectCacheRoot(project).resolve("cognitive-java");
        if (hasCustomProjectCacheDir(project)) {
            stateRoot = stateRoot.resolve(rootProjectStateName(project));
        }
        return stateRoot.resolve(projectStateName(project));
    }

    private Path projectCacheRoot(Project project) {
        File projectCacheDir = project.getGradle().getStartParameter().getProjectCacheDir();
        if (projectCacheDir != null) {
            return projectCacheDir.toPath().toAbsolutePath().normalize();
        }
        return project.getRootProject().getProjectDir().toPath().resolve(".gradle").toAbsolutePath().normalize();
    }

    private boolean hasCustomProjectCacheDir(Project project) {
        return project.getGradle().getStartParameter().getProjectCacheDir() != null;
    }

    private String rootProjectStateName(Project project) {
        String rootPath = project.getRootProject().getProjectDir().toPath().toAbsolutePath().normalize().toString();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rootPath.getBytes(StandardCharsets.UTF_8));
            return "workspace-" + HexFormat.of().formatHex(hash, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String projectStateName(Project project) {
        String projectPath = project.getPath();
        if (":".equals(projectPath)) {
            return "root";
        }
        return projectPath.replace("%", "%25").replace(":", "%3A");
    }

    private boolean sameReportTarget(Path first, Path second) throws IOException {
        if (first.equals(second)) {
            return true;
        }
        return sameExistingFile(first, second)
                || sameRealPath(first, second)
                || sameParentAndFileName(first, second);
    }

    private boolean sameExistingFile(Path first, Path second) throws IOException {
        return Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second);
    }

    private boolean sameParentAndFileName(Path first, Path second) throws IOException {
        Path firstParent = first.getParent();
        Path secondParent = second.getParent();
        Path firstFileName = first.getFileName();
        Path secondFileName = second.getFileName();
        return firstFileName != null
                && secondFileName != null
                && sameParent(firstParent, secondParent)
                && sameFileName(firstFileName.toString(), secondFileName.toString(), firstParent);
    }

    private boolean sameParent(Path firstParent, Path secondParent) throws IOException {
        return (firstParent == null || secondParent == null)
                ? firstParent == secondParent
                : sameNonNullParent(firstParent, secondParent);
    }

    private boolean sameNonNullParent(Path firstParent, Path secondParent) throws IOException {
        return firstParent.equals(secondParent)
                || sameAliasedParent(firstParent, secondParent);
    }

    private boolean sameAliasedParent(Path firstParent, Path secondParent) throws IOException {
        return sameExistingFile(firstParent, secondParent)
                || sameRealPath(firstParent, secondParent)
                || sameCaseInsensitivePath(firstParent, secondParent);
    }

    private boolean sameRealPath(Path first, Path second) {
        Path firstRealPath = realPathForComparison(first);
        Path secondRealPath = realPathForComparison(second);
        return firstRealPath != null && firstRealPath.equals(secondRealPath);
    }

    private boolean sameCaseInsensitivePath(Path first, Path second) {
        return first.toString().equalsIgnoreCase(second.toString()) && isCaseInsensitive(first);
    }

    private boolean sameFileName(String firstName, String secondName, Path parent) {
        return firstName.equals(secondName) || sameCaseInsensitiveFileName(firstName, secondName, parent);
    }

    private Path gradleProjectRoot() {
        return gradleProjectRootPath;
    }

    private Path analysisRootPath() {
        return getAnalysisRoot().get().getAsFile().toPath().toAbsolutePath().normalize();
    }

    private record RememberedReport(Path path, String ownership, Path ownerLink) {
    }

    private record ReportSnapshot(boolean exists, long modifiedNanos, long size) {
        private static ReportSnapshot missing() {
            return new ReportSnapshot(false, 0, 0);
        }
    }

    @FunctionalInterface
    private interface LockedAction<T> {
        T run() throws Exception;
    }
}

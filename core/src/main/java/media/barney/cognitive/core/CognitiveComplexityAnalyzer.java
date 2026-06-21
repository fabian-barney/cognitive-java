package media.barney.cognitive.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class CognitiveComplexityAnalyzer {

    private CognitiveComplexityAnalyzer() {
    }

    static List<MethodMetrics> analyze(List<Path> files) throws IOException {
        return analyze(Path.of(".").toAbsolutePath().normalize(), files);
    }

    static List<MethodMetrics> analyze(Path projectRoot, List<Path> files) throws IOException {
        List<ParsedMethod> parsedMethods = new ArrayList<>();
        Path root = projectRoot.toAbsolutePath().normalize();
        for (Path file : files) {
            parsedMethods.addAll(parseDetailed(root, file));
        }
        return metricsForParsedMethods(parsedMethods);
    }

    static List<MethodMetrics> analyze(Path projectRoot,
                                       List<Path> files,
                                       SourceExclusionMatcher exclusions,
                                       SourceExclusionAudit.Builder audit) throws IOException {
        List<ParsedMethod> includedMethods = new ArrayList<>();
        Set<String> excludedClasses = new LinkedHashSet<>();
        Path root = projectRoot.toAbsolutePath().normalize();
        for (Path file : files) {
            addIncludedMethods(includedMethods, parseDetailed(root, file), exclusions, audit, excludedClasses);
        }
        audit.recordAnalyzedMethods(includedMethods.size());
        return metricsForParsedMethods(includedMethods);
    }

    private static List<ParsedMethod> parseDetailed(Path root, Path file) throws IOException {
        Path normalized = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("Source file does not exist: " + normalized);
        }
        return JavaMethodParser.parseDetailed(sourceName(root, normalized), Files.readString(normalized));
    }

    private static void addIncludedMethods(List<ParsedMethod> includedMethods,
                                           List<ParsedMethod> parsedMethods,
                                           SourceExclusionMatcher exclusions,
                                           SourceExclusionAudit.Builder audit,
                                           Set<String> excludedClasses) {
        for (ParsedMethod parsedMethod : parsedMethods) {
            if (recordExcludedClass(parsedMethod, exclusions, audit, excludedClasses)) {
                continue;
            }
            includedMethods.add(parsedMethod);
        }
    }

    private static boolean recordExcludedClass(ParsedMethod parsedMethod,
                                               SourceExclusionMatcher exclusions,
                                               SourceExclusionAudit.Builder audit,
                                               Set<String> excludedClasses) {
        Optional<String> classExclusion = exclusions.classExclusionReason(
                parsedMethod.className(),
                parsedMethod.classAnnotations());
        if (classExclusion.isEmpty()) {
            return false;
        }
        if (excludedClasses.add(parsedMethod.className())) {
            audit.recordExcludedClass(classExclusion.get());
        }
        return true;
    }

    static List<MethodMetrics> analyzeSources(Map<String, String> sources) {
        List<ParsedMethod> parsedMethods = new ArrayList<>();
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            parsedMethods.addAll(JavaMethodParser.parseDetailed(entry.getKey(), entry.getValue()));
        }
        return metricsForParsedMethods(parsedMethods);
    }

    static List<MethodMetrics> metricsForParsedMethods(List<ParsedMethod> parsedMethods) {
        Map<String, Integer> complexitiesByMethodId = complexitiesByMethodId(parsedMethods);
        List<MethodMetrics> metrics = new ArrayList<>(parsedMethods.size());
        for (ParsedMethod parsedMethod : parsedMethods) {
            metrics.add(new MethodMetrics(
                    parsedMethod.displayName(),
                    parsedMethod.className(),
                    parsedMethod.sourcePath(),
                    parsedMethod.startLine(),
                    parsedMethod.endLine(),
                    Objects.requireNonNull(complexitiesByMethodId.get(parsedMethod.id()))));
        }
        metrics.sort(Comparator
                .comparingInt(MethodMetrics::cognitiveComplexity)
                .reversed()
                .thenComparing(MethodMetrics::sourcePath)
                .thenComparing(MethodMetrics::methodName)
                .thenComparingInt(MethodMetrics::startLine));
        return metrics;
    }

    static Map<String, Integer> complexitiesByMethodId(List<ParsedMethod> parsedMethods) {
        Set<String> recursiveMethodIds = recursiveMethodIds(parsedMethods);
        Map<String, Integer> complexities = new LinkedHashMap<>();
        for (ParsedMethod parsedMethod : parsedMethods) {
            int cognitiveComplexity = parsedMethod.baseCognitiveComplexity()
                    + (recursiveMethodIds.contains(parsedMethod.id()) ? 1 : 0);
            complexities.put(parsedMethod.id(), cognitiveComplexity);
        }
        return complexities;
    }

    private static Set<String> recursiveMethodIds(List<ParsedMethod> parsedMethods) {
        Map<MethodKey, List<ParsedMethod>> declarationsByKey = new HashMap<>();
        Set<String> classNames = new LinkedHashSet<>();
        Map<String, Set<String>> classNamesBySimpleName = new HashMap<>();
        for (ParsedMethod parsedMethod : parsedMethods) {
            declarationsByKey.computeIfAbsent(parsedMethod.key(), ignored -> new ArrayList<>()).add(parsedMethod);
            classNames.add(parsedMethod.className());
            classNamesBySimpleName
                    .computeIfAbsent(simpleName(parsedMethod.className()), ignored -> new LinkedHashSet<>())
                    .add(parsedMethod.className());
        }

        Map<String, Set<String>> edges = new LinkedHashMap<>();
        for (ParsedMethod parsedMethod : parsedMethods) {
            Set<String> targets = new LinkedHashSet<>();
            for (MethodCall call : parsedMethod.calls()) {
                for (ParsedMethod target : resolveTargets(
                        parsedMethod,
                        call,
                        declarationsByKey,
                        classNames,
                        classNamesBySimpleName)) {
                    targets.add(target.id());
                }
            }
            edges.put(parsedMethod.id(), targets);
        }
        return stronglyConnectedRecursiveMembers(edges);
    }

    private static List<ParsedMethod> resolveTargets(ParsedMethod source,
                                                     MethodCall call,
                                                     Map<MethodKey, List<ParsedMethod>> declarationsByKey,
                                                     Set<String> classNames,
                                                     Map<String, Set<String>> classNamesBySimpleName) {
        if (call.sameClass()) {
            return targetsForOwner(source.className(), call, declarationsByKey);
        }
        String ownerName = call.ownerName();
        if (ownerName == null) {
            return List.of();
        }

        return targetsForOwners(
                candidateOwners(source.packageName(), ownerName, classNames, classNamesBySimpleName),
                call,
                declarationsByKey);
    }

    private static Set<String> candidateOwners(String packageName,
                                               String ownerName,
                                               Set<String> classNames,
                                               Map<String, Set<String>> classNamesBySimpleName) {
        Set<String> owners = new LinkedHashSet<>();
        addKnownOwner(owners, ownerName, classNames);
        addKnownOwner(owners, qualifiedNameInPackage(packageName, ownerName), classNames);
        addSimpleNameMatch(owners, ownerName, classNamesBySimpleName);
        return owners;
    }

    private static void addKnownOwner(Set<String> owners, String ownerName, Set<String> classNames) {
        if (classNames.contains(ownerName)) {
            owners.add(ownerName);
        }
    }

    private static void addSimpleNameMatch(Set<String> owners,
                                           String ownerName,
                                           Map<String, Set<String>> classNamesBySimpleName) {
        if (!owners.isEmpty() || ownerName.contains(".")) {
            return;
        }
        Set<String> simpleMatches = classNamesBySimpleName.getOrDefault(ownerName, Set.of());
        if (simpleMatches.size() == 1) {
            owners.addAll(simpleMatches);
        }
    }

    private static List<ParsedMethod> targetsForOwners(Set<String> owners,
                                                       MethodCall call,
                                                       Map<MethodKey, List<ParsedMethod>> declarationsByKey) {
        List<ParsedMethod> targets = new ArrayList<>();
        for (String owner : owners) {
            targets.addAll(targetsForOwner(owner, call, declarationsByKey));
        }
        return targets;
    }

    private static List<ParsedMethod> targetsForOwner(String owner,
                                                      MethodCall call,
                                                      Map<MethodKey, List<ParsedMethod>> declarationsByKey) {
        return declarationsByKey.getOrDefault(new MethodKey(owner, call.methodName(), call.arity()), List.of());
    }

    private static Set<String> stronglyConnectedRecursiveMembers(Map<String, Set<String>> edges) {
        Set<String> recursiveMembers = new LinkedHashSet<>();
        Tarjan tarjan = new Tarjan(edges, recursiveMembers);
        for (String node : edges.keySet()) {
            tarjan.visit(node);
        }
        return recursiveMembers;
    }

    private static String simpleName(String className) {
        int separator = className.lastIndexOf('.');
        return separator < 0 ? className : className.substring(separator + 1);
    }

    private static String qualifiedNameInPackage(String packageName, String className) {
        return packageName.isEmpty() ? className : packageName + "." + className;
    }

    private static String sourceName(Path projectRoot, Path file) {
        Path sourcePath = file.startsWith(projectRoot) ? projectRoot.relativize(file) : file;
        return sourcePath.toString().replace('\\', '/');
    }

    private static final class Tarjan {
        private final Map<String, Set<String>> edges;
        private final Set<String> recursiveMembers;
        private final Map<String, Integer> indexByNode = new HashMap<>();
        private final Map<String, Integer> lowLinkByNode = new HashMap<>();
        private final Deque<String> stack = new ArrayDeque<>();
        private final Set<String> onStack = new HashSet<>();
        private int nextIndex;

        private Tarjan(Map<String, Set<String>> edges, Set<String> recursiveMembers) {
            this.edges = edges;
            this.recursiveMembers = recursiveMembers;
        }

        private void visit(String node) {
            if (!indexByNode.containsKey(node)) {
                strongConnect(node);
            }
        }

        private void strongConnect(String node) {
            indexByNode.put(node, nextIndex);
            lowLinkByNode.put(node, nextIndex);
            nextIndex++;
            stack.push(node);
            onStack.add(node);

            for (String target : edges.getOrDefault(node, Set.of())) {
                visitEdge(node, target);
            }

            if (!isComponentRoot(node)) {
                return;
            }

            List<String> component = popComponent(node);
            if (isRecursiveComponent(component)) {
                recursiveMembers.addAll(component);
            }
        }

        private void visitEdge(String node, String target) {
            if (!indexByNode.containsKey(target)) {
                strongConnect(target);
                lowLinkByNode.put(node, Math.min(lowLink(node), lowLink(target)));
            } else if (onStack.contains(target)) {
                lowLinkByNode.put(node, Math.min(lowLink(node), index(target)));
            }
        }

        private boolean isComponentRoot(String node) {
            return lowLink(node) == index(node);
        }

        private List<String> popComponent(String node) {
            List<String> component = new ArrayList<>();
            String member;
            do {
                member = stack.pop();
                onStack.remove(member);
                component.add(member);
            } while (!member.equals(node));
            return component;
        }

        private boolean isRecursiveComponent(List<String> component) {
            if (component.size() > 1) {
                return true;
            }
            String singleton = component.get(0);
            return edges.getOrDefault(singleton, Set.of()).contains(singleton);
        }

        private int index(String node) {
            return Objects.requireNonNull(indexByNode.get(node));
        }

        private int lowLink(String node) {
            return Objects.requireNonNull(lowLinkByNode.get(node));
        }
    }
}

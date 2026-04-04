package media.barney.cognitivejava.core;

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
import java.util.Set;

final class CognitiveComplexityAnalyzer {

    private CognitiveComplexityAnalyzer() {
    }

    static List<MethodMetrics> analyze(List<Path> files) throws IOException {
        List<ParsedMethod> parsedMethods = new ArrayList<>();
        for (Path file : files) {
            Path normalized = file.normalize();
            if (!Files.isRegularFile(normalized)) {
                throw new IllegalArgumentException("Source file does not exist: " + normalized);
            }
            parsedMethods.addAll(JavaMethodParser.parseDetailed(sourceName(normalized), Files.readString(normalized)));
        }
        return metricsForParsedMethods(parsedMethods);
    }

    static List<MethodMetrics> analyzeSources(Map<String, String> sources) {
        List<ParsedMethod> parsedMethods = new ArrayList<>();
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            parsedMethods.addAll(JavaMethodParser.parseDetailed(entry.getKey(), entry.getValue()));
        }
        return metricsForParsedMethods(parsedMethods);
    }

    static List<MethodMetrics> metricsForParsedMethods(List<ParsedMethod> parsedMethods) {
        Set<String> recursiveMethodIds = recursiveMethodIds(parsedMethods);
        List<MethodMetrics> metrics = new ArrayList<>(parsedMethods.size());
        for (ParsedMethod parsedMethod : parsedMethods) {
            int cognitiveComplexity = parsedMethod.baseCognitiveComplexity()
                    + (recursiveMethodIds.contains(parsedMethod.id()) ? 1 : 0);
            metrics.add(new MethodMetrics(parsedMethod.methodName(), parsedMethod.className(), cognitiveComplexity));
        }
        metrics.sort(Comparator
                .comparingInt(MethodMetrics::cognitiveComplexity)
                .reversed()
                .thenComparing(MethodMetrics::className)
                .thenComparing(MethodMetrics::methodName));
        return metrics;
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
            return declarationsByKey.getOrDefault(
                    new MethodKey(source.className(), call.methodName(), call.arity()),
                    List.of());
        }
        String ownerName = call.ownerName();
        if (ownerName == null) {
            return List.of();
        }

        Set<String> owners = new LinkedHashSet<>();
        if (classNames.contains(ownerName)) {
            owners.add(ownerName);
        }
        String samePackageOwner = qualifiedNameInPackage(source.packageName(), ownerName);
        if (classNames.contains(samePackageOwner)) {
            owners.add(samePackageOwner);
        }
        if (owners.isEmpty() && !ownerName.contains(".")) {
            Set<String> simpleMatches = classNamesBySimpleName.getOrDefault(ownerName, Set.of());
            if (simpleMatches.size() == 1) {
                owners.addAll(simpleMatches);
            }
        }

        List<ParsedMethod> targets = new ArrayList<>();
        for (String owner : owners) {
            targets.addAll(declarationsByKey.getOrDefault(
                    new MethodKey(owner, call.methodName(), call.arity()),
                    List.of()));
        }
        return targets;
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

    private static String sourceName(Path file) {
        return file.toString().replace('\\', '/');
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
                if (!indexByNode.containsKey(target)) {
                    strongConnect(target);
                    lowLinkByNode.put(node, Math.min(lowLink(node), lowLink(target)));
                } else if (onStack.contains(target)) {
                    lowLinkByNode.put(node, Math.min(lowLink(node), index(target)));
                }
            }

            if (lowLink(node) != index(node)) {
                return;
            }

            List<String> component = new ArrayList<>();
            String member;
            do {
                member = stack.pop();
                onStack.remove(member);
                component.add(member);
            } while (!member.equals(node));

            boolean recursiveComponent = component.size() > 1;
            if (!recursiveComponent) {
                String singleton = component.get(0);
                recursiveComponent = edges.getOrDefault(singleton, Set.of()).contains(singleton);
            }
            if (recursiveComponent) {
                recursiveMembers.addAll(component);
            }
        }

        private int index(String node) {
            return Objects.requireNonNull(indexByNode.get(node));
        }

        private int lowLink(String node) {
            return Objects.requireNonNull(lowLinkByNode.get(node));
        }
    }
}

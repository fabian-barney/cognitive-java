package media.barney.cognitive.core;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.jspecify.annotations.Nullable;

final class JavaMethodParser {

    private JavaMethodParser() {
    }

    static List<MethodDescriptor> parse(String sourceName, String source) {
        List<ParsedMethod> parsedMethods = parseDetailed(sourceName, source);
        Map<String, Integer> complexities = new LinkedHashMap<>();
        for (MethodMetrics metric : CognitiveComplexityAnalyzer.metricsForParsedMethods(parsedMethods)) {
            complexities.put(metric.className() + "#" + metric.methodName(), metric.cognitiveComplexity());
        }

        List<MethodDescriptor> methods = new ArrayList<>(parsedMethods.size());
        for (ParsedMethod parsedMethod : parsedMethods) {
            String key = parsedMethod.className() + "#" + parsedMethod.methodName();
            methods.add(new MethodDescriptor(
                    parsedMethod.methodName(),
                    parsedMethod.startLine(),
                    parsedMethod.endLine(),
                    Objects.requireNonNull(complexities.get(key))));
        }
        return methods;
    }

    static List<ParsedMethod> parseDetailed(String sourceName, String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler is available");
        }

        try {
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            JavacTask task = (JavacTask) compiler.getTask(
                    null,
                    null,
                    diagnostics,
                    List.of("-proc:none"),
                    null,
                    List.of(new SourceFileObject(sourceName, source))
            );
            Iterable<? extends CompilationUnitTree> units = task.parse();
            List<Diagnostic<? extends JavaFileObject>> errors = diagnostics.getDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .toList();
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException(formatDiagnostics(errors));
            }
            return collectMethods(task, units);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    static String sourcePath(String className) {
        String normalized = className.endsWith(".java")
                ? className.substring(0, className.length() - ".java".length())
                : className;
        return normalized.replace('.', '/') + ".java";
    }

    static URI sourceUri(String className) {
        return URI.create("string:///" + sourcePath(className));
    }

    private static List<ParsedMethod> collectMethods(JavacTask task,
                                                     Iterable<? extends CompilationUnitTree> units) {
        Trees trees = Trees.instance(task);
        List<ParsedMethod> methods = new ArrayList<>();
        for (CompilationUnitTree unit : units) {
            String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            SourcePositions positions = trees.getSourcePositions();
            new MethodScanner(unit, packageName, positions, methods).scan(unit, null);
        }
        return methods;
    }

    private static final class MethodScanner extends TreePathScanner<Void, Void> {
        private final CompilationUnitTree unit;
        private final String packageName;
        private final SourcePositions positions;
        private final List<ParsedMethod> methods;
        private final Deque<String> classNames = new ArrayDeque<>();

        private MethodScanner(CompilationUnitTree unit,
                              String packageName,
                              SourcePositions positions,
                              List<ParsedMethod> methods) {
            this.unit = unit;
            this.packageName = packageName;
            this.positions = positions;
            this.methods = methods;
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            String simpleName = node.getSimpleName().toString();
            if (simpleName.isEmpty()) {
                return null;
            }
            classNames.addLast(simpleName);
            try {
                return super.visitClass(node, null);
            } finally {
                classNames.removeLast();
            }
        }

        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            if (node.getBody() == null || node.getReturnType() == null) {
                return null;
            }

            long start = positions.getStartPosition(unit, node);
            long bodyEndExclusive = positions.getEndPosition(unit, node.getBody());
            MethodAnalysis analysis = MethodAnalysisScanner.analyze(node, currentClassName());
            methods.add(new ParsedMethod(
                    packageName,
                    currentClassName(),
                    node.getName().toString(),
                    node.getParameters().size(),
                    lineNumber(start),
                    lineNumber(Math.max(start, bodyEndExclusive - 1)),
                    analysis.cognitiveComplexity(),
                    analysis.calls()));
            return null;
        }

        private String currentClassName() {
            String nestedClassName = String.join(".", classNames);
            return packageName.isEmpty() ? nestedClassName : packageName + "." + nestedClassName;
        }

        private int lineNumber(long position) {
            return (int) unit.getLineMap().getLineNumber(position);
        }
    }

    private record MethodAnalysis(int cognitiveComplexity, List<MethodCall> calls) {
    }

    private static final class MethodAnalysisScanner extends TreeScanner<Void, Integer> {
        private final String ownerClassName;
        private final List<MethodCall> calls = new ArrayList<>();
        private int cognitiveComplexity;

        private MethodAnalysisScanner(String ownerClassName) {
            this.ownerClassName = ownerClassName;
        }

        static MethodAnalysis analyze(MethodTree method, String ownerClassName) {
            MethodAnalysisScanner scanner = new MethodAnalysisScanner(ownerClassName);
            scanner.scan(method.getBody(), 0);
            return new MethodAnalysis(scanner.cognitiveComplexity, List.copyOf(scanner.calls));
        }

        @Override
        public Void visitClass(ClassTree node, Integer nesting) {
            return null;
        }

        @Override
        public Void visitIf(IfTree node, Integer nesting) {
            incrementStructural(nesting);
            scan(node.getCondition(), nesting);
            scan(node.getThenStatement(), nesting + 1);
            scanElse(node.getElseStatement(), nesting);
            return null;
        }

        @Override
        public Void visitForLoop(ForLoopTree node, Integer nesting) {
            incrementStructural(nesting);
            scan(node.getInitializer(), nesting);
            scan(node.getCondition(), nesting);
            scan(node.getUpdate(), nesting);
            scan(node.getStatement(), nesting + 1);
            return null;
        }

        @Override
        public Void visitEnhancedForLoop(EnhancedForLoopTree node, Integer nesting) {
            incrementStructural(nesting);
            scan(node.getExpression(), nesting);
            scan(node.getStatement(), nesting + 1);
            return null;
        }

        @Override
        public Void visitWhileLoop(WhileLoopTree node, Integer nesting) {
            incrementStructural(nesting);
            scan(node.getCondition(), nesting);
            scan(node.getStatement(), nesting + 1);
            return null;
        }

        @Override
        public Void visitDoWhileLoop(DoWhileLoopTree node, Integer nesting) {
            incrementStructural(nesting);
            scan(node.getStatement(), nesting + 1);
            scan(node.getCondition(), nesting);
            return null;
        }

        @Override
        public Void visitCatch(CatchTree node, Integer nesting) {
            incrementStructural(nesting);
            scan(node.getBlock(), nesting + 1);
            return null;
        }

        @Override
        public Void visitSwitch(SwitchTree node, Integer nesting) {
            incrementStructural(nesting);
            scan(node.getExpression(), nesting);
            scanCases(node.getCases(), nesting + 1);
            return null;
        }

        @Override
        public Void visitSwitchExpression(SwitchExpressionTree node, Integer nesting) {
            incrementStructural(nesting);
            scan(node.getExpression(), nesting);
            scanCases(node.getCases(), nesting + 1);
            return null;
        }

        @Override
        public Void visitConditionalExpression(ConditionalExpressionTree node, Integer nesting) {
            incrementStructural(nesting);
            scan(node.getCondition(), nesting);
            scan(node.getTrueExpression(), nesting + 1);
            scan(node.getFalseExpression(), nesting + 1);
            return null;
        }

        @Override
        public Void visitLambdaExpression(LambdaExpressionTree node, Integer nesting) {
            scan(node.getBody(), nesting + 1);
            return null;
        }

        @Override
        public Void visitBreak(BreakTree node, Integer nesting) {
            if (node.getLabel() != null) {
                incrementFundamental();
            }
            return null;
        }

        @Override
        public Void visitContinue(ContinueTree node, Integer nesting) {
            if (node.getLabel() != null) {
                incrementFundamental();
            }
            return null;
        }

        @Override
        public Void visitBinary(BinaryTree node, Integer nesting) {
            if (isLogicalOperator(node.getKind())) {
                scanLogicalExpression(node, nesting, LogicalSequence.NONE);
                return null;
            }
            return super.visitBinary(node, nesting);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Integer nesting) {
            MethodCall methodCall = methodCall(node);
            if (methodCall != null) {
                calls.add(methodCall);
            }

            if (node.getMethodSelect() instanceof MemberSelectTree memberSelect) {
                scan(memberSelect.getExpression(), nesting);
            }
            scan(node.getTypeArguments(), nesting);
            scan(node.getArguments(), nesting);
            return null;
        }

        private void scanElse(@Nullable StatementTree elseStatement, int nesting) {
            if (elseStatement == null) {
                return;
            }
            if (elseStatement instanceof IfTree elseIf) {
                incrementHybrid();
                scan(elseIf.getCondition(), nesting);
                scan(elseIf.getThenStatement(), nesting + 1);
                scanElse(elseIf.getElseStatement(), nesting);
                return;
            }
            incrementHybrid();
            scan(elseStatement, nesting + 1);
        }

        private void scanCases(List<? extends CaseTree> cases, int nesting) {
            for (CaseTree caseTree : cases) {
                if (caseTree.getStatements() != null) {
                    scan(caseTree.getStatements(), nesting);
                }
                Tree body = caseTree.getBody();
                if (body != null) {
                    scan(body, nesting);
                }
            }
        }

        private void scanLogicalExpression(Tree tree, int nesting, LogicalSequence currentOperator) {
            if (tree instanceof ParenthesizedTree parenthesizedTree) {
                scanLogicalExpression(parenthesizedTree.getExpression(), nesting, currentOperator);
                return;
            }
            if (tree instanceof UnaryTree unaryTree && unaryTree.getKind() == Tree.Kind.LOGICAL_COMPLEMENT) {
                scanLogicalExpression(unaryTree.getExpression(), nesting, LogicalSequence.NONE);
                return;
            }
            if (tree instanceof BinaryTree binaryTree && isLogicalOperator(binaryTree.getKind())) {
                LogicalSequence operator = logicalSequence(binaryTree.getKind());
                if (currentOperator != operator) {
                    incrementFundamental();
                }
                scanLogicalExpression(binaryTree.getLeftOperand(), nesting, operator);
                scanLogicalExpression(binaryTree.getRightOperand(), nesting, operator);
                return;
            }
            scan(tree, nesting);
        }

        private @Nullable MethodCall methodCall(MethodInvocationTree node) {
            if (node.getMethodSelect() instanceof IdentifierTree identifier) {
                return new MethodCall(ownerClassName, true, identifier.getName().toString(), node.getArguments().size());
            }
            if (node.getMethodSelect() instanceof MemberSelectTree memberSelect) {
                String ownerName = expressionName(memberSelect.getExpression());
                String methodName = memberSelect.getIdentifier().toString();
                if ("this".equals(ownerName) || "super".equals(ownerName)) {
                    return new MethodCall(ownerClassName, true, methodName, node.getArguments().size());
                }
                return new MethodCall(ownerName, false, methodName, node.getArguments().size());
            }
            return null;
        }

        private static @Nullable String expressionName(ExpressionTree expression) {
            if (expression instanceof IdentifierTree identifier) {
                return identifier.getName().toString();
            }
            if (expression instanceof MemberSelectTree memberSelect) {
                String prefix = expressionName(memberSelect.getExpression());
                if (prefix == null) {
                    return null;
                }
                return prefix + "." + memberSelect.getIdentifier();
            }
            return null;
        }

        private static boolean isLogicalOperator(Tree.Kind kind) {
            return kind == Tree.Kind.CONDITIONAL_AND || kind == Tree.Kind.CONDITIONAL_OR;
        }

        private static LogicalSequence logicalSequence(Tree.Kind kind) {
            return kind == Tree.Kind.CONDITIONAL_AND ? LogicalSequence.AND : LogicalSequence.OR;
        }

        private void incrementStructural(int nesting) {
            cognitiveComplexity += 1 + nesting;
        }

        private void incrementHybrid() {
            cognitiveComplexity++;
        }

        private void incrementFundamental() {
            cognitiveComplexity++;
        }
    }

    private enum LogicalSequence {
        NONE,
        AND,
        OR
    }

    private static final class SourceFileObject extends SimpleJavaFileObject {
        private final String source;

        private SourceFileObject(String sourceName, String source) {
            super(sourceUriForSourceName(sourceName), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }

    }

    private static String formatDiagnostics(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        StringBuilder builder = new StringBuilder("Failed to parse Java source:");
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            builder.append(System.lineSeparator())
                    .append(formatDiagnostic(diagnostic));
        }
        return builder.toString();
    }

    private static String formatDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        String sourceName = diagnostic.getSource() == null ? "<unknown>" : diagnostic.getSource().getName();
        return "%s:%d:%d: %s".formatted(
                sourceName,
                diagnostic.getLineNumber(),
                diagnostic.getColumnNumber(),
                diagnostic.getMessage(Locale.ROOT));
    }

    private static URI sourceUriForSourceName(String sourceName) {
        if (looksLikePath(sourceName)) {
            return pathLikeSourceUri(sourceName);
        }
        return sourceUri(sourceName);
    }

    private static boolean looksLikePath(String sourceName) {
        return sourceName.contains("/")
                || sourceName.contains("\\")
                || sourceName.contains(":");
    }

    private static URI pathLikeSourceUri(String sourceName) {
        String normalized = sourceName.replace('\\', '/');
        String path = normalized.startsWith("/") ? normalized : "/" + normalized;
        try {
            return new URI("string", null, path, null);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid source name: " + sourceName, ex);
        }
    }
}

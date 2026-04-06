package media.barney.cognitive.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import org.junit.jupiter.api.Test;

class CognitiveComplexityPdfExamplesTest {

    @Test
    void getWordsMatchesThePaper() {
        assertMethodComplexity("""
                class Sample {
                    String getWords(int number) {
                        switch (number) {
                            case 1:
                                return "one";
                            case 2:
                                return "a couple";
                            case 3:
                                return "a few";
                            default:
                                return "lots";
                        }
                    }
                }
                """, "getWords", 1);
    }

    @Test
    void sumOfPrimesMatchesThePaper() {
        assertMethodComplexity("""
                class Sample {
                    int sumOfPrimes(int max) {
                        int total = 0;
                        OUT: for (int i = 1; i <= max; ++i) {
                            for (int j = 2; j < i; ++j) {
                                if (i % j == 0) {
                                    continue OUT;
                                }
                            }
                            total += i;
                        }
                        return total;
                    }
                }
                """, "sumOfPrimes", 7);
    }

    @Test
    void mixedLogicalOperatorsExampleMatchesThePaper() {
        assertMethodComplexity("""
                class Sample {
                    int logical(boolean a, boolean b, boolean c, boolean d, boolean e, boolean f) {
                        if (a && b && c || d || e && f) {
                            return 1;
                        }
                        return 0;
                    }
                }
                """, "logical", 4);
    }

    @Test
    void negatedLogicalOperatorsExampleMatchesThePaper() {
        assertMethodComplexity("""
                class Sample {
                    int logical(boolean a, boolean b, boolean c) {
                        if (a && !(b && c)) {
                            return 1;
                        }
                        return 0;
                    }
                }
                """, "logical", 3);
    }

    @Test
    void myMethodMatchesThePaper() {
        assertMethodComplexity("""
                class Sample {
                    void myMethod(boolean condition1, boolean condition2) {
                        try {
                            if (condition1) {
                                for (int i = 0; i < 10; i++) {
                                    while (condition2) {
                                    }
                                }
                            }
                        } catch (ExcepType1 | ExcepType2 e) {
                            if (condition2) {
                            }
                        }
                    }
                }
                """, "myMethod", 9);
    }

    @Test
    void myMethod2MatchesThePaper() {
        assertMethodComplexity("""
                class Sample {
                    void myMethod2(boolean condition1) {
                        Runnable r = () -> {
                            if (condition1) {
                            }
                        };
                    }
                }
                """, "myMethod2", 2);
    }

    @Test
    void overriddenSymbolFromMatchesThePaper() {
        assertMethodComplexity("""
                class Sample {
                    @Nullable
                    private MethodJavaSymbol overriddenSymbolFrom(ClassJavaType classType) {
                        if (classType.isUnknown()) {
                            return Symbols.unknownMethodSymbol;
                        }
                        boolean unknownFound = false;
                        List<JavaSymbol> symbols = classType.getSymbol().members().lookup(name);
                        for (JavaSymbol overrideSymbol : symbols) {
                            if (overrideSymbol.isKind(JavaSymbol.MTH) && !overrideSymbol.isStatic()) {
                                MethodJavaSymbol methodJavaSymbol = (MethodJavaSymbol) overrideSymbol;
                                if (canOverride(methodJavaSymbol)) {
                                    Boolean overriding = checkOverridingParameters(methodJavaSymbol, classType);
                                    if (overriding == null) {
                                        if (!unknownFound) {
                                            unknownFound = true;
                                        }
                                    } else if (overriding) {
                                        return methodJavaSymbol;
                                    }
                                }
                            }
                        }
                        if (unknownFound) {
                            return Symbols.unknownMethodSymbol;
                        }
                        return null;
                    }
                }
                """, "overriddenSymbolFrom", 19);
    }

    @Test
    void addVersionMatchesThePaper() {
        assertMethodComplexity("""
                class Sample {
                    private void addVersion(final Entry entry, final Transaction txn)
                            throws PersistitInterruptedException, RollbackException {
                        final TransactionIndex ti = _persistit.getTransactionIndex();
                        while (true) {
                            try {
                                synchronized (this) {
                                    if (frst != null) {
                                        if (frst.getVersion() > entry.getVersion()) {
                                            throw new RollbackException();
                                        }
                                        if (txn.isActive()) {
                                            for (Entry e = frst; e != null; e = e.getPrevious()) {
                                                final long version = e.getVersion();
                                                final long depends = ti.wwDependency(version, txn.getTransactionStatus(), 0);
                                                if (depends == TIMED_OUT) {
                                                    throw new WWRetryException(version);
                                                }
                                                if (depends != 0 && depends != ABORTED) {
                                                    throw new RollbackException();
                                                }
                                            }
                                        }
                                    }
                                    entry.setPrevious(frst);
                                    frst = entry;
                                    break;
                                }
                            } catch (final WWRetryException re) {
                                try {
                                    final long depends = _persistit.getTransactionIndex()
                                            .wwDependency(re.getVersionHandle(), txn.getTransactionStatus(),
                                                    SharedResource.DEFAULT_MAX_WAIT_TIME);
                                    if (depends != 0 && depends != ABORTED) {
                                        throw new RollbackException();
                                    }
                                } catch (final InterruptedException ie) {
                                    throw new PersistitInterruptedException(ie);
                                }
                            } catch (final InterruptedException ie) {
                                throw new PersistitInterruptedException(ie);
                            }
                        }
                    }
                }
                """, "addVersion", 35);
    }

    @Test
    void toRegexpMatchesThePaper() {
        assertMethodComplexity("""
                class Sample {
                    private static final String SPECIAL_CHARS = "[]";

                    private static String toRegexp(String antPattern, String directorySeparator) {
                        final String escapedDirectorySeparator = '\\\\' + directorySeparator;
                        final StringBuilder sb = new StringBuilder(antPattern.length());
                        sb.append('^');
                        int i = antPattern.startsWith("/") || antPattern.startsWith("\\\\") ? 1 : 0;
                        while (i < antPattern.length()) {
                            final char ch = antPattern.charAt(i);
                            if (SPECIAL_CHARS.indexOf(ch) != -1) {
                                sb.append('\\\\').append(ch);
                            } else if (ch == '*') {
                                if (i + 1 < antPattern.length() && antPattern.charAt(i + 1) == '*') {
                                    if (i + 2 < antPattern.length() && isSlash(antPattern.charAt(i + 2))) {
                                        sb.append("(?:.*").append(escapedDirectorySeparator).append("|)");
                                        i += 2;
                                    } else {
                                        sb.append(".*");
                                        i += 1;
                                    }
                                } else {
                                    sb.append("[^").append(escapedDirectorySeparator).append("]*?");
                                }
                            } else if (ch == '?') {
                                sb.append("[^").append(escapedDirectorySeparator).append("]");
                            } else if (isSlash(ch)) {
                                sb.append(escapedDirectorySeparator);
                            } else {
                                sb.append(ch);
                            }
                            i++;
                        }
                        sb.append('$');
                        return sb.toString();
                    }
                }
                """, "toRegexp", 20);
    }

    private static void assertMethodComplexity(String source, String methodName, int expected) {
        List<MethodDescriptor> descriptors = JavaMethodParser.parse("Sample.java", source);
        for (MethodDescriptor descriptor : descriptors) {
            if (descriptor.name().equals(methodName)) {
                assertEquals(expected, descriptor.cognitiveComplexity(), methodName);
                return;
            }
        }
        fail("Method not found: " + methodName);
    }
}

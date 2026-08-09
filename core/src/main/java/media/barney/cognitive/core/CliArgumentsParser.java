package media.barney.cognitive.core;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class CliArgumentsParser {

    private static final List<ValuedOptionParser> VALUED_OPTION_PARSERS = List.of(
            CliArgumentsParser::parseReportFormatOption,
            CliArgumentsParser::parseOutputOption,
            CliArgumentsParser::parseJunitReportOption,
            CliArgumentsParser::parseThresholdOption,
            CliArgumentsParser::parseExclusionOption
    );

    private CliArgumentsParser() {
    }

    static CliArguments parse(String[] args) {
        ParseState state = parseState(args);
        if (state.help) {
            return arguments(CliMode.HELP, state, List.of());
        }
        ensureChangedIsNotCombined(state.changed, state.fileArgs);
        if (state.changed) {
            return arguments(CliMode.CHANGED_SRC, state, List.of());
        }
        if (state.fileArgs.isEmpty()) {
            return arguments(CliMode.ALL_SRC, state, List.of());
        }
        return arguments(CliMode.EXPLICIT_FILES, state, List.copyOf(state.fileArgs));
    }

    private static CliArguments arguments(CliMode mode, ParseState state, List<String> fileArgs) {
        return new CliArguments(
                mode,
                state.reportFormat,
                state.threshold,
                state.agent,
                state.failuresOnly,
                state.omitRedundancy,
                state.outputPath,
                state.junitReportPath,
                fileArgs,
                List.copyOf(state.sourceRoots),
                state.exclusionOptions
        );
    }

    private static ParseState parseState(String[] args) {
        ParseStateBuilder state = new ParseStateBuilder();
        for (int index = 0; index < args.length; index++) {
            index = parseArg(args, index, state);
        }
        return state.build();
    }

    private static int parseArg(String[] args, int index, ParseStateBuilder state) {
        String arg = args[index];
        if (!arg.startsWith("--")) {
            state.values.add(arg);
            return index;
        }
        return parseOption(args, index, state, arg);
    }

    private static int parseOption(String[] args, int index, ParseStateBuilder state, String arg) {
        if (parseExactOption(state, arg)) {
            return index;
        }
        if (isBooleanOption(arg, "--failures-only")) {
            state.failuresOnly = parseBooleanOption(arg, "--failures-only", state.failuresOnlySeen);
            state.failuresOnlySeen = true;
            return index;
        }
        if (isBooleanOption(arg, "--omit-redundancy")) {
            state.omitRedundancy = parseBooleanOption(arg, "--omit-redundancy", state.omitRedundancySeen);
            state.omitRedundancySeen = true;
            return index;
        }
        if (isBooleanOption(arg, "--use-default-exclusions")) {
            state.useDefaultExclusions = parseBooleanOption(
                    arg,
                    "--use-default-exclusions",
                    state.useDefaultExclusionsSeen);
            state.useDefaultExclusionsSeen = true;
            return index;
        }
        return parseValuedOption(args, index, state, arg);
    }

    private static boolean parseExactOption(ParseStateBuilder state, String arg) {
        return switch (arg) {
            case "--help" -> setFlag(state.helpSeen, "--help", () -> {
                state.help = true;
                state.helpSeen = true;
            });
            case "--changed" -> setFlag(state.changedSeen, "--changed", () -> {
                state.changed = true;
                state.changedSeen = true;
            });
            case "--agent" -> setFlag(state.agentSeen, "--agent", () -> {
                state.agent = true;
                state.agentSeen = true;
            });
            default -> false;
        };
    }

    private static boolean setFlag(boolean seen, String option, Runnable setter) {
        if (seen) {
            throw new IllegalArgumentException(option + " can only be provided once");
        }
        setter.run();
        return true;
    }

    private static int parseValuedOption(String[] args, int index, ParseStateBuilder state, String arg) {
        AssignedOption option = AssignedOption.parse(arg);
        for (ValuedOptionParser parser : VALUED_OPTION_PARSERS) {
            if (parser.parse(args, index, state, option)) {
                return option.hasInlineValue() ? index : index + 1;
            }
        }
        throw new IllegalArgumentException("Unknown option: " + arg);
    }

    private static boolean parseReportFormatOption(String[] args,
                                                   int index,
                                                   ParseStateBuilder state,
                                                   AssignedOption option) {
        if ("--format".equals(option.name())) {
            if (state.reportFormatSeen) {
                throw new IllegalArgumentException("--format can only be provided once");
            }
            state.reportFormat = ReportFormat.parse(optionValue(args, index, option, "one of: toon, json, text, junit, none"));
            state.reportFormatSeen = true;
            return true;
        }
        return false;
    }

    private static boolean parseExclusionOption(String[] args,
                                                int index,
                                                ParseStateBuilder state,
                                                AssignedOption option) {
        if ("--exclude".equals(option.name())) {
            state.excludes.add(parseListOption(args, index, option, "a glob"));
            return true;
        }
        if ("--source-root".equals(option.name())) {
            state.sourceRoots.add(parseListOption(args, index, option, "a path"));
            return true;
        }
        if ("--exclude-class".equals(option.name())) {
            state.excludeClasses.add(parseListOption(args, index, option, "a regex"));
            return true;
        }
        if ("--exclude-annotation".equals(option.name())) {
            state.excludeAnnotations.add(parseListOption(args, index, option, "an annotation name"));
            return true;
        }
        return false;
    }

    private static boolean parseOutputOption(String[] args,
                                             int index,
                                             ParseStateBuilder state,
                                             AssignedOption option) {
        if ("--output".equals(option.name())) {
            state.outputPath = parsePathOption(args, index, option, state.outputPathSeen);
            state.outputPathSeen = true;
            return true;
        }
        return false;
    }

    private static boolean parseJunitReportOption(String[] args,
                                                  int index,
                                                  ParseStateBuilder state,
                                                  AssignedOption option) {
        if ("--junit-report".equals(option.name())) {
            state.junitReportPath = parsePathOption(args, index, option, state.junitReportPathSeen);
            state.junitReportPathSeen = true;
            return true;
        }
        return false;
    }

    private static boolean parseThresholdOption(String[] args,
                                                int index,
                                                ParseStateBuilder state,
                                                AssignedOption option) {
        if ("--threshold".equals(option.name())) {
            if (state.thresholdSeen) {
                throw new IllegalArgumentException("--threshold can only be provided once");
            }
            try {
                state.threshold = Thresholds.parse(optionValue(args, index, option, "a positive integer"));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("--threshold requires a positive integer", ex);
            }
            state.thresholdSeen = true;
            return true;
        }
        return false;
    }

    private static String parsePathOption(String[] args,
                                          int index,
                                          AssignedOption assignedOption,
                                          boolean seen) {
        if (seen) {
            throw new IllegalArgumentException(assignedOption.name() + " can only be provided once");
        }
        return optionValue(args, index, assignedOption, "a path");
    }

    private static String parseListOption(String[] args,
                                          int index,
                                          AssignedOption option,
                                          String valueDescription) {
        return optionValue(args, index, option, valueDescription).trim();
    }

    private static boolean isBooleanOption(String arg, String option) {
        return arg.equals(option) || arg.startsWith(option + "=");
    }

    private static boolean parseBooleanOption(String arg, String option, boolean seen) {
        if (seen) {
            throw new IllegalArgumentException(option + " can only be provided once");
        }
        if (arg.equals(option)) {
            return true;
        }
        String value = arg.substring(option.length() + 1);
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException(option + " requires true or false when assigned");
    }

    private static String optionValue(String[] args,
                                      int index,
                                      AssignedOption option,
                                      String valueDescription) {
        @Nullable String inlineValue = option.inlineValue();
        if (inlineValue != null) {
            if (inlineValue.isEmpty()) {
                throw new IllegalArgumentException(option.name() + " requires " + valueDescription);
            }
            return inlineValue;
        }
        if (index + 1 >= args.length) {
            throw new IllegalArgumentException(option.name() + " requires " + valueDescription);
        }
        String nextValue = args[index + 1];
        if (nextValue.startsWith("--")) {
            throw new IllegalArgumentException(option.name() + " requires " + valueDescription);
        }
        return nextValue;
    }

    private static void ensureChangedIsNotCombined(boolean changed, List<String> values) {
        if (changed && !values.isEmpty()) {
            throw new IllegalArgumentException("--changed cannot be combined with file arguments");
        }
    }

    private record ParseState(boolean help,
                              boolean changed,
                              ReportFormat reportFormat,
                              int threshold,
                              boolean agent,
                              boolean failuresOnly,
                              boolean omitRedundancy,
                              @Nullable String outputPath,
                              @Nullable String junitReportPath,
                              List<String> sourceRoots,
                              SourceExclusionOptions exclusionOptions,
                              List<String> fileArgs) {
    }

    @FunctionalInterface
    private interface ValuedOptionParser {
        boolean parse(String[] args, int index, ParseStateBuilder state, AssignedOption option);
    }

    private static final class ParseStateBuilder {
        private boolean help;
        private boolean helpSeen;
        private boolean changed;
        private boolean changedSeen;
        private ReportFormat reportFormat = ReportFormat.TOON;
        private boolean reportFormatSeen;
        private int threshold = Thresholds.DEFAULT;
        private boolean thresholdSeen;
        private boolean agent;
        private boolean agentSeen;
        private boolean failuresOnly;
        private boolean failuresOnlySeen;
        private boolean omitRedundancy;
        private boolean omitRedundancySeen;
        private boolean useDefaultExclusions = true;
        private boolean useDefaultExclusionsSeen;
        private final List<String> excludes = new ArrayList<>();
        private final List<String> sourceRoots = new ArrayList<>();
        private final List<String> excludeClasses = new ArrayList<>();
        private final List<String> excludeAnnotations = new ArrayList<>();
        private @Nullable String outputPath;
        private boolean outputPathSeen;
        private @Nullable String junitReportPath;
        private boolean junitReportPathSeen;
        private final List<String> values = new ArrayList<>();

        private ParseState build() {
            boolean effectiveFailuresOnly = (agent && !failuresOnlySeen) || failuresOnly;
            boolean effectiveOmitRedundancy = (agent && !omitRedundancySeen) || omitRedundancy;
            return new ParseState(
                    help,
                    changed,
                    reportFormat,
                    threshold,
                    agent,
                    effectiveFailuresOnly,
                    effectiveOmitRedundancy,
                    outputPath,
                    junitReportPath,
                    List.copyOf(sourceRoots),
                    new SourceExclusionOptions(excludes, excludeClasses, excludeAnnotations, useDefaultExclusions),
                    List.copyOf(values)
            );
        }
    }

    private record AssignedOption(String name, @Nullable String inlineValue) {
        private static AssignedOption parse(String arg) {
            int equalsIndex = arg.indexOf('=');
            if (equalsIndex < 0) {
                return new AssignedOption(arg, null);
            }
            return new AssignedOption(arg.substring(0, equalsIndex), arg.substring(equalsIndex + 1));
        }

        private boolean hasInlineValue() {
            return inlineValue != null;
        }
    }
}

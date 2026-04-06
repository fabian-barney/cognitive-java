package media.barney.cognitive.core;

import java.util.ArrayList;
import java.util.List;

final class CliArgumentsParser {

    private CliArgumentsParser() {
    }

    static CliArguments parse(String[] args) {
        ParseState state = parseState(args);
        if (state.help) {
            return new CliArguments(CliMode.HELP, List.of());
        }
        ensureChangedIsNotCombined(state.changed, state.fileArgs);
        if (state.changed) {
            return new CliArguments(CliMode.CHANGED_SRC, List.of());
        }
        if (state.fileArgs.isEmpty()) {
            return new CliArguments(CliMode.ALL_SRC, List.of());
        }
        return new CliArguments(CliMode.EXPLICIT_FILES, List.copyOf(state.fileArgs));
    }

    private static ParseState parseState(String[] args) {
        boolean help = false;
        boolean changed = false;
        List<String> values = new ArrayList<>();
        for (String arg : args) {
            switch (arg) {
                case "--help" -> help = true;
                case "--changed" -> changed = true;
                default -> {
                    if (arg.startsWith("--")) {
                        throw new IllegalArgumentException("Unknown option: " + arg);
                    }
                    values.add(arg);
                }
            }
        }
        return new ParseState(help, changed, values);
    }

    private static void ensureChangedIsNotCombined(boolean changed, List<String> values) {
        if (changed && !values.isEmpty()) {
            throw new IllegalArgumentException("--changed cannot be combined with file arguments");
        }
    }

    private record ParseState(boolean help, boolean changed, List<String> fileArgs) {
    }
}

package media.barney.cognitive.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class ReportFormatter {

    private ReportFormatter() {
    }

    static String format(List<MethodMetrics> entries) {
        List<MethodMetrics> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator
                .comparingInt(MethodMetrics::cognitiveComplexity)
                .reversed()
                .thenComparing(MethodMetrics::className)
                .thenComparing(MethodMetrics::methodName));

        String title = "Cognitive Complexity Report";
        String header = String.format("%-30s %-35s %4s", "Method", "Class", "CogC");
        String separator = "-".repeat(header.length());
        StringBuilder builder = new StringBuilder();
        builder.append(title).append('\n');
        builder.append("=".repeat(title.length())).append('\n');
        builder.append(header).append('\n');
        builder.append(separator).append('\n');

        for (MethodMetrics entry : sorted) {
            builder.append(String.format(Locale.ROOT, "%-30s %-35s %4d",
                    entry.methodName(),
                    entry.className(),
                    entry.cognitiveComplexity()));
            builder.append('\n');
        }

        return builder.toString();
    }
}

package media.barney.cognitive.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReportFormatterTest {

    @Test
    void formatsExactReportWithCognitiveComplexityScores() {
        MethodMetrics higher = new MethodMetrics("foo", "demo.Sample", 5);
        MethodMetrics lower = new MethodMetrics("bar", "demo.Sample", 2);

        String report = ReportFormatter.format(List.of(higher, lower));

        String title = "Cognitive Complexity Report";
        String header = String.format("%-30s %-35s %4s", "Method", "Class", "CogC");
        String separator = "-".repeat(header.length());
        String expected = """
                %s
                %s
                %s
                %s
                %-30s %-35s %4d
                %-30s %-35s %4d
                """.formatted(
                title,
                "=".repeat(title.length()),
                header,
                separator,
                "foo",
                "demo.Sample",
                5,
                "bar",
                "demo.Sample",
                2);

        assertEquals(expected, report);
    }

    @Test
    void sortsHigherScoresFirst() {
        String report = ReportFormatter.format(List.of(
                new MethodMetrics("low", "demo.Sample", 2),
                new MethodMetrics("high", "demo.Sample", 9)
        ));

        assertTrue(report.indexOf("high") < report.indexOf("low"));
    }
}

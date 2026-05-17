package media.barney.cognitive.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReportFormatterTest {

    @Test
    void formatsTextReportWithStatusThresholdAndSourcePaths() {
        String report = ReportFormatter.format(report(), ReportFormat.TEXT);

        assertTrue(report.contains("Cognitive Complexity Report"));
        assertTrue(report.contains("Status: failed"));
        assertTrue(report.contains("Threshold: 15"));
        assertTrue(report.contains("failed"));
        assertTrue(report.contains("src/main/java/demo/High.java"));
        assertTrue(report.contains("src/main/java/demo/Low.java"));
        assertTrue(report.indexOf("high") < report.indexOf("low"));
    }

    @Test
    void formatsJsonReportWithCompactMethodModel() {
        String report = ReportFormatter.format(report(), ReportFormat.JSON);

        assertTrue(report.contains("\"status\": \"failed\""));
        assertTrue(report.contains("\"threshold\": 15"));
        assertTrue(report.contains("\"cc\": 16"));
        assertTrue(report.contains("\"method\": \"high\""));
        assertTrue(report.contains("\"src\": \"src/main/java/demo/High.java\""));
        assertTrue(report.contains("\"lineStart\": 4"));
        assertTrue(report.contains("\"lineEnd\": 8"));
    }

    @Test
    void omitRedundancyRemovesMethodStatusFromPrimaryReport() {
        String report = ReportFormatter.format(report(), ReportFormat.JSON, false, true);

        assertTrue(report.contains("\"status\": \"failed\""));
        assertFalse(report.contains("\"status\": \"passed\""));
    }

    @Test
    void failuresOnlyFiltersPrimaryReport() {
        String report = ReportFormatter.format(report(), ReportFormat.JSON, true, false);

        assertTrue(report.contains("\"method\": \"high\""));
        assertFalse(report.contains("\"method\": \"low\""));
    }

    @Test
    void formatsJunitReportWithFailureDetailsAndElapsedTime() {
        String report = ReportFormatter.format(report(), ReportFormat.JUNIT);

        assertTrue(report.contains("<testsuites"));
        assertTrue(report.contains("tests=\"2\""));
        assertTrue(report.contains("failures=\"1\""));
        assertTrue(report.contains("time=\"0.250000\""));
        assertTrue(report.contains("Cognitive Complexity threshold exceeded: 16 > 15"));
        assertTrue(report.contains("Source: src/main/java/demo/High.java:4-8"));
    }

    @Test
    void noneFormatProducesEmptyReport() {
        assertEquals("", ReportFormatter.format(report(), ReportFormat.NONE));
    }

    private static CognitiveReport report() {
        return CognitiveReport.from(List.of(
                metric("low", "demo.Low", "src/main/java/demo/Low.java", 4, 6, 2),
                metric("high", "demo.High", "src/main/java/demo/High.java", 4, 8, 16)
        ), 15).withElapsedNanos(250_000_000);
    }

    private static MethodMetrics metric(String methodName,
                                        String className,
                                        String sourcePath,
                                        int startLine,
                                        int endLine,
                                        int complexity) {
        return new MethodMetrics(methodName, className, sourcePath, startLine, endLine, complexity);
    }
}

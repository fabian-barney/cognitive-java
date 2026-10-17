package media.barney.cognitive.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import tools.jackson.databind.json.JsonMapper;

class ReportFormatterTest {

    @Test
    void jsonPreservesFieldOrderWhitespaceAndTrailingNewline() {
        CognitiveReport report = CognitiveReport.from(List.of(
                metric("low", "demo.Low", "src/main/java/demo/Low.java", 4, 6, 2)), 8);

        assertEquals("""
                {
                  "status": "passed",
                  "threshold": 8,
                  "methods": [
                    {
                      "status": "passed",
                      "cc": 2,
                      "method": "low",
                      "src": "src/main/java/demo/Low.java",
                      "lineStart": 4,
                      "lineEnd": 6
                    }
                  ]
                }
                """, ReportFormatter.format(report, ReportFormat.JSON, false, false, false));
    }

    @Test
    void jsonRoundTripsUnicodeControlCharactersAndIntegerBoundaries() throws Exception {
        String method = "mÃƒÆ’Ã‚Â©thode\"\\\n\t";
        String source = "src/GrÃƒÆ’Ã‚Â¼ÃƒÆ’Ã…Â¸e\\Example.java";
        CognitiveReport report = CognitiveReport.from(List.of(
                metric(method, "demo.Unicode", source, 1, Integer.MAX_VALUE, Integer.MAX_VALUE)),
                Integer.MAX_VALUE);

        var json = JsonMapper.builder().build().readTree(
                ReportFormatter.format(report, ReportFormat.JSON, false, true, false));

        assertEquals(Integer.MAX_VALUE, json.get("threshold").intValue());
        var entry = json.get("methods").get(0);
        assertEquals(method, entry.get("method").stringValue());
        assertEquals(source, entry.get("src").stringValue());
        assertEquals(Integer.MAX_VALUE, entry.get("cc").intValue());
        assertEquals(Integer.MAX_VALUE, entry.get("lineEnd").intValue());
        assertFalse(entry.has("status"));
        assertFalse(json.has("exclusions"));
    }

    @Test
    void junitRoundTripsEscapingAndKeepsDeclarationAttributeOrderAndTime() throws Exception {
        String method = "mÃƒÆ’Ã‚Â©thode<&\"";
        String source = "src/GrÃƒÆ’Ã‚Â¼ÃƒÆ’Ã…Â¸e<&\".java";
        CognitiveReport report = CognitiveReport.from(List.of(
                metric(method, "demo.Unicode", source, 1, 2, 9)), 8).withElapsedNanos(123_456_789);

        String xml = ReportFormatter.format(report, ReportFormat.JUNIT, false, false, false);
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        var testcase = (org.w3c.dom.Element) document.getElementsByTagName("testcase").item(0);

        assertTrue(xml.startsWith("<?xml version='1.0' encoding='UTF-8'?>"));
        assertTrue(xml.indexOf("tests=\"1\"") < xml.indexOf("failures=\"1\""));
        assertTrue(xml.endsWith("\n"));
        assertEquals("0.123457", document.getDocumentElement().getAttribute("time"));
        assertEquals(source, testcase.getAttribute("file"));
        assertEquals(method + ":1 [CC=9]", testcase.getAttribute("name"));
        assertTrue(document.getElementsByTagName("failure").item(0).getTextContent().contains(method));
    }

    @Test
    void emptyToonReportUsesCanonicalArrayEncoding() {
        CognitiveReport report = CognitiveReport.from(List.of(), 8);

        assertEquals("status: passed\nthreshold: 8\nmethods: []",
                ReportFormatter.format(report, ReportFormat.TOON, false, false, false));
    }

    @Test
    void populatedToonReportPreservesColumnsAndValues() {
        assertEquals("""
                status: failed
                threshold: 8
                methods[2]{status,cc,method,src,lineStart,lineEnd}:
                  failed,16,high,src/main/java/demo/High.java,4,8
                  passed,2,low,src/main/java/demo/Low.java,4,6""",
                ReportFormatter.format(report(), ReportFormat.TOON, false, false, false));
    }

    @Test
    void formatsTextReportWithStatusThresholdAndSourcePaths() {
        String report = ReportFormatter.format(report(), ReportFormat.TEXT);

        assertTrue(report.contains("Cognitive Complexity Report"));
        assertTrue(report.contains("Status: failed"));
        assertTrue(report.contains("Threshold: 8"));
        assertTrue(report.contains("failed"));
        assertTrue(report.contains("src/main/java/demo/High.java"));
        assertTrue(report.contains("src/main/java/demo/Low.java"));
        assertTrue(report.indexOf("high") < report.indexOf("low"));
    }

    @Test
    void formatsJsonReportWithCompactMethodModel() {
        String report = ReportFormatter.format(report(), ReportFormat.JSON);

        assertTrue(report.contains("\"status\": \"failed\""));
        assertTrue(report.contains("\"threshold\": 8"));
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
        assertTrue(report.contains("classname=\"demo.High\""));
        assertTrue(report.contains("file=\"src/main/java/demo/High.java\""));
        assertTrue(report.contains("name=\"high:4 [CC=16]\""));
        assertTrue(report.contains("name=\"low:4 [CC=2]\""));
        assertTrue(report.contains("<system-out>Cognitive Complexity: 16"));
        assertTrue(report.contains("<system-out>Cognitive Complexity: 2"));
        assertTrue(report.contains("Cognitive Complexity threshold exceeded: 16 > 8"));
        assertTrue(report.contains("Source: src/main/java/demo/High.java:4-8"));
        assertTrue(report.contains("<property name=\"status\" value=\"failed\"/>"));
    }

    @Test
    void noneFormatProducesEmptyReport() {
        assertEquals("", ReportFormatter.format(report(), ReportFormat.NONE));
    }

    @Test
    void fullReportsIncludeExclusionAuditWhileCompactPrimaryCanOmitIt() {
        CognitiveReport report = CognitiveReport.from(
                List.of(metric("high", "demo.High", "src/main/java/demo/High.java", 4, 8, 16)),
                15,
                new SourceExclusionAudit(
                        3,
                        1,
                        1,
                        List.of(new SourceExclusionAudit.ExclusionCount("default:path:generated-directory", 1)),
                        List.of(new SourceExclusionAudit.ExclusionCount("default:annotation:Generated", 1))
                )).withElapsedNanos(250_000_000);

        String text = ReportFormatter.format(report, ReportFormat.TEXT, false, false, true);
        String json = ReportFormatter.format(report, ReportFormat.JSON, false, false, true);
        String junit = ReportFormatter.format(report, ReportFormat.JUNIT, false, false, true);
        String compact = ReportFormatter.format(report, ReportFormat.JSON, false, false, false);

        assertTrue(text.contains("Exclusions:"));
        assertTrue(text.contains("Discovered files: 3"));
        assertTrue(json.contains("\"excludedFiles\": 1"));
        assertTrue(json.contains("\"analyzedMethods\": 1"));
        assertTrue(junit.contains("exclusion.excludedFiles"));
        assertTrue(junit.contains("exclusion.excludedClasses"));
        assertFalse(compact.contains("\"exclusions\""));
    }

    private static CognitiveReport report() {
        return CognitiveReport.from(List.of(
                metric("high", "demo.High", "src/main/java/demo/High.java", 4, 8, 16),
                metric("low", "demo.Low", "src/main/java/demo/Low.java", 4, 6, 2)
        ), 8).withElapsedNanos(250_000_000);
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

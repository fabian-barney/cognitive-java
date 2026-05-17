package media.barney.cognitive.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import dev.toonformat.jtoon.JToon;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

final class ReportFormatter {

    private static final ObjectWriter JSON_WRITER = JsonMapper.builder()
            .build()
            .writer(new JsonPrettyPrinter());
    private static final ObjectWriter XML_WRITER = xmlMapper()
            .writerWithDefaultPrettyPrinter();

    private ReportFormatter() {
    }

    static String format(CognitiveReport report, ReportFormat format) {
        return format(report, format, false, false);
    }

    static String format(CognitiveReport report,
                         ReportFormat format,
                         boolean failuresOnly,
                         boolean omitRedundancy) {
        if (format == ReportFormat.NONE) {
            return "";
        }
        CognitiveReport selected = failuresOnly ? failuresOnly(report) : report;
        return switch (format) {
            case TOON -> JToon.encodeJson(formatJson(selected, omitRedundancy));
            case JSON -> formatJson(selected, omitRedundancy);
            case TEXT -> formatText(selected, omitRedundancy);
            case JUNIT -> formatJunit(selected, omitRedundancy);
            case NONE -> "";
        };
    }

    private static String formatText(CognitiveReport report, boolean omitRedundancy) {
        List<CognitiveReport.MethodReport> sorted = sortedMethods(report.methods());
        StringBuilder builder = new StringBuilder();
        builder.append("Cognitive Complexity Report\n");
        builder.append("===========================\n");
        builder.append("Status: ").append(report.status()).append('\n');
        builder.append("Threshold: ").append(report.threshold()).append('\n');
        appendMethodTable(builder, omitRedundancy ? methodTextColumns() : fullTextColumns(), sorted);
        return builder.toString();
    }

    private static List<TableColumn> fullTextColumns() {
        List<TableColumn> columns = new ArrayList<>();
        columns.add(new TableColumn("Status", Alignment.LEFT, method -> method.status().value()));
        columns.addAll(methodTextColumns());
        return columns;
    }

    private static List<TableColumn> methodTextColumns() {
        return List.of(
                new TableColumn("Method", Alignment.LEFT, CognitiveReport.MethodReport::methodName),
                new TableColumn("Src", Alignment.LEFT, CognitiveReport.MethodReport::sourcePath),
                new TableColumn("CC", Alignment.RIGHT, method -> Integer.toString(method.complexity()))
        );
    }

    private static void appendMethodTable(StringBuilder builder,
                                          List<TableColumn> columns,
                                          List<CognitiveReport.MethodReport> methods) {
        List<List<String>> rows = methods.stream()
                .map(method -> row(columns, method))
                .toList();
        List<Integer> widths = columnWidths(columns, rows);
        appendTableRow(builder, columns, columnHeaders(columns), widths);
        appendSeparator(builder, widths);
        for (List<String> row : rows) {
            appendTableRow(builder, columns, row, widths);
        }
    }

    private static List<String> row(List<TableColumn> columns, CognitiveReport.MethodReport method) {
        List<String> values = new ArrayList<>();
        for (TableColumn column : columns) {
            values.add(column.value(method));
        }
        return values;
    }

    private static List<String> columnHeaders(List<TableColumn> columns) {
        return columns.stream()
                .map(TableColumn::header)
                .toList();
    }

    private static List<Integer> columnWidths(List<TableColumn> columns, List<List<String>> rows) {
        List<Integer> widths = columns.stream()
                .map(column -> column.header().length())
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        for (List<String> row : rows) {
            for (int index = 0; index < row.size(); index++) {
                widths.set(index, Math.max(widths.get(index), row.get(index).length()));
            }
        }
        return widths;
    }

    private static void appendSeparator(StringBuilder builder, List<Integer> widths) {
        for (int index = 0; index < widths.size(); index++) {
            if (index > 0) {
                builder.append("  ");
            }
            builder.append("-".repeat(widths.get(index)));
        }
        builder.append('\n');
    }

    private static void appendTableRow(StringBuilder builder,
                                       List<TableColumn> columns,
                                       List<String> row,
                                       List<Integer> widths) {
        for (int index = 0; index < row.size(); index++) {
            if (index > 0) {
                builder.append("  ");
            }
            builder.append(columns.get(index).align(row.get(index), widths.get(index)));
        }
        builder.append('\n');
    }

    private static String formatJson(CognitiveReport report, boolean omitRedundancy) {
        return writeJson(jsonReport(report, omitRedundancy));
    }

    private static String formatJunit(CognitiveReport report, boolean omitRedundancy) {
        return writeXml(junitTestSuites(report, omitRedundancy));
    }

    private static JsonReport jsonReport(CognitiveReport report, boolean omitRedundancy) {
        return new JsonReport(
                report.status(),
                report.threshold(),
                sortedMethods(report.methods()).stream()
                        .map(method -> jsonMethod(method, omitRedundancy))
                        .toList()
        );
    }

    private static JsonMethod jsonMethod(CognitiveReport.MethodReport method, boolean omitRedundancy) {
        return new JsonMethod(
                omitRedundancy ? null : method.status().value(),
                method.complexity(),
                method.methodName(),
                method.sourcePath(),
                method.startLine(),
                method.endLine()
        );
    }

    private static String writeJson(JsonReport report) {
        try {
            return JSON_WRITER.writeValueAsString(report) + '\n';
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to format JSON report", ex);
        }
    }

    private static JunitTestSuites junitTestSuites(CognitiveReport report, boolean omitRedundancy) {
        List<CognitiveReport.MethodReport> methods = sortedMethods(report.methods());
        int failed = countFailed(methods);
        String suiteTime = formatTime(report.elapsedSeconds());
        String testCaseTime = formatTime(methods.isEmpty() ? 0.0 : report.elapsedSeconds() / methods.size());
        JunitTestSuite testSuite = new JunitTestSuite(
                "cognitive-java",
                methods.size(),
                failed,
                0,
                0,
                suiteTime,
                new JunitProperties(List.of(
                        new JunitProperty("status", report.status()),
                        new JunitProperty("threshold", Integer.toString(report.threshold()))
                )),
                methods.stream()
                        .map(method -> junitTestCase(method, report.threshold(), omitRedundancy, testCaseTime))
                        .toList()
        );
        return new JunitTestSuites(methods.size(), failed, 0, 0, suiteTime, List.of(testSuite));
    }

    private static int countFailed(List<CognitiveReport.MethodReport> methods) {
        int count = 0;
        for (CognitiveReport.MethodReport method : methods) {
            if (method.status() == MethodStatus.FAILED) {
                count++;
            }
        }
        return count;
    }

    private static JunitTestCase junitTestCase(CognitiveReport.MethodReport method,
                                               int threshold,
                                               boolean omitRedundancy,
                                               String time) {
        return new JunitTestCase(
                method.sourcePath(),
                testcaseName(method),
                method.sourcePath(),
                method.startLine(),
                time,
                junitProperties(method, omitRedundancy),
                junitFailure(method, threshold)
        );
    }

    private static JunitProperties junitProperties(CognitiveReport.MethodReport method, boolean omitRedundancy) {
        List<JunitProperty> properties = new ArrayList<>();
        if (!omitRedundancy) {
            properties.add(new JunitProperty("status", method.status().value()));
        }
        properties.addAll(List.of(
                new JunitProperty("methodName", method.methodName()),
                new JunitProperty("className", method.className()),
                new JunitProperty("sourcePath", method.sourcePath()),
                new JunitProperty("startLine", Integer.toString(method.startLine())),
                new JunitProperty("endLine", Integer.toString(method.endLine())),
                new JunitProperty("complexity", Integer.toString(method.complexity()))
        ));
        return new JunitProperties(properties);
    }

    private static @Nullable JunitFailure junitFailure(CognitiveReport.MethodReport method, int threshold) {
        if (method.status() != MethodStatus.FAILED) {
            return null;
        }
        String message = "Cognitive Complexity threshold exceeded: "
                + method.complexity() + " > " + threshold;
        return new JunitFailure(message, "cognitive-java.threshold", junitDiagnosticText(method, threshold));
    }

    private static String writeXml(JunitTestSuites testSuites) {
        try {
            return XML_WRITER.writeValueAsString(testSuites) + '\n';
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to format JUnit XML report", ex);
        }
    }

    private static ObjectMapper xmlMapper() {
        XmlMapper mapper = XmlMapper.builder()
                .configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true)
                .build();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }

    private static String testcaseName(CognitiveReport.MethodReport method) {
        return method.methodName() + ":" + method.startLine();
    }

    private static String junitDiagnosticText(CognitiveReport.MethodReport method, int threshold) {
        return String.join("\n",
                "Cognitive Complexity: " + method.complexity(),
                "Threshold: " + threshold,
                "Source: " + method.sourcePath() + ":" + method.startLine() + "-" + method.endLine(),
                "Method: " + method.methodName()
        );
    }

    private static List<CognitiveReport.MethodReport> sortedMethods(List<CognitiveReport.MethodReport> entries) {
        List<CognitiveReport.MethodReport> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator
                .comparingInt(CognitiveReport.MethodReport::complexity)
                .reversed()
                .thenComparing(CognitiveReport.MethodReport::sourcePath)
                .thenComparing(CognitiveReport.MethodReport::methodName)
                .thenComparingInt(CognitiveReport.MethodReport::startLine));
        return sorted;
    }

    private static CognitiveReport failuresOnly(CognitiveReport report) {
        List<CognitiveReport.MethodReport> failedMethods = report.methods().stream()
                .filter(method -> method.status() == MethodStatus.FAILED)
                .toList();
        return new CognitiveReport(
                report.status(),
                report.threshold(),
                failedMethods,
                report.elapsedSeconds()
        );
    }

    private static String formatTime(double elapsedSeconds) {
        return String.format(Locale.ROOT, "%.6f", Math.max(0.0, elapsedSeconds));
    }

    private record JsonReport(
            String status,
            int threshold,
            List<JsonMethod> methods
    ) {
    }

    private record JsonMethod(
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Nullable String status,
            int cc,
            String method,
            String src,
            int lineStart,
            int lineEnd
    ) {
    }

    @JacksonXmlRootElement(localName = "testsuites")
    private record JunitTestSuites(
            @JacksonXmlProperty(isAttribute = true) int tests,
            @JacksonXmlProperty(isAttribute = true) int failures,
            @JacksonXmlProperty(isAttribute = true) int errors,
            @JacksonXmlProperty(isAttribute = true) int skipped,
            @JacksonXmlProperty(isAttribute = true) String time,
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "testsuite") List<JunitTestSuite> testSuites
    ) {
    }

    private record JunitTestSuite(
            @JacksonXmlProperty(isAttribute = true) String name,
            @JacksonXmlProperty(isAttribute = true) int tests,
            @JacksonXmlProperty(isAttribute = true) int failures,
            @JacksonXmlProperty(isAttribute = true) int errors,
            @JacksonXmlProperty(isAttribute = true) int skipped,
            @JacksonXmlProperty(isAttribute = true) String time,
            @JacksonXmlProperty(localName = "properties") JunitProperties properties,
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "testcase") List<JunitTestCase> testCases
    ) {
    }

    private record JunitTestCase(
            @JacksonXmlProperty(isAttribute = true, localName = "classname") String className,
            @JacksonXmlProperty(isAttribute = true) String name,
            @JacksonXmlProperty(isAttribute = true) String file,
            @JacksonXmlProperty(isAttribute = true) int line,
            @JacksonXmlProperty(isAttribute = true) String time,
            @JacksonXmlProperty(localName = "properties") JunitProperties properties,
            @Nullable JunitFailure failure
    ) {
    }

    private record JunitProperties(
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "property") List<JunitProperty> property
    ) {
    }

    private record JunitProperty(
            @JacksonXmlProperty(isAttribute = true) String name,
            @JacksonXmlProperty(isAttribute = true) String value
    ) {
    }

    private record JunitFailure(
            @JacksonXmlProperty(isAttribute = true) String message,
            @JacksonXmlProperty(isAttribute = true) String type,
            @JacksonXmlText String text
    ) {
    }

    private static final class JsonPrettyPrinter extends DefaultPrettyPrinter {
        private JsonPrettyPrinter() {
            indentObjectsWith(new DefaultIndenter("  ", "\n"));
            indentArraysWith(new DefaultIndenter("  ", "\n"));
        }

        private JsonPrettyPrinter(JsonPrettyPrinter base) {
            super(base);
        }

        @Override
        public DefaultPrettyPrinter createInstance() {
            return new JsonPrettyPrinter(this);
        }

        @Override
        public void writeObjectFieldValueSeparator(JsonGenerator generator) throws IOException {
            generator.writeRaw(": ");
        }
    }

    private record TableColumn(String header, Alignment alignment, CellValue cellValue) {
        private String align(String value, int width) {
            return alignment.align(value, width);
        }

        private String value(CognitiveReport.MethodReport method) {
            return cellValue.value(method);
        }
    }

    private interface CellValue {
        String value(CognitiveReport.MethodReport method);
    }

    private enum Alignment {
        LEFT {
            @Override
            String align(String value, int width) {
                return value + " ".repeat(width - value.length());
            }
        },
        RIGHT {
            @Override
            String align(String value, int width) {
                return " ".repeat(width - value.length()) + value;
            }
        };

        abstract String align(String value, int width);
    }
}

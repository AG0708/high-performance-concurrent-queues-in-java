package io.github.ag0708.stridequeue.jcstress;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import org.openjdk.jcstress.infra.Status;
import org.openjdk.jcstress.infra.collectors.TestResult;
import org.openjdk.jcstress.infra.grading.TestGrading;

/** Converts the raw JCStress result stream into a small machine-readable summary. */
public final class JcstressResultSummary {
    private JcstressResultSummary() {}

    /** Reads one result blob and writes a JSON summary. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("usage: RESULT_BLOB OUTPUT_JSON");
        }

        Summary summary = read(Path.of(arguments[0]));
        Path output = Path.of(arguments[1]);
        Files.writeString(output, summary.toJson(), StandardCharsets.UTF_8);
        if (summary.failedRecords != 0L) {
            throw new IllegalStateException("JCStress recorded failing or error results");
        }
    }

    private static Summary read(Path input) throws IOException, ClassNotFoundException {
        Summary summary = new Summary();
        try (ObjectInputStream stream =
                new ObjectInputStream(
                        new GZIPInputStream(
                                new BufferedInputStream(new FileInputStream(input.toFile()))))) {
            while (true) {
                try {
                    Object value = stream.readObject();
                    if (value instanceof TestResult result) {
                        summary.add(result);
                    }
                } catch (EOFException exception) {
                    break;
                }
            }
        }
        return summary;
    }

    private static final class Summary {
        private final Map<String, TestCounts> tests = new TreeMap<>();
        private final Map<String, Long> statuses = new TreeMap<>();
        private long resultRecords;
        private long passingRecords;
        private long failedRecords;
        private long interestingRecords;
        private long observations;

        private void add(TestResult result) {
            resultRecords++;
            observations = Math.addExact(observations, result.getTotalCount());
            statuses.merge(result.status().name(), 1L, Long::sum);

            boolean passed = false;
            boolean interesting = false;
            if (result.status() == Status.NORMAL) {
                TestGrading grading = result.grading();
                passed = grading.isPassed;
                interesting = grading.hasInteresting;
            }

            if (passed) {
                passingRecords++;
            } else {
                failedRecords++;
            }
            if (interesting) {
                interestingRecords++;
            }

            tests.computeIfAbsent(result.getName(), ignored -> new TestCounts())
                    .add(result.getTotalCount(), passed);
        }

        private String toJson() {
            StringBuilder json = new StringBuilder(1024);
            json.append("{\n")
                    .append("  \"schema\": \"stridequeue.jcstress-summary.v1\",\n")
                    .append("  \"result_records\": ").append(resultRecords).append(",\n")
                    .append("  \"passing_records\": ").append(passingRecords).append(",\n")
                    .append("  \"failed_records\": ").append(failedRecords).append(",\n")
                    .append("  \"interesting_records\": ").append(interestingRecords).append(",\n")
                    .append("  \"observations\": ").append(observations).append(",\n")
                    .append("  \"statuses\": {");
            appendLongMap(json, statuses);
            json.append("\n  },\n  \"tests\": {");

            boolean first = true;
            for (Map.Entry<String, TestCounts> entry : tests.entrySet()) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                TestCounts counts = entry.getValue();
                json.append("\n    \"")
                        .append(escape(entry.getKey()))
                        .append("\": {\"records\": ")
                        .append(counts.records)
                        .append(", \"passing_records\": ")
                        .append(counts.passingRecords)
                        .append(", \"observations\": ")
                        .append(counts.observations)
                        .append('}');
            }
            return json.append("\n  }\n}\n").toString();
        }

        private static void appendLongMap(StringBuilder json, Map<String, Long> values) {
            boolean first = true;
            for (Map.Entry<String, Long> entry : values.entrySet()) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append("\n    \"")
                        .append(escape(entry.getKey()))
                        .append("\": ")
                        .append(entry.getValue());
            }
        }

        private static String escape(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    private static final class TestCounts {
        private long records;
        private long passingRecords;
        private long observations;

        private void add(long count, boolean passed) {
            records++;
            observations = Math.addExact(observations, count);
            if (passed) {
                passingRecords++;
            }
        }
    }
}

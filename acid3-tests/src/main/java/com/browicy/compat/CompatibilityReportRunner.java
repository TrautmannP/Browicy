package com.browicy.compat;

import com.browicy.acid3.Acid3Harness;
import com.browicy.engine.BrowicyEngine;
import com.browicy.engine.PageSession;
import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import com.browicy.engine.js.JsExecutionResult;
import com.browicy.engine.net.NetworkRequestEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Maven-invoked compatibility suite that writes detailed JSON and HTML reports. */
public final class CompatibilityReportRunner {

    private static final String DEFAULT_CSS3TEST_URL =
            "https://css3test.com/?filter=css2007";
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private CompatibilityReportRunner() { }

    public static void main(String[] args) throws Exception {
        Instant started = Instant.now();
        String cssUrl = System.getProperty("browicy.css3test.url", DEFAULT_CSS3TEST_URL);
        Path output = Path.of(System.getProperty(
                "browicy.report.directory", "target/compatibility-reports"));

        List<SuiteResult> suites = List.of(runCss3Test(cssUrl), runAcid3());
        Report report = new Report(1, started, Instant.now(), suites);
        Files.createDirectories(output);

        String baseName = "compatibility-" + FILE_TIME.format(report.finishedAt());
        String json = Json.write(report.toMap(), true, 0) + System.lineSeparator();
        String html = Html.write(report);
        Files.writeString(output.resolve(baseName + ".json"), json, StandardCharsets.UTF_8);
        Files.writeString(output.resolve(baseName + ".html"), html, StandardCharsets.UTF_8);
        Files.writeString(output.resolve("latest.json"), json, StandardCharsets.UTF_8);
        Files.writeString(output.resolve("latest.html"), html, StandardCharsets.UTF_8);

        System.out.printf("Compatibility report: %s%n", output.resolve("latest.html").toAbsolutePath());
        for (SuiteResult suite : suites) {
            System.out.printf("  %s: %d/%d passed (%s)%n",
                    suite.name(), suite.passed(), suite.total(), suite.score());
        }
    }

    private static SuiteResult runCss3Test(String url) {
        Instant started = Instant.now();
        List<NetworkRequestEvent> network = java.util.Collections.synchronizedList(new ArrayList<>());
        try (BrowicyEngine engine = new BrowicyEngine()) {
            engine.addRequestObserver(network::add);
            try (PageSession session = engine.loadPageSession(url, ignored -> { })) {
                session.awaitResources();
                Document document = session.document();
                JsExecutionResult javascript = session.runtime().snapshot();
                List<CaseResult> cases = cssCases(document);
                int passed = (int) cases.stream().filter(CaseResult::passed).count();
                List<String> warnings = new ArrayList<>(javascript.errors());
                network.stream().filter(NetworkRequestEvent.Failed.class::isInstance)
                        .map(NetworkRequestEvent.Failed.class::cast)
                        .map(failure -> failure.url() + ": " + failure.cause().getMessage())
                        .forEach(warnings::add);
                String score = text(document, "score", percent(passed, cases.size()));
                return new SuiteResult("css3test-css2007", "CSS3Test – CSS 2007", url,
                        score, passed, cases.size() - passed, cases.size(),
                        started, Instant.now(), cases, warnings);
            }
        } catch (RuntimeException failure) {
            return failedSuite("css3test-css2007", "CSS3Test – CSS 2007", url,
                    started, failure);
        }
    }

    private static List<CaseResult> cssCases(Document document) {
        List<CaseResult> results = new ArrayList<>();
        int index = 0;
        for (Element details : document.getElementsByTagName("details")) {
            Element summary = directChild(details, "summary");
            String feature = summary == null ? "Unknown feature"
                    : ownText(summary, clean(summary.getTextContent()));
            String specification = specification(details);
            for (Element item : details.getElementsByTagName("li")) {
                String cssClass = String.join(" ", item.getClassNames());
                boolean passed = item.hasClass("pass");
                String status = passed ? "pass" : cssClass.isBlank() ? "fail" : cssClass;
                results.add(new CaseResult("css-" + (++index), specification, feature,
                        clean(item.getTextContent()), status, passed,
                        passed ? "" : "CSS capability is not fully supported"));
            }
        }
        return List.copyOf(results);
    }

    private static String specification(Element node) {
        for (Node parent = node.getParent(); parent instanceof Element element;
             parent = parent.getParent()) {
            String id = element.getAttribute("id");
            if (id != null && !id.isBlank() && element.hasClass("tests")) {
                Element heading = directChild(element, "h1");
                return heading == null ? id : ownText(heading, clean(heading.getTextContent()));
            }
        }
        return "CSS 2007";
    }

    private static SuiteResult runAcid3() {
        Instant started = Instant.now();
        try {
            Acid3Harness.RunResult run = Acid3Harness.run();
            List<CaseResult> cases = run.tests().stream().map(test -> new CaseResult(
                    "acid3-" + test.index(), "Acid3", "Subtest " + test.index(),
                    "Acid3 test " + test.index(), test.status().name().toLowerCase(),
                    test.status() == Acid3Harness.Status.PASS, test.message())).toList();
            int passed = (int) run.passed();
            return new SuiteResult("acid3", "Acid3", "embedded:wpt/acid3/test.html",
                    passed + "/" + Acid3Harness.TEST_COUNT, passed,
                    (int) run.failed(), Acid3Harness.TEST_COUNT, started, Instant.now(),
                    cases, run.scriptErrors());
        } catch (RuntimeException failure) {
            return failedSuite("acid3", "Acid3", "embedded:wpt/acid3/test.html",
                    started, failure);
        }
    }

    private static SuiteResult failedSuite(String id, String name, String source,
                                           Instant started, RuntimeException failure) {
        String message = failure.getMessage() == null
                ? failure.getClass().getSimpleName() : failure.getMessage();
        CaseResult result = new CaseResult(id + "-runner", "Runner", "Execution",
                "Suite execution", "error", false, message);
        return new SuiteResult(id, name, source, "error", 0, 1, 1,
                started, Instant.now(), List.of(result), List.of(message));
    }

    private static Element directChild(Element parent, String tag) {
        return parent.getChildElements().stream()
                .filter(child -> tag.equals(child.getTagName())).findFirst().orElse(null);
    }

    private static String text(Document document, String id, String fallback) {
        Element element = document.getElementById(id);
        return element == null || element.getTextContent().isBlank()
                ? fallback : clean(element.getTextContent());
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").strip();
    }

    private static String ownText(Element element, String fallback) {
        for (Node child : element.getChildren()) {
            if (child.getNodeType() == Node.TEXT_NODE && !clean(child.getTextContent()).isBlank()) {
                return clean(child.getTextContent());
            }
        }
        return fallback;
    }

    private static String percent(int passed, int total) {
        return total == 0 ? "0%" : Math.round(100.0 * passed / total) + "%";
    }

    private record Report(int schemaVersion, Instant startedAt, Instant finishedAt,
                          List<SuiteResult> suites) {
        int passed() { return suites.stream().mapToInt(SuiteResult::passed).sum(); }
        int failed() { return suites.stream().mapToInt(SuiteResult::failed).sum(); }
        int total() { return suites.stream().mapToInt(SuiteResult::total).sum(); }
        Map<String, Object> toMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("schemaVersion", schemaVersion);
            value.put("startedAt", startedAt.toString());
            value.put("finishedAt", finishedAt.toString());
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("passed", passed()); summary.put("failed", failed());
            summary.put("total", total());
            value.put("summary", summary);
            value.put("suites", suites.stream().map(SuiteResult::toMap).toList());
            return value;
        }
    }

    private record SuiteResult(String id, String name, String source, String score,
                               int passed, int failed, int total, Instant startedAt,
                               Instant finishedAt, List<CaseResult> cases,
                               List<String> warnings) {
        SuiteResult {
            cases = List.copyOf(cases);
            warnings = List.copyOf(warnings);
        }
        Map<String, Object> toMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", id); value.put("name", name); value.put("source", source);
            value.put("score", score); value.put("passed", passed); value.put("failed", failed);
            value.put("total", total); value.put("startedAt", startedAt.toString());
            value.put("finishedAt", finishedAt.toString()); value.put("warnings", warnings);
            value.put("cases", cases.stream().map(CaseResult::toMap).toList());
            return value;
        }
    }

    private record CaseResult(String id, String group, String feature, String name,
                              String status, boolean passed, String message) {
        Map<String, Object> toMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", id); value.put("group", group); value.put("feature", feature);
            value.put("name", name); value.put("status", status);
            value.put("passed", passed); value.put("message", message);
            return value;
        }
    }

    private static final class Html {
        static String write(Report report) {
            StringBuilder html = new StringBuilder("""
                    <!doctype html><html lang="en"><head><meta charset="utf-8">
                    <meta name="viewport" content="width=device-width,initial-scale=1">
                    <title>Browicy compatibility report</title><style>
                    :root{color-scheme:dark;font-family:Inter,system-ui,sans-serif;background:#0d1117;color:#e6edf3}
                    body{max-width:1400px;margin:auto;padding:32px}h1,h2{margin:.2em 0}.muted{color:#8b949e}
                    .cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:16px;margin:24px 0}
                    .card,section{background:#161b22;border:1px solid #30363d;border-radius:10px;padding:18px}
                    .metric{font-size:2rem;font-weight:700}.pass{color:#3fb950}.fail,.error,.not_reported{color:#f85149}
                    table{width:100%;border-collapse:collapse;font-size:.9rem}th,td{text-align:left;padding:8px;border-bottom:1px solid #30363d;vertical-align:top}
                    th{position:sticky;top:0;background:#161b22}section{margin:24px 0;overflow:auto}.badge{font-weight:700;text-transform:uppercase}
                    details{margin:12px 0}.warnings{color:#d29922;white-space:pre-wrap}code{word-break:break-word}
                    </style></head><body>
                    """);
            html.append("<h1>Browicy compatibility report</h1><p class=muted>Generated ")
                    .append(escape(report.finishedAt().toString())).append("</p><div class=cards>")
                    .append(card("Passed", report.passed(), "pass"))
                    .append(card("Failed", report.failed(), "fail"))
                    .append(card("Total", report.total(), ""))
                    .append(card("Overall", percent(report.passed(), report.total()), ""))
                    .append("</div>");
            for (SuiteResult suite : report.suites()) {
                html.append("<section><h2>").append(escape(suite.name())).append(" — ")
                        .append(escape(suite.score())).append("</h2><p class=muted><code>")
                        .append(escape(suite.source())).append("</code></p><div class=cards>")
                        .append(card("Passed", suite.passed(), "pass"))
                        .append(card("Failed", suite.failed(), "fail"))
                        .append(card("Total", suite.total(), ""))
                        .append("</div>");
                if (!suite.warnings().isEmpty()) {
                    html.append("<details><summary>Warnings (").append(suite.warnings().size())
                            .append(")</summary><div class=warnings>")
                            .append(escape(String.join("\n", suite.warnings()))).append("</div></details>");
                }
                html.append("<table><thead><tr><th>Status</th><th>Group</th><th>Feature</th><th>Test</th><th>Details</th></tr></thead><tbody>");
                for (CaseResult test : suite.cases()) {
                    html.append("<tr><td><span class=\"badge ").append(escape(test.status()))
                            .append("\">").append(escape(test.status())).append("</span></td><td>")
                            .append(escape(test.group())).append("</td><td>")
                            .append(escape(test.feature())).append("</td><td>")
                            .append(escape(test.name())).append("</td><td>")
                            .append(escape(test.message())).append("</td></tr>");
                }
                html.append("</tbody></table></section>");
            }
            return html.append("</body></html>\n").toString();
        }
        private static String card(String name, Object value, String cssClass) {
            return "<div class=card><div class=muted>" + escape(name) + "</div><div class=\"metric "
                    + cssClass + "\">" + escape(String.valueOf(value)) + "</div></div>";
        }
        private static String escape(String value) {
            return value.replace("&", "&amp;").replace("<", "&lt;")
                    .replace(">", "&gt;").replace("\"", "&quot;");
        }
    }

    private static final class Json {
        static String write(Object value, boolean pretty, int depth) {
            if (value == null) return "null";
            if (value instanceof String string) return quote(string);
            if (value instanceof Number || value instanceof Boolean) return value.toString();
            if (value instanceof Map<?, ?> map) {
                List<String> entries = new ArrayList<>();
                map.forEach((key, child) -> entries.add(quote(String.valueOf(key)) + ":"
                        + (pretty ? " " : "") + write(child, pretty, depth + 1)));
                return collection("{", "}", entries, pretty, depth);
            }
            if (value instanceof Iterable<?> iterable) {
                List<String> entries = new ArrayList<>();
                iterable.forEach(child -> entries.add(write(child, pretty, depth + 1)));
                return collection("[", "]", entries, pretty, depth);
            }
            return quote(value.toString());
        }
        private static String collection(String open, String close, List<String> entries,
                                         boolean pretty, int depth) {
            if (entries.isEmpty()) return open + close;
            if (!pretty) return open + String.join(",", entries) + close;
            String indent = "  ".repeat(depth + 1);
            return open + "\n" + indent + String.join(",\n" + indent, entries)
                    + "\n" + "  ".repeat(depth) + close;
        }
        private static String quote(String value) {
            StringBuilder result = new StringBuilder("\"");
            for (char character : value.toCharArray()) {
                switch (character) {
                    case '\"' -> result.append("\\\"");
                    case '\\' -> result.append("\\\\");
                    case '\n' -> result.append("\\n");
                    case '\r' -> result.append("\\r");
                    case '\t' -> result.append("\\t");
                    default -> {
                        if (character < 0x20) result.append(String.format("\\u%04x", (int) character));
                        else result.append(character);
                    }
                }
            }
            return result.append('\"').toString();
        }
    }
}

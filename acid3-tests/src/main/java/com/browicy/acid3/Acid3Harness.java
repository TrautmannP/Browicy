package com.browicy.acid3;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.DocumentReadyState;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Event;
import com.browicy.engine.html.HtmlParser;
import com.browicy.engine.js.JavaScriptEngine;
import com.browicy.engine.js.JavaScriptSource;
import com.browicy.engine.js.JsExecutionResult;
import com.browicy.engine.js.PageRuntime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Executes the original Acid3 JavaScript tests once and exposes every subtest result. */
public final class Acid3Harness {

    public static final int TEST_COUNT = 100;
    private static final String REPORT_PREFIX = "log: ACID3|";

    private Acid3Harness() { }

    public static RunResult run() {
        String html = instrumentReports(readResource("/acid3/test.html"));
        Document document = new HtmlParser().parse(html, "http://localhost/acid3/test.html");
        JsExecutionResult execution = executeToCompletion(document);

        List<TestResult> results = new ArrayList<>();
        for (int index = 0; index < TEST_COUNT; index++) {
            results.add(new TestResult(index, Status.NOT_REPORTED,
                    "Acid3 test " + index + " did not report a result"));
        }
        for (String message : execution.consoleMessages()) {
            if (!message.startsWith(REPORT_PREFIX)) continue;
            String[] fields = message.substring(REPORT_PREFIX.length()).split("\\|", 3);
            if (fields.length < 2 || fields[0].equals("TOTAL")) continue;
            int index = Integer.parseInt(fields[0]);
            String details = fields.length == 3 ? fields[2]
                    : "Acid3 test " + index + " " + fields[1];
            Status status = "pass".equals(fields[1]) ? Status.PASS : Status.FAIL;
            results.set(index, new TestResult(index, status, details));
        }
        return new RunResult(results, execution.consoleMessages(), execution.errors());
    }

    private static JsExecutionResult executeToCompletion(Document document) {
        JavaScriptEngine engine = new JavaScriptEngine(100_000_000);
        try (PageRuntime runtime = engine.createPageRuntime(document)) {
            int scriptIndex = 0;
            for (Element script : document.getElementsByTagName("script")) {
                if (!script.hasAttribute("src") && !script.getTextContent().isBlank()) {
                    runtime.execute(new JavaScriptSource(script.getTextContent(), script,
                            "acid3-inline-" + (++scriptIndex) + ".js"));
                }
            }
            runtime.enqueueTask(() -> document.transitionTo(DocumentReadyState.INTERACTIVE));
            runtime.submitEvent(document, new Event("DOMContentLoaded", true, false)).join();
            runtime.enqueueTask(() -> document.transitionTo(DocumentReadyState.COMPLETE));
            if (document.getBody() != null) {
                runtime.submitEvent(document.getBody(), new Event("load", false, false)).join();
            }

            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
            JsExecutionResult snapshot;
            do {
                runtime.awaitIdle();
                snapshot = runtime.snapshot();
                long reported = snapshot.consoleMessages().stream()
                        .filter(message -> message.startsWith(REPORT_PREFIX))
                        .filter(message -> !message.startsWith(REPORT_PREFIX + "TOTAL|"))
                        .count();
                if (reported >= TEST_COUNT) return snapshot;
                try {
                    Thread.sleep(10);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return snapshot;
                }
            } while (System.nanoTime() < deadline);
            return snapshot;
        }
    }

    private static String instrumentReports(String html) {
        return html
                .replace("window.parent.postMessage({num_tests: tests.length}, \"*\");",
                        "console.log('ACID3|TOTAL|' + tests.length);")
                .replace("window.parent.postMessage({test: index, result: \"pass\"}, \"*\");",
                        "console.log('ACID3|' + index + '|pass');")
                .replace("window.parent.postMessage({test: index, result: \"fail\", message: s}, \"*\");",
                        "console.log('ACID3|' + index + '|fail|' + s);");
    }

    private static String readResource(String path) {
        try (InputStream stream = Acid3Harness.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing test resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read test resource: " + path, exception);
        }
    }

    public enum Status { PASS, FAIL, NOT_REPORTED }

    public record TestResult(int index, Status status, String message) { }

    public record RunResult(List<TestResult> tests,
                            List<String> consoleMessages,
                            List<String> scriptErrors) {
        public RunResult {
            tests = List.copyOf(tests);
            consoleMessages = List.copyOf(consoleMessages);
            scriptErrors = List.copyOf(scriptErrors);
        }

        public long passed() {
            return tests.stream().filter(test -> test.status() == Status.PASS).count();
        }

        public long failed() {
            return tests.size() - passed();
        }
    }
}

package com.browicy.acid3;

import com.browicy.engine.dom.Document;
import com.browicy.engine.html.HtmlParser;
import com.browicy.engine.js.JavaScriptEngine;
import com.browicy.engine.js.JsExecutionResult;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(Parameterized.class)
public class Acid3Test {

    private static final int TEST_COUNT = 100;
    private static final String REPORT_PREFIX = "log: ACID3|";
    private static final List<Result> RESULTS = runAcid3();

    @Parameterized.Parameters(name = "Acid3 test {0}")
    public static Collection<Object[]> tests() {
        List<Object[]> tests = new ArrayList<>(TEST_COUNT);
        for (int index = 0; index < TEST_COUNT; index++) {
            tests.add(new Object[]{index});
        }
        return tests;
    }

    private final int index;

    public Acid3Test(int index) {
        this.index = index;
    }

    @Test
    public void acid3Subtest() {
        Result result = RESULTS.get(index);
        assertNotNull("Acid3 test " + index + " did not report a result", result);
        assertEquals(result.message(), "pass", result.status());
    }

    private static List<Result> runAcid3() {
        String html = instrumentReports(readResource("/acid3/test.html"));
        Document document = new HtmlParser().parse(html, "http://localhost/acid3/test.html");
        JsExecutionResult execution = new JavaScriptEngine(100_000_000).runScripts(document);

        List<Result> results = new ArrayList<>();
        for (int index = 0; index < TEST_COUNT; index++) {
            results.add(null);
        }
        for (String message : execution.consoleMessages()) {
            if (!message.startsWith(REPORT_PREFIX)) {
                continue;
            }
            String[] fields = message.substring(REPORT_PREFIX.length()).split("\\|", 3);
            if (fields.length < 2 || fields[0].equals("TOTAL")) {
                continue;
            }
            int testIndex = Integer.parseInt(fields[0]);
            String details = fields.length == 3 ? fields[2] : "Acid3 test " + testIndex + " failed";
            results.set(testIndex, new Result(fields[1], details));
        }
        return Collections.unmodifiableList(results);
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
        try (InputStream stream = Acid3Test.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing test resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read test resource: " + path, exception);
        }
    }

    private record Result(String status, String message) {
    }
}

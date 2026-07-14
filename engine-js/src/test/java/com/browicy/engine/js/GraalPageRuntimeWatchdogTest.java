package com.browicy.engine.js;

import com.browicy.engine.dom.Document;
import com.browicy.engine.html.HtmlParser;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GraalPageRuntimeWatchdogTest {

    private final HtmlParser parser = new HtmlParser();

    @Test
    public void interruptsRunawayScriptAndKeepsRuntimeUsable() {
        Document document = parser.parse("<html><body></body></html>");
        JavaScriptEngine engine = new JavaScriptEngine(Long.MAX_VALUE);
        try (PageRuntime runtime = engine.createPageRuntime(document)) {
            ((GraalPageRuntime) runtime).taskTimeBudgetMillisForTesting(300);

            JsExecutionResult result = runtime.execute(new JavaScriptSource(
                    "while (true) { }", null, "endlosschleife.js"));

            assertTrue("Endlosschleife muss als Fehler enden", result.hasErrors());
            assertTrue(String.valueOf(result.errors()),
                    result.errors().getFirst().contains("Zeitbudget"));

            JsExecutionResult afterwards = runtime.execute(new JavaScriptSource(
                    "console.log('läuft weiter')", null, "danach.js"));
            assertFalse("Runtime muss nach dem Abbruch nutzbar bleiben: " + afterwards.errors(),
                    afterwards.hasErrors());
        }
    }

    @Test
    public void diagnosticsExposeRunningTaskWithoutBlocking() throws Exception {
        Document document = parser.parse("<html><body></body></html>");
        JavaScriptEngine engine = new JavaScriptEngine(Long.MAX_VALUE);
        try (PageRuntime runtime = engine.createPageRuntime(document)) {
            ((GraalPageRuntime) runtime).taskTimeBudgetMillisForTesting(1_000);
            runtime.enqueue(new PageTask.Script(new JavaScriptSource(
                    "while (true) { }", null, "beschaeftigt.js")));

            PageRuntimeDiagnostics busy = awaitBusyDiagnostics(runtime);
            assertNotNull("Diagnose meldete keine laufende Task", busy);
            assertTrue(busy.currentTask(), busy.currentTask().contains("beschaeftigt.js"));

            runtime.awaitIdle();
            assertFalse(runtime.diagnostics().busy());
        }
    }

    @Test
    public void closedRuntimeReportsClosedDiagnostics() {
        PageRuntimeDiagnostics diagnostics = PageRuntime.closed().diagnostics();
        assertTrue(diagnostics.closed());
        assertFalse(diagnostics.busy());
    }

    private static PageRuntimeDiagnostics awaitBusyDiagnostics(PageRuntime runtime)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            PageRuntimeDiagnostics diagnostics = runtime.diagnostics();
            if (diagnostics.busy()) {
                return diagnostics;
            }
            Thread.sleep(10);
        }
        return null;
    }
}

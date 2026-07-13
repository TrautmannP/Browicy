package com.browicy.engine.js;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Event;
import com.browicy.engine.html.HtmlParser;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PageRuntimeTest {

    private final HtmlParser parser = new HtmlParser();
    private final JavaScriptEngine engine = new JavaScriptEngine();

    @Test
    public void globalStateAndListenersSurviveInitialScriptExecution() {
        Document document = parse("""
                <html><body>
                  <button id="button">Klick</button>
                  <output id="result">0</output>
                </body></html>
                """);
        Element button = document.getElementById("button");

        try (PageRuntime runtime = engine.createPageRuntime(document)) {
            JsExecutionResult result = runtime.execute(new JavaScriptSource("""
                    window.counter = 1;
                    const result = document.getElementById('result');
                    document.getElementById('button').addEventListener('click', () => {
                      counter++;
                      result.textContent = String(counter);
                    });
                    """, null, "listener.js"));

            assertFalse(String.valueOf(result.errors()), result.hasErrors());
            runtime.submitEvent(button, new Event("click", true, true)).join();
            runtime.submitEvent(button, new Event("click", true, true)).join();

            assertEquals("3", document.getElementById("result").getTextContent());
        }
    }

    @Test
    public void delayedTimerExecutesOnPersistentRuntime() throws Exception {
        Document document = parse("""
                <html><body><p id="message">Warte</p></body></html>
                """);
        CountDownLatch changed = new CountDownLatch(1);
        document.addMutationListener(mutation -> changed.countDown());

        try (PageRuntime runtime = engine.createPageRuntime(document)) {
            runtime.execute(new JavaScriptSource("""
                    setTimeout(() => {
                      document.getElementById('message').textContent = 'Fertig';
                    }, 50);
                    """, null, "timer.js"));

            assertTrue("Timer hat das DOM nicht aktualisiert",
                    changed.await(2, TimeUnit.SECONDS));
            runtime.awaitIdle();
            assertEquals("Fertig", document.getElementById("message").getTextContent());
        }
    }

    @Test
    public void listenerExceptionDoesNotStopLaterListenersOrTasks() {
        Document document = parse("""
                <html><body><button id="button"></button><output id="result">0</output></body></html>
                """);
        Element button = document.getElementById("button");

        try (PageRuntime runtime = engine.createPageRuntime(document)) {
            runtime.execute(new JavaScriptSource("""
                    const button = document.getElementById('button');
                    const result = document.getElementById('result');
                    button.addEventListener('click', () => { throw new Error('boom'); });
                    button.addEventListener('click', () => {
                      result.textContent = String(Number(result.textContent) + 1);
                    });
                    """, null, "errors.js"));

            runtime.submitEvent(button, new Event("click", true, true)).join();
            runtime.submitEvent(button, new Event("click", true, true)).join();

            assertEquals("2", document.getElementById("result").getTextContent());
        }
    }

    @Test
    public void closeCancelsTimersAndRejectsNewWork() throws Exception {
        Document document = parse("""
                <html><body><p id="message">Unverändert</p></body></html>
                """);
        PageRuntime runtime = engine.createPageRuntime(document);
        runtime.execute(new JavaScriptSource("""
                setTimeout(() => {
                  document.getElementById('message').textContent = 'Zu spät';
                }, 200);
                """, null, "cancelled-timer.js"));

        runtime.close();
        Thread.sleep(300);

        assertTrue(runtime.isClosed());
        assertEquals("Unverändert", document.getElementById("message").getTextContent());
        assertTrue(runtime.submitTask(() -> { }).isCompletedExceptionally());
    }

    @Test
    public void closeRemovesJavaScriptListenersFromDomNodes() {
        Document document = parse("""
                <html><body><button id="button"></button><output id="result">0</output></body></html>
                """);
        Element button = document.getElementById("button");
        PageRuntime runtime = engine.createPageRuntime(document);
        runtime.execute(new JavaScriptSource("""
                document.getElementById('button').addEventListener('click', () => {
                  document.getElementById('result').textContent = '1';
                });
                """, null, "listener-cleanup.js"));

        runtime.close();
        button.dispatchEvent(new Event("click", true, true));

        assertEquals("0", document.getElementById("result").getTextContent());
    }

    private Document parse(String html) {
        return parser.parse(html, "about:test");
    }
}

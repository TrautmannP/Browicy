package com.browicy.engine;

import com.browicy.engine.dom.Document;
import com.browicy.engine.net.LocalTestServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * P3: Cross-Origin-XHR/fetch ohne gültigen Access-Control-Allow-Origin-Header
 * löst schnell onerror aus und lässt die Seite ihre Ressourcen abschließen.
 */
public class CrossOriginFailFastTest {

    private LocalTestServer server;
    private LocalTestServer other;
    private final BrowicyEngine engine = new BrowicyEngine();

    @Before
    public void setUp() throws IOException {
        server = new LocalTestServer();
        other = new LocalTestServer();
    }

    @After
    public void tearDown() {
        engine.close();
        other.close();
        server.close();
    }

    @Test
    public void crossOriginXhrWithoutAllowHeaderFailsFastAndPageCompletes() throws Exception {
        other.serveText("/intern.txt", "text/plain; charset=utf-8", "intern");
        server.serveHtml("/", """
                <html><body><output id="out">pending</output><script>
                  var xhr = new XMLHttpRequest();
                  xhr.onerror = function() {
                    document.getElementById('out').textContent = 'error';
                  };
                  xhr.open('GET', '%s');
                  xhr.send();
                </script></body></html>
                """.formatted(other.url("/intern.txt")));

        long start = System.nanoTime();
        try (PageSession session = engine.loadPageSession(
                server.url("/"), PageUpdateListener.NO_OP)) {
            session.awaitResources();
            awaitOutput(session, "error", 5_000);
        }
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue("Cross-Origin-Fehler dauerte zu lange: " + elapsedMillis + " ms",
                elapsedMillis < 5_000);
    }

    @Test
    public void crossOriginXhrWithWildcardHeaderSucceeds() throws Exception {
        other.on("/offen.txt", exchange -> {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            LocalTestServer.respond(exchange, 200, "text/plain; charset=utf-8",
                    "öffentlich".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        });
        server.serveHtml("/", """
                <html><body><output id="out">pending</output><script>
                  var xhr = new XMLHttpRequest();
                  xhr.onload = function() {
                    document.getElementById('out').textContent = xhr.responseText;
                  };
                  xhr.open('GET', '%s');
                  xhr.send();
                </script></body></html>
                """.formatted(other.url("/offen.txt")));

        try (PageSession session = engine.loadPageSession(
                server.url("/"), PageUpdateListener.NO_OP)) {
            session.awaitResources();
            awaitOutput(session, "öffentlich", 5_000);
        }
    }

    private static void awaitOutput(PageSession session, String expected, long timeoutMillis)
            throws InterruptedException {
        Document document = session.document();
        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        String actual = "";
        while (System.nanoTime() < deadline) {
            session.runtime().awaitIdle();
            actual = document.getElementById("out").getTextContent();
            if (expected.equals(actual)) {
                return;
            }
            Thread.sleep(20);
        }
        fail("Erwartete Ausgabe '" + expected + "', zuletzt gesehen: '" + actual + "'");
    }
}

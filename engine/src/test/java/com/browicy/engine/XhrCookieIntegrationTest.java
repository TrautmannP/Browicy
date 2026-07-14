package com.browicy.engine;

import com.browicy.engine.dom.Document;
import com.browicy.engine.net.LocalTestServer;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.After;
import org.junit.Before;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class XhrCookieIntegrationTest {

    private LocalTestServer server;
    private final BrowicyEngine engine = new BrowicyEngine();

    @Before
    public void setUp() throws IOException {
        server = new LocalTestServer();
    }

    @After
    public void tearDown() {
        engine.close();
        server.close();
    }

    @Test
    public void inlineScriptLoadsDataViaAsyncXhr() throws Exception {
        server.serveText("/api/nachricht.txt", "text/plain; charset=utf-8", "vom Server");
        server.serveHtml("/", """
                <html><body>
                  <output id="out"></output>
                  <script>
                    var xhr = new XMLHttpRequest();
                    xhr.onreadystatechange = function() {
                      if (xhr.readyState === 4 && xhr.status === 200) {
                        document.getElementById('out').textContent = xhr.responseText;
                      }
                    };
                    xhr.open('GET', '/api/nachricht.txt');
                    xhr.send();
                  </script>
                </body></html>
                """);

        try (PageSession session = engine.loadPageSession(server.url("/"), PageUpdateListener.NO_OP)) {
            session.awaitResources();
            awaitOutput(session, "vom Server");
        }
    }

    @Test
    public void asynchronousXhrPostsRequestBody() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        server.on("/api/speichern", exchange -> {
            method.set(exchange.getRequestMethod());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            LocalTestServer.respond(exchange, 200, "text/plain; charset=utf-8",
                    "gespeichert".getBytes(StandardCharsets.UTF_8));
        });
        server.serveHtml("/", """
                <html><body>
                  <output id="out"></output>
                  <script>
                    var xhr = new XMLHttpRequest();
                    xhr.onload = function() {
                      document.getElementById('out').textContent = xhr.responseText;
                    };
                    xhr.open('POST', '/api/speichern');
                    xhr.setRequestHeader('Content-Type', 'application/json');
                    xhr.send(JSON.stringify({wert: 42}));
                  </script>
                </body></html>
                """);

        try (PageSession session = engine.loadPageSession(
                server.url("/"), PageUpdateListener.NO_OP)) {
            session.awaitResources();
            awaitOutput(session, "gespeichert");
        }

        assertEquals("POST", method.get());
        assertEquals("application/json", contentType.get());
        assertEquals("{\"wert\":42}", body.get());
    }

    @Test
    public void synchronousXhrDeliversTheResponseInline() throws Exception {
        server.serveText("/api/direkt.txt", "text/plain; charset=utf-8", "sofort");
        server.serveHtml("/", """
                <html><body>
                  <output id="out"></output>
                  <script>
                    var xhr = new XMLHttpRequest();
                    xhr.open('GET', '/api/direkt.txt', false);
                    xhr.send();
                    document.getElementById('out').textContent =
                        xhr.status + '|' + xhr.responseText;
                  </script>
                </body></html>
                """);

        try (PageSession session = engine.loadPageSession(server.url("/"), PageUpdateListener.NO_OP)) {
            session.awaitResources();
            awaitOutput(session, "200|sofort");
        }
    }

    @Test
    public void documentCookieRoundTripsAndFetchResponsesFillTheStore() throws Exception {
        server.on("/api/anmelden", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "sitzung=server123; Path=/");
            exchange.getResponseHeaders().add("Set-Cookie", "geheim=intern; Path=/; HttpOnly");
            LocalTestServer.respond(exchange, 200, "text/plain", new byte[0]);
        });
        server.serveHtml("/", """
                <html><body>
                  <output id="out"></output>
                  <script>
                    document.cookie = 'lokal=skript; Path=/';
                    fetch('/api/anmelden').then(function() {
                      document.getElementById('out').textContent = document.cookie;
                    });
                  </script>
                </body></html>
                """);

        try (PageSession session = engine.loadPageSession(server.url("/"), PageUpdateListener.NO_OP)) {
            session.awaitResources();
            awaitOutput(session, "lokal=skript; sitzung=server123");
            assertEquals("lokal=skript; sitzung=server123",
                    session.cookies().cookiesForScript(URI.create(server.url("/"))));
        }
    }

    private static void awaitOutput(PageSession session, String expected)
            throws InterruptedException {
        Document document = session.document();
        long deadline = System.nanoTime() + 5_000_000_000L;
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

package com.browicy.engine;

import com.browicy.engine.js.JsCookieStore;
import com.browicy.engine.net.LocalTestServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertTrue;

/**
 * P2: Der Cookie-Store lebt auf Engine-Ebene, überlebt Navigationen, wird an
 * ausgehende Dokument-Requests angehängt und erfasst Set-Cookie-Header auch
 * aus Dokument-Antworten.
 */
public class CookieNavigationIntegrationTest {

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
    public void xhrSetCookieIsSentOnFollowingNavigation() throws Exception {
        AtomicReference<String> cookieHeader = new AtomicReference<>();
        server.on("/abo/setconsentcookie.php", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie",
                    "golem_consent20=ok; Path=/; Max-Age=3600");
            LocalTestServer.respond(exchange, 200, "text/html; charset=UTF-8", new byte[0]);
        });
        server.on("/", exchange -> {
            cookieHeader.set(exchange.getRequestHeaders().getFirst("Cookie"));
            LocalTestServer.respond(exchange, 200, "text/html; charset=UTF-8",
                    "<html><head><title>Echte Seite</title></head><body>echt</body></html>"
                            .getBytes(StandardCharsets.UTF_8));
        });
        server.serveHtml("/sonstiges/zustimmung/auswahl.html", """
                <html><body><script>
                  var xhr = new XMLHttpRequest();
                  xhr.onload = function() { location.replace('/'); };
                  xhr.open('GET', '/abo/setconsentcookie.php');
                  xhr.send();
                </script></body></html>
                """);

        try (PageSession session = engine.loadPageSession(
                server.url("/sonstiges/zustimmung/auswahl.html"), PageUpdateListener.NO_OP)) {
            long generation = session.navigationGeneration();
            session.awaitResources();
            assertTrue("Navigation nach XHR erwartet",
                    session.awaitNavigation(generation, 5_000));

            assertEquals("Echte Seite", session.document().getTitle());
            String sent = cookieHeader.get();
            assertTrue("Cookie-Header fehlt: " + sent,
                    sent != null && sent.contains("golem_consent20=ok"));
        }
    }

    @Test
    public void documentSetCookieIsCapturedAndVisibleToScripts() throws Exception {
        server.on("/", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "sitzung=abc123; Path=/");
            LocalTestServer.respond(exchange, 200, "text/html; charset=UTF-8", """
                    <html><body><output id="out"></output><script>
                      document.getElementById('out').textContent = document.cookie;
                    </script></body></html>
                    """.getBytes(StandardCharsets.UTF_8));
        });

        try (PageSession session = engine.loadPageSession(
                server.url("/"), PageUpdateListener.NO_OP)) {
            session.awaitResources();

            assertEquals("sitzung=abc123",
                    session.cookies().cookiesForScript(URI.create(server.url("/"))));
            assertTrue(session.cookies().receivedCookieHeaders().stream()
                    .anyMatch(record -> JsCookieStore.SOURCE_DOCUMENT.equals(record.source())
                            && record.header().startsWith("sitzung=abc123")));
            assertEquals("sitzung=abc123",
                    session.document().getElementById("out").getTextContent());
        }
    }

    @Test
    public void cookiesSurviveMultipleNavigationsWithinOneSession() throws Exception {
        server.on("/set", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "bleib=da; Path=/");
            LocalTestServer.respond(exchange, 200, "text/html; charset=UTF-8", new byte[0]);
        });
        server.serveHtml("/zwischen", """
                <html><body><script>
                  var xhr = new XMLHttpRequest();
                  xhr.onload = function() { location.replace('/ende'); };
                  xhr.open('GET', '/set');
                  xhr.send();
                </script></body></html>
                """);
        AtomicReference<String> cookieHeader = new AtomicReference<>();
        server.on("/ende", exchange -> {
            cookieHeader.set(exchange.getRequestHeaders().getFirst("Cookie"));
            LocalTestServer.respond(exchange, 200, "text/html; charset=UTF-8",
                    "<html><head><title>Ende</title></head><body>ende</body></html>"
                            .getBytes(StandardCharsets.UTF_8));
        });

        try (PageSession session = engine.loadPageSession(
                server.url("/zwischen"), PageUpdateListener.NO_OP)) {
            long generation = session.navigationGeneration();
            session.awaitResources();
            assertTrue("Navigation nach XHR erwartet",
                    session.awaitNavigation(generation, 5_000));

            assertEquals(server.url("/ende"), session.document().getUrl());
            String sent = cookieHeader.get();
            assertTrue("Cookie-Header fehlt: " + sent, sent != null && sent.contains("bleib=da"));
        }
    }
}

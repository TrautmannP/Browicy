package com.browicy.engine;

import com.browicy.engine.dom.Document;
import com.browicy.engine.net.NetworkRequestEvent;
import com.browicy.engine.net.NetworkResourceType;
import com.browicy.engine.net.LocalTestServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FetchIntegrationTest {

    private LocalTestServer server;
    private final BrowicyEngine engine = new BrowicyEngine();
    private final List<NetworkRequestEvent> events = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() throws IOException {
        server = new LocalTestServer();
        engine.addRequestObserver(events::add);
    }

    @After
    public void tearDown() {
        engine.close();
        server.close();
    }

    @Test
    public void inlineScriptFetchesSameOriginJsonAndUpdatesDom() throws Exception {
        server.serveText("/api/status.json", "application/json; charset=utf-8",
                "{\"nachricht\":\"vom Server\"}");
        server.serveHtml("/", """
                <html><body>
                  <output id="out"></output>
                  <script>
                    fetch('/api/status.json')
                      .then(r => r.json())
                      .then(daten => {
                        document.getElementById('out').textContent = daten.nachricht;
                      })
                      .catch(fehler => {
                        document.getElementById('out').textContent = 'Fehler: ' + fehler;
                      });
                  </script>
                </body></html>
                """);

        try (PageSession session = engine.loadPageSession(server.url("/"), PageUpdateListener.NO_OP)) {
            session.awaitResources();
            awaitOutput(session, "vom Server");
        }

        assertTrue(events.stream().anyMatch(event ->
                event instanceof NetworkRequestEvent.Loaded loaded
                        && loaded.resourceType() == NetworkResourceType.FETCH));
    }

    @Test
    public void inlineScriptPostsJsonBodyAndCustomHeader() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> requestHeader = new AtomicReference<>();
        server.on("/api/eintrag", exchange -> {
            method.set(exchange.getRequestMethod());
            requestHeader.set(exchange.getRequestHeaders().getFirst("X-Anfrage"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            LocalTestServer.respond(exchange, 201, "application/json; charset=utf-8",
                    "{\"id\":7}".getBytes(StandardCharsets.UTF_8));
        });
        server.serveHtml("/", """
                <html><body>
                  <output id="out"></output>
                  <script>
                    fetch('/api/eintrag', {
                      method: 'POST',
                      headers: {
                        'Content-Type': 'application/json; charset=utf-8',
                        'X-Anfrage': 'integration'
                      },
                      body: JSON.stringify({name: 'Grüße'})
                    }).then(r => r.json()).then(daten => {
                      document.getElementById('out').textContent = daten.id;
                    });
                  </script>
                </body></html>
                """);

        try (PageSession session = engine.loadPageSession(
                server.url("/"), PageUpdateListener.NO_OP)) {
            session.awaitResources();
            awaitOutput(session, "7");
        }

        assertEquals("POST", method.get());
        assertEquals("integration", requestHeader.get());
        assertEquals("{\"name\":\"Grüße\"}", body.get());
    }

    @Test
    public void notFoundResponsesReachTheScriptAsRegularResponses() throws Exception {
        server.on("/fehlt", exchange ->
                LocalTestServer.respond(exchange, 404, "text/plain", new byte[0]));
        server.serveHtml("/", """
                <html><body>
                  <output id="out"></output>
                  <script>
                    fetch('/fehlt').then(r => {
                      document.getElementById('out').textContent = r.ok + '|' + r.status;
                    });
                  </script>
                </body></html>
                """);

        try (PageSession session = engine.loadPageSession(server.url("/"), PageUpdateListener.NO_OP)) {
            session.awaitResources();
            awaitOutput(session, "false|404");
        }
    }

    @Test
    public void crossOriginFetchWithoutCorsHeaderRejects() throws Exception {
        try (LocalTestServer otherOrigin = new LocalTestServer()) {
            otherOrigin.serveText("/geheim", "text/plain", "intern");
            server.serveHtml("/", """
                    <html><body>
                      <output id="out"></output>
                      <script>
                        fetch('%s')
                          .then(r => { document.getElementById('out').textContent = 'aufgelöst'; })
                          .catch(fehler => {
                            document.getElementById('out').textContent =
                                'blockiert:' + (fehler instanceof TypeError);
                          });
                      </script>
                    </body></html>
                    """.formatted(otherOrigin.url("/geheim")));

            try (PageSession session = engine.loadPageSession(
                    server.url("/"), PageUpdateListener.NO_OP)) {
                session.awaitResources();
                awaitOutput(session, "blockiert:true");
            }
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

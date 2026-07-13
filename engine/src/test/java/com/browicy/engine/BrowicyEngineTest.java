package com.browicy.engine;

import com.browicy.engine.dom.Document;
import com.browicy.engine.net.LocalTestServer;
import com.browicy.engine.net.NetworkRequestEvent;
import com.browicy.engine.net.NetworkResourceType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BrowicyEngineTest {

    private LocalTestServer server;
    private final BrowicyEngine engine = new BrowicyEngine();

    @Before
    public void startServer() throws IOException {
        server = new LocalTestServer();
    }

    @After
    public void tearDown() {
        engine.close();
        server.close();
    }

    @Test
    public void loadsAndParsesPageFromNetwork() {
        server.serveHtml("/", """
                <html>
                  <head><title>Netzwerk-Test</title></head>
                  <body><h1>Es funktioniert</h1><p>Über HTTP geladen.</p></body>
                </html>
                """);

        Document document = engine.loadPage(server.url("/"));

        assertEquals("Netzwerk-Test", document.getTitle());
        assertTrue(document.getBody().getTextContent().contains("Es funktioniert"));
    }

    @Test
    public void followsRedirectsLikeGoogleDotCom() {
        server.on("/", exchange -> {
            exchange.getResponseHeaders().set("Location", "/www");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });
        server.serveHtml("/www", "<html><head><title>Ziel</title></head><body>Da</body></html>");

        Document document = engine.loadPage(server.url("/"));

        assertEquals("Ziel", document.getTitle());
        assertEquals(server.url("/www"), document.getUrl());
    }

    @Test
    public void rendersErrorPageWhenServerUnreachable() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }

        Document document = engine.loadPage("http://127.0.0.1:" + freePort + "/");

        assertEquals("Seite konnte nicht geladen werden", document.getTitle());
    }

    @Test
    public void rendersErrorPageForInvalidUrls() {
        Document document = engine.loadPage("http://also wirklich keine url");

        assertEquals("Seite konnte nicht geladen werden", document.getTitle());
    }

    @Test
    public void executesInlineScriptsWhenLoadingPages() {
        server.serveHtml("/js", """
                <html>
                  <head><title>Vor JavaScript</title></head>
                  <body>
                    <h1 id="ueberschrift">Statischer Text</h1>
                    <script>
                      document.getElementById('ueberschrift').textContent = 'Von JavaScript gerendert';
                      document.title = 'Nach JavaScript';
                    </script>
                  </body>
                </html>
                """);

        Document document = engine.loadPage(server.url("/js"));

        assertEquals("Nach JavaScript", document.getTitle());
        assertTrue(document.getBody().getTextContent().contains("Von JavaScript gerendert"));
    }

    @Test
    public void brokenScriptsDoNotPreventPageRendering() {
        server.serveHtml("/kaputt", """
                <html>
                  <head><title>Robuste Seite</title></head>
                  <body>
                    <p>Sichtbarer Inhalt</p>
                    <script>das ist kein gültiges JavaScript(((</script>
                  </body>
                </html>
                """);

        Document document = engine.loadPage(server.url("/kaputt"));

        assertEquals("Robuste Seite", document.getTitle());
        assertTrue(document.getBody().getTextContent().contains("Sichtbarer Inhalt"));
    }

    @Test
    public void servesHelloWorldForAboutUrls() {
        Document document = engine.loadPage("about:home");

        assertTrue(document.getTitle().contains("Hallo Welt"));
    }

    @Test
    public void loadsExternalStylesheetsAgainstBaseUrl() {
        server.serveHtml("/pages/index.html", """
                <html><head>
                  <base href="/assets/">
                  <link rel="stylesheet" href="theme.css">
                </head><body><p id="message">Text</p></body></html>
                """);
        server.serveText("/assets/theme.css", "text/css; charset=utf-8",
                "#message { color: red; }");

        Document document = engine.loadPage(server.url("/pages/index.html"));

        assertEquals("red", document.getElementById("message")
                .getComputedStyles().get("color"));
    }

    @Test
    public void stylesheetSourceOrderDoesNotDependOnDownloadOrder() {
        server.serveHtml("/styles", """
                <html><head>
                  <link rel="stylesheet" href="/slow.css">
                  <link rel="stylesheet" href="/fast.css">
                </head><body><p id="message">Text</p></body></html>
                """);
        server.on("/slow.css", exchange -> {
            try {
                Thread.sleep(120);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            LocalTestServer.respond(exchange, 200, "text/css; charset=utf-8",
                    "#message { color: red; }".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        });
        server.serveText("/fast.css", "text/css; charset=utf-8",
                "#message { color: blue; }");

        Document document = engine.loadPage(server.url("/styles"));

        assertEquals("blue", document.getElementById("message")
                .getComputedStyles().get("color"));
    }

    @Test
    public void externalAndInlineScriptsShareOneGlobalContextInTreeOrder() {
        server.serveHtml("/scripts", """
                <html><body>
                  <p id="message">vorher</p>
                  <script src="/first.js"></script>
                  <script>
                    sharedValue += '-inline';
                    document.getElementById('message').textContent = sharedValue;
                  </script>
                </body></html>
                """);
        server.serveText("/first.js", "application/javascript; charset=utf-8",
                "globalThis.sharedValue = 'external';");

        Document document = engine.loadPage(server.url("/scripts"));

        assertEquals("external-inline", document.getElementById("message").getTextContent());
    }

    @Test
    public void failedSubresourcesDoNotPreventRemainingPageProcessing() {
        server.serveHtml("/robust", """
                <html><head>
                  <link rel="stylesheet" href="/missing.css">
                </head><body>
                  <p id="message">vorher</p>
                  <script src="/missing.js"></script>
                  <script>document.getElementById('message').textContent = 'weiter';</script>
                </body></html>
                """);
        server.on("/missing.css", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.on("/missing.js", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        Document document = engine.loadPage(server.url("/robust"));

        assertEquals("weiter", document.getElementById("message").getTextContent());
    }

    @Test
    public void reportsDocumentCssAndScriptAsSeparateNetworkRequests() {
        List<NetworkRequestEvent> events = new CopyOnWriteArrayList<>();
        try (BrowicyEngine observedEngine = new BrowicyEngine()) {
            observedEngine.addRequestObserver(events::add);
            server.serveHtml("/network", """
                    <html><head><link rel="stylesheet" href="/theme.css"></head>
                    <body><script src="/app.js"></script></body></html>
                    """);
            server.serveText("/theme.css", "text/css", "body { color: black; }");
            server.serveText("/app.js", "application/javascript",
                    "globalThis.ok = true;");

            observedEngine.loadPage(server.url("/network"));

            List<NetworkResourceType> startedTypes = events.stream()
                    .filter(NetworkRequestEvent.Started.class::isInstance)
                    .map(NetworkRequestEvent.Started.class::cast)
                    .map(NetworkRequestEvent.Started::resourceType)
                    .toList();
            assertTrue(startedTypes.contains(NetworkResourceType.DOCUMENT));
            assertTrue(startedTypes.contains(NetworkResourceType.STYLESHEET));
            assertTrue(startedTypes.contains(NetworkResourceType.SCRIPT));
        }
    }

    @Test
    public void bodyStylesheetTriggersIncrementalStyleUpdateAfterInitialPageLoad() throws Exception {
        CountDownLatch releaseStyle = new CountDownLatch(1);
        server.serveHtml("/incremental", """
                <html><head><title>Inkrementell</title></head><body>
                  <p id="message">Text</p>
                  <link rel="stylesheet" href="/late.css">
                </body></html>
                """);
        server.on("/late.css", exchange -> {
            try {
                releaseStyle.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            LocalTestServer.respond(exchange, 200, "text/css; charset=utf-8",
                    "#message { color: purple; }"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        });
        List<PageUpdate> updates = new CopyOnWriteArrayList<>();

        PageSession session = engine.loadPageSession(server.url("/incremental"), updates::add);

        assertNull(session.document().getElementById("message")
                .getComputedStyles().get("color"));
        releaseStyle.countDown();
        session.awaitResources();

        assertEquals("purple", session.document().getElementById("message")
                .getComputedStyles().get("color"));
        assertEquals(1, updates.size());
        assertTrue(updates.getFirst() instanceof PageUpdate.StylesChanged);
    }

}

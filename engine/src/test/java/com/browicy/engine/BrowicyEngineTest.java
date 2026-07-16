package com.browicy.engine;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.DocumentReadyState;
import com.browicy.engine.dom.Event;
import com.browicy.engine.net.LocalTestServer;
import com.browicy.engine.net.NetworkRequestEvent;
import com.browicy.engine.net.NetworkResourceType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void loadsImagesInParallelAndExposesTheirBinaryPayloadInTheSession() {
        byte[] imageBytes = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47};
        server.serveHtml("/images", """
                <html><body><img id="hero" src="/hero.png"></body></html>
                """);
        server.on("/hero.png", exchange -> LocalTestServer.respond(
                exchange, 200, "image/png", imageBytes));

        try (PageSession session = engine.loadPageSession(
                server.url("/images"), PageUpdateListener.NO_OP)) {
            session.awaitResources();

            assertTrue(session.images().find(
                    session.document().getElementById("hero")).isPresent());
            assertEquals(NetworkResourceType.IMAGE, session.images().find(
                    session.document().getElementById("hero")).orElseThrow().resourceType());
        }
    }

    @Test
    public void loadsCssBackgroundImagesUsedByGeneratedPseudoElements() {
        byte[] imageBytes = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47};
        AtomicInteger requests = new AtomicInteger();
        server.serveHtml("/pseudo-background", """
                <html><head><style>
                  #hero::after {
                    content:'';
                    background:url('/pseudo.png') no-repeat center/100% auto;
                  }
                </style></head><body><div id="hero"></div></body></html>
                """);
        server.on("/pseudo.png", exchange -> {
            requests.incrementAndGet();
            LocalTestServer.respond(exchange, 200, "image/png", imageBytes);
        });

        try (PageSession session = engine.loadPageSession(
                server.url("/pseudo-background"), PageUpdateListener.NO_OP)) {
            session.awaitResources();
            assertTrue(session.images().find(java.net.URI.create(
                    server.url("/pseudo.png"))).isPresent());
            assertEquals(1, requests.get());
        }
    }

    @Test
    public void loadsAndExecutesDynamicallyInsertedScriptsBeforeCompletingResources() {
        AtomicInteger requests = new AtomicInteger();
        server.serveHtml("/dynamic-script", """
                <html><head><title>Dynamic</title></head><body data-state="initial"><script>
                  const script = document.createElement('script');
                  script.src = '/dynamic.js';
                  script.onload = script.onreadystatechange = function () {
                    if (!this.readyState || /loaded|complete/.test(this.readyState)) {
                      document.body.setAttribute('data-state', window.dynamicValue);
                    }
                  };
                  document.head.appendChild(script);
                </script></body></html>
                """);
        server.on("/dynamic.js", exchange -> {
            requests.incrementAndGet();
            LocalTestServer.respond(exchange, 200, "application/javascript",
                    "window.dynamicValue = 'loaded';".getBytes(StandardCharsets.UTF_8));
        });

        try (PageSession session = engine.loadPageSession(
                server.url("/dynamic-script"), PageUpdateListener.NO_OP)) {
            session.awaitResources();
            assertEquals(
                    "requests=" + requests.get() + ", errors="
                            + session.runtime().snapshot().errors(),
                    "loaded", session.document().getBody().getAttribute("data-state"));
            assertEquals(1, requests.get());
            assertTrue(session.runtime().snapshot().errors().isEmpty());
        }
    }

    @Test
    public void rendersInitialContentWhileBlockingScriptIsStillLoading() throws Exception {
        CountDownLatch scriptStarted = new CountDownLatch(1);
        CountDownLatch releaseScript = new CountDownLatch(1);
        CountDownLatch imageStarted = new CountDownLatch(1);
        server.serveHtml("/priorities", """
                <html><body>
                  <img src="/background.png">
                  <script src="/blocking.js"></script>
                  <p id="message">Sichtbarer Inhalt</p>
                </body></html>
                """);
        server.on("/blocking.js", exchange -> {
            scriptStarted.countDown();
            try {
                releaseScript.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            LocalTestServer.respond(exchange, 200, "application/javascript", new byte[0]);
        });
        server.on("/background.png", exchange -> {
            imageStarted.countDown();
            LocalTestServer.respond(exchange, 200, "image/png",
                    new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47});
        });

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            var sessionFuture = executor.submit(() -> engine.loadPageSession(
                    server.url("/priorities"), PageUpdateListener.NO_OP));
            try {
                assertTrue("Das blockierende Skript wurde nicht angefordert",
                        scriptStarted.await(2, TimeUnit.SECONDS));
                try (PageSession session = sessionFuture.get(2, TimeUnit.SECONDS)) {
                    assertEquals("Sichtbarer Inhalt", session.document()
                            .getElementById("message").getTextContent());
                    assertTrue("Der First Render sollte in der Metrik vermerkt sein",
                            session.progress().firstRenderMillis() >= 0);
                    assertTrue("Bild-Downloads sollten mit dem initialen Render anlaufen",
                            imageStarted.await(2, TimeUnit.SECONDS));
                    assertFalse("Skripte laufen noch – die Ressourcen dürfen nicht fertig sein",
                            session.resourcesLoaded().isDone());
                    releaseScript.countDown();
                    session.awaitResources();
                }
            } finally {
                releaseScript.countDown();
            }
        }
    }

    @Test
    public void prefetchesExternalScriptsInParallelButExecutesThemInOrder() throws Exception {
        CountDownLatch bothRequested = new CountDownLatch(2);
        server.serveHtml("/parallel-scripts", """
                <html><body><p id="message">wartet</p>
                  <script src="/first.js"></script>
                  <script src="/second.js"></script>
                </body></html>
                """);
        server.on("/first.js", exchange -> {
            bothRequested.countDown();
            awaitQuietly(bothRequested);
            LocalTestServer.respond(exchange, 200, "application/javascript",
                    "window.reihenfolge = 'erstes';"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        });
        server.on("/second.js", exchange -> {
            bothRequested.countDown();
            awaitQuietly(bothRequested);
            LocalTestServer.respond(exchange, 200, "application/javascript",
                    ("document.getElementById('message').textContent ="
                            + " window.reihenfolge + '-dann-zweites';")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        });

        try (PageSession session = engine.loadPageSession(
                server.url("/parallel-scripts"), PageUpdateListener.NO_OP)) {
            session.awaitResources();
            assertEquals("erstes-dann-zweites", session.document()
                    .getElementById("message").getTextContent());
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void returnsBeforeSlowImageFinishesAndRendersItIncrementally() throws Exception {
        CountDownLatch imageStarted = new CountDownLatch(1);
        CountDownLatch releaseImage = new CountDownLatch(1);
        List<PageUpdate> updates = new CopyOnWriteArrayList<>();
        server.serveHtml("/progressive-image", """
                <html><body>
                  <h1 id="heading">Schon sichtbar</h1>
                  <img id="slow" src="/slow.png">
                </body></html>
                """);
        server.on("/slow.png", exchange -> {
            imageStarted.countDown();
            try {
                releaseImage.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            LocalTestServer.respond(exchange, 200, "image/png",
                    new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47});
        });

        try (PageSession session = engine.loadPageSession(
                server.url("/progressive-image"), updates::add)) {
            assertEquals("Schon sichtbar", session.document()
                    .getElementById("heading").getTextContent());
            assertTrue("Bild-Download wurde nicht asynchron gestartet",
                    imageStarted.await(2, TimeUnit.SECONDS));
            assertFalse("Die Session darf nicht auf das Bild warten",
                    session.resourcesLoaded().isDone());
            assertTrue(session.images().find(
                    session.document().getElementById("slow")).isEmpty());

            releaseImage.countDown();
            session.awaitResources();

            assertTrue(session.images().find(
                    session.document().getElementById("slow")).isPresent());
            assertTrue(updates.stream().anyMatch(update ->
                    update.invalidation() == InvalidationType.RENDER_TREE));
        } finally {
            releaseImage.countDown();
        }
    }

    @Test
    public void fetchesRepeatedImageUrlsOnlyOnceButRegistersEveryElement() {
        byte[] imageBytes = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47};
        AtomicInteger requests = new AtomicInteger();
        server.serveHtml("/wiederholt", """
                <html><body>
                  <img id="erstes" src="/gleich.png">
                  <img id="zweites" src="/gleich.png">
                </body></html>
                """);
        server.on("/gleich.png", exchange -> {
            requests.incrementAndGet();
            LocalTestServer.respond(exchange, 200, "image/png", imageBytes);
        });

        try (PageSession session = engine.loadPageSession(
                server.url("/wiederholt"), PageUpdateListener.NO_OP)) {
            session.awaitResources();

            assertEquals(1, requests.get());
            assertTrue(session.images().find(
                    session.document().getElementById("erstes")).isPresent());
            assertTrue(session.images().find(
                    session.document().getElementById("zweites")).isPresent());
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

    @Test
    public void persistentSessionBatchesClickMutationsAndRecalculatesStyles() {
        server.serveHtml("/interactive", """
                <html><head>
                  <style>body { color: black; } body.dark { color: white; }</style>
                </head><body>
                  <button id="button">Öffnen</button>
                  <p id="message">1</p>
                  <script>
                    window.counter = 1;
                    document.addEventListener('DOMContentLoaded', () => {
                      document.body.setAttribute('data-dom-ready', document.readyState);
                    });
                    document.body.addEventListener('load', () => {
                      document.body.setAttribute('data-load-ready', document.readyState);
                    });
                    document.getElementById('button').addEventListener('click', () => {
                      counter++;
                      document.body.classList.add('dark');
                      document.body.setAttribute('aria-expanded', 'true');
                      for (let i = 0; i < 10; i++) {
                        document.body.setAttribute('data-change-' + i, String(i));
                      }
                      document.getElementById('message').textContent = String(counter);
                    });
                  </script>
                </body></html>
                """);
        List<PageUpdate> updates = new CopyOnWriteArrayList<>();

        try (PageSession session = engine.loadPageSession(server.url("/interactive"), updates::add)) {
            session.awaitResources();
            assertEquals(DocumentReadyState.COMPLETE, session.document().getReadyState());
            assertEquals("interactive", session.document().getBody()
                    .getAttribute("data-dom-ready"));
            assertEquals("complete", session.document().getBody()
                    .getAttribute("data-load-ready"));
            updates.clear();

            session.runtime().submitEvent(
                    session.document().getElementById("button"),
                    new Event("click", true, true)).join();

            assertEquals("2", session.document().getElementById("message").getTextContent());
            assertEquals("white", session.document().getBody()
                    .getComputedStyles().get("color"));
            assertEquals(1, updates.size());
            assertEquals(InvalidationType.STYLE, updates.getFirst().invalidation());
            assertTrue(updates.getFirst().mutations().size() >= 13);
        }
    }

    @Test
    public void cssomRuleMutationRequestsStyleInvalidationAndRestylesDocument() {
        server.serveHtml("/cssom", """
                <html><head><style id="theme">#message { color: red; }</style></head>
                <body><p id="message">Text</p></body></html>
                """);
        List<PageUpdate> updates = new CopyOnWriteArrayList<>();

        try (PageSession session = engine.loadPageSession(server.url("/cssom"), updates::add)) {
            assertEquals("red", session.document().getElementById("message")
                    .getComputedStyles().get("color"));

            session.runtime().execute(new com.browicy.engine.js.JavaScriptSource("""
                    const sheet = document.getElementById('theme').sheet;
                    sheet.insertRule('#message { color: blue; }', sheet.cssRules.length);
                    """, null, "cssom-test.js"));

            assertEquals("blue", session.document().getElementById("message")
                    .getComputedStyles().get("color"));
            assertEquals(1, updates.size());
            assertEquals(InvalidationType.STYLE, updates.getFirst().invalidation());
        }
    }

    @Test
    public void timerScheduledFromLoadUpdatesPageAfterInitialLoad() throws Exception {
        server.serveHtml("/timer", """
                <html><body><p id="message">Warte</p><script>
                  document.body.addEventListener('load', () => {
                    setTimeout(() => {
                      document.getElementById('message').textContent = 'Fertig';
                    }, 100);
                  });
                </script></body></html>
                """);
        CountDownLatch updated = new CountDownLatch(1);

        try (PageSession session = engine.loadPageSession(
                server.url("/timer"), update -> updated.countDown())) {
            assertEquals("Warte", session.document().getElementById("message").getTextContent());
            assertTrue("Timer-Update wurde nicht veröffentlicht",
                    updated.await(2, TimeUnit.SECONDS));
            session.runtime().awaitIdle();
            assertEquals("Fertig", session.document().getElementById("message").getTextContent());
        }
    }

    @Test
    public void loadsStaticDefaultImportsFromExternalEsModules() {
        server.serveHtml("/modules", """
                <html><body><p id="message">before</p>
                  <script type="module" src="/app.js"></script>
                </body></html>
                """);
        server.serveText("/app.js", "text/javascript", """
                import data from './data.js';
                onload = () => document.getElementById('message').textContent = data.message;
                """);
        server.serveText("/data.js", "text/javascript", """
                export default { message: 'module-loaded' };
                """);

        Document document = engine.loadPage(server.url("/modules"));

        assertEquals("module-loaded", document.getElementById("message").getTextContent());
    }

}

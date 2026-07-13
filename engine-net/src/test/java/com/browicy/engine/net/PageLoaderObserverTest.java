package com.browicy.engine.net;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PageLoaderObserverTest {

    private static final long WAIT_SECONDS = 5;

    private LocalTestServer server;
    private final PageLoader loader = new PageLoader();
    private final List<PageLoadEvent> events = new CopyOnWriteArrayList<>();

    @Before
    public void startServer() throws IOException {
        server = new LocalTestServer();
        loader.addObserver(events::add);
    }

    @After
    public void stopServer() {
        server.close();
    }

    @Test(timeout = 10_000)
    public void syncLoadEmitsStartedAndLoaded() throws IOException {
        server.serveHtml("/", "<html><body>Inhalt</body></html>");

        PageLoader.Page page = loader.load(server.url("/"));

        assertEquals(2, events.size());
        PageLoadEvent.Started started = (PageLoadEvent.Started) events.get(0);
        PageLoadEvent.Loaded loaded = (PageLoadEvent.Loaded) events.get(1);
        assertEquals(server.url("/"), started.url());
        assertEquals(started.loadId(), loaded.loadId());
        assertEquals(page, loaded.page());
    }

    @Test(timeout = 10_000)
    public void redirectsEmitRedirectedEvents() throws IOException {
        server.on("/alt", exchange -> {
            exchange.getResponseHeaders().set("Location", "/ziel");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });
        server.serveHtml("/ziel", "<html><body>Angekommen</body></html>");

        loader.load(server.url("/alt"));

        assertEquals(3, events.size());
        PageLoadEvent.Redirected redirected = (PageLoadEvent.Redirected) events.get(1);
        assertEquals(URI.create(server.url("/alt")), redirected.from());
        assertEquals(URI.create(server.url("/ziel")), redirected.to());
        assertEquals(301, redirected.statusCode());
        assertTrue(events.get(2) instanceof PageLoadEvent.Loaded);
    }

    @Test(timeout = 10_000)
    public void failureEmitsFailedEvent() {
        assertThrows(IOException.class, () -> loader.load("ftp://beispiel.invalid/"));

        assertEquals(2, events.size());
        PageLoadEvent.Failed failed = (PageLoadEvent.Failed) events.get(1);
        assertEquals("ftp://beispiel.invalid/", failed.url());
        assertTrue(failed.cause() instanceof IOException);
    }

    @Test(timeout = 10_000)
    public void cancelEmitsCancelledEvent() throws Exception {
        CountDownLatch requestArrived = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        server.on("/langsam", exchange -> {
            requestArrived.countDown();
            try {
                releaseResponse.await(WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
            LocalTestServer.respond(exchange, 200, "text/html", new byte[0]);
        });

        PageLoader ownLoader = new PageLoader(new HttpClient(), Executors.newSingleThreadExecutor());
        ownLoader.addObserver(events::add);
        PageLoad load = ownLoader.loadAsync(server.url("/langsam"));
        assertTrue(requestArrived.await(WAIT_SECONDS, TimeUnit.SECONDS));

        load.cancel();

        releaseResponse.countDown();
        ownLoader.close();
        assertEquals(2, events.size());
        PageLoadEvent.Cancelled cancelled = (PageLoadEvent.Cancelled) events.get(1);
        assertEquals(load.id(), cancelled.loadId());
    }

    @Test(timeout = 10_000)
    public void observerExceptionsDoNotBreakTheLoad() throws IOException {
        server.serveHtml("/", "<html><body>Inhalt</body></html>");
        PageLoader ownLoader = new PageLoader();
        ownLoader.addObserver(event -> {
            throw new IllegalStateException("Beobachter kaputt");
        });
        ownLoader.addObserver(events::add);

        PageLoader.Page page = ownLoader.load(server.url("/"));

        assertEquals(200, page.statusCode());
        assertEquals(2, events.size());
    }

    @Test(timeout = 10_000)
    public void removedObserverReceivesNoEvents() throws IOException {
        server.serveHtml("/", "<html><body>Inhalt</body></html>");
        PageLoader ownLoader = new PageLoader();
        PageLoadObserver observer = events::add;
        ownLoader.addObserver(observer);
        ownLoader.removeObserver(observer);

        ownLoader.load(server.url("/"));

        assertTrue(events.isEmpty());
    }
}

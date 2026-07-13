package com.browicy.engine.net;

import com.sun.net.httpserver.HttpHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Tests für Zustand, Abbruch und Parallelität asynchroner Ladevorgänge. */
public class PageLoadTest {

    private static final long WAIT_SECONDS = 5;

    private LocalTestServer server;
    private final PageLoader loader = new PageLoader();

    @Before
    public void startServer() throws IOException {
        server = new LocalTestServer();
    }

    @After
    public void stopServer() {
        server.close();
    }

    @Test(timeout = 10_000)
    public void loadAsyncLoadsPageAndNotifiesListener() throws Exception {
        server.serveHtml("/", "<html><body>Inhalt</body></html>");
        PageLoad load = loader.loadAsync(server.url("/"));

        CountDownLatch notified = new CountDownLatch(1);
        AtomicReference<PageLoad.State> stateInListener = new AtomicReference<>();
        load.onDone(l -> {
            stateInListener.set(l.state());
            notified.countDown();
        });

        PageLoader.Page page = load.await();

        assertEquals(200, page.statusCode());
        assertTrue(page.html().contains("Inhalt"));
        assertEquals(PageLoad.State.LOADED, load.state());
        assertTrue(load.isDone());
        assertEquals(page, load.page().orElseThrow());
        assertTrue(load.failure().isEmpty());
        assertTrue(notified.await(WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(PageLoad.State.LOADED, stateInListener.get());
    }

    @Test(timeout = 10_000)
    public void reportsLoadingWhileRequestIsInFlight() throws Exception {
        CountDownLatch requestArrived = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        server.on("/langsam", exchange -> {
            requestArrived.countDown();
            awaitQuietly(releaseResponse);
            LocalTestServer.respond(exchange, 200, "text/html",
                    "<html><body>fertig</body></html>".getBytes(StandardCharsets.UTF_8));
        });

        PageLoad load = loader.loadAsync(server.url("/langsam"));
        assertTrue(requestArrived.await(WAIT_SECONDS, TimeUnit.SECONDS));

        assertEquals(PageLoad.State.LOADING, load.state());
        assertFalse(load.isDone());
        assertTrue(load.page().isEmpty());

        releaseResponse.countDown();
        load.await();
        assertEquals(PageLoad.State.LOADED, load.state());
    }

    @Test(timeout = 10_000)
    public void failedLoadExposesFailure() throws Exception {
        // ftp wird vom HttpClient abgelehnt — der Fehler muss im Zustand landen
        PageLoad load = loader.loadAsync("ftp://beispiel.invalid/");

        CountDownLatch done = new CountDownLatch(1);
        load.onDone(l -> done.countDown());
        assertTrue(done.await(WAIT_SECONDS, TimeUnit.SECONDS));

        assertEquals(PageLoad.State.FAILED, load.state());
        assertTrue(load.failure().orElseThrow() instanceof IOException);
        assertTrue(load.page().isEmpty());
        assertThrows(IOException.class, load::await);
    }

    @Test(timeout = 10_000)
    public void invalidInputFailsInsteadOfThrowing() throws Exception {
        PageLoad load = loader.loadAsync("   ");

        CountDownLatch done = new CountDownLatch(1);
        load.onDone(l -> done.countDown());
        assertTrue(done.await(WAIT_SECONDS, TimeUnit.SECONDS));

        assertEquals(PageLoad.State.FAILED, load.state());
        assertTrue(load.failure().orElseThrow() instanceof IllegalArgumentException);
        assertThrows(IllegalArgumentException.class, load::await);
    }

    @Test(timeout = 10_000)
    public void cancelSetsStateImmediatelyAndSkipsFurtherRedirects() throws Exception {
        CountDownLatch requestArrived = new CountDownLatch(1);
        CountDownLatch releaseRedirect = new CountDownLatch(1);
        AtomicBoolean redirectFollowed = new AtomicBoolean();
        server.on("/start", exchange -> {
            requestArrived.countDown();
            awaitQuietly(releaseRedirect);
            exchange.getResponseHeaders().set("Location", "/ziel");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.on("/ziel", exchange -> {
            redirectFollowed.set(true);
            LocalTestServer.respond(exchange, 200, "text/html", new byte[0]);
        });

        PageLoader ownLoader = new PageLoader(new HttpClient(), Executors.newSingleThreadExecutor());
        PageLoad load = ownLoader.loadAsync(server.url("/start"));
        assertTrue(requestArrived.await(WAIT_SECONDS, TimeUnit.SECONDS));

        load.cancel();

        assertEquals(PageLoad.State.CANCELLED, load.state());
        assertTrue(load.isDone());
        assertTrue(load.isCancelled());
        assertThrows(CancellationException.class, load::await);

        // Lade-Thread auslaufen lassen: close() wartet auf laufende Vorgänge —
        // erst danach ist belegt, dass die Weiterleitung nicht mehr verfolgt wurde
        releaseRedirect.countDown();
        ownLoader.close();
        assertFalse(redirectFollowed.get());
    }

    @Test(timeout = 10_000)
    public void cancelAfterCompletionChangesNothing() throws Exception {
        server.serveHtml("/", "<html><body>Inhalt</body></html>");
        PageLoad load = loader.loadAsync(server.url("/"));
        load.await();

        load.cancel();

        assertEquals(PageLoad.State.LOADED, load.state());
        assertTrue(load.page().isPresent());
    }

    @Test(timeout = 10_000)
    public void loadsPagesInParallel() throws Exception {
        // Beide Requests müssen gleichzeitig unterwegs sein, sonst löst die Barriere nie aus
        CyclicBarrier bothInFlight = new CyclicBarrier(2);
        HttpHandler handler = exchange -> {
            try {
                bothInFlight.await(WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IOException("Zweiter paralleler Request kam nicht an", e);
            }
            LocalTestServer.respond(exchange, 200, "text/html",
                    "<html><body>ok</body></html>".getBytes(StandardCharsets.UTF_8));
        };
        server.on("/a", handler);
        server.on("/b", handler);

        PageLoad first = loader.loadAsync(server.url("/a"));
        PageLoad second = loader.loadAsync(server.url("/b"));

        assertEquals(200, first.await().statusCode());
        assertEquals(200, second.await().statusCode());
        assertEquals(PageLoad.State.LOADED, first.state());
        assertEquals(PageLoad.State.LOADED, second.state());
    }

    private static void awaitQuietly(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IOException("Test-Latch wurde nicht ausgelöst");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }
}

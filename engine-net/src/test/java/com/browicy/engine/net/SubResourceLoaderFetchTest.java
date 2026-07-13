package com.browicy.engine.net;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SubResourceLoaderFetchTest {

    private LocalTestServer server;
    private SubResourceLoader loader;
    private final List<NetworkRequestEvent> events = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() throws IOException {
        server = new LocalTestServer();
        loader = new SubResourceLoader();
        loader.addObserver(events::add);
    }

    @After
    public void tearDown() {
        loader.close();
        server.close();
    }

    @Test
    public void deliversBodyStatusAndHeaders() throws Exception {
        server.on("/data.json", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("X-Custom", "wert");
            LocalTestServer.respond(exchange, 200, null,
                    "{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
        });

        FetchResource resource = loader.fetchAsync(
                URI.create(server.url("/data.json"))).await();

        assertEquals(200, resource.statusCode());
        assertEquals("{\"ok\":true}", resource.bodyText());
        assertEquals("wert", resource.headers().first("X-Custom"));
        assertEquals(NetworkResourceType.FETCH, resource.resourceType());
        assertEquals(2, events.size());
        assertTrue(events.get(0) instanceof NetworkRequestEvent.Started);
        NetworkRequestEvent.Loaded loaded = (NetworkRequestEvent.Loaded) events.get(1);
        assertEquals(NetworkResourceType.FETCH, loaded.resourceType());
    }

    @Test
    public void nonSuccessfulStatusStillCompletesLikeFetchSemantics() throws Exception {
        server.on("/missing", exchange -> LocalTestServer.respond(exchange, 404,
                "text/plain", "nicht da".getBytes(StandardCharsets.UTF_8)));

        FetchResource resource = loader.fetchAsync(
                URI.create(server.url("/missing"))).await();

        assertEquals(404, resource.statusCode());
        assertEquals("nicht da", resource.bodyText());
        assertTrue(events.getLast() instanceof NetworkRequestEvent.Loaded);
    }

    @Test
    public void followsRedirectsAndReportsFinalUri() throws Exception {
        server.on("/old", exchange -> {
            exchange.getResponseHeaders().set("Location", "/new");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.serveText("/new", "text/plain; charset=utf-8", "angekommen");

        FetchResource resource = loader.fetchAsync(URI.create(server.url("/old"))).await();

        assertEquals(URI.create(server.url("/new")), resource.uri());
        assertEquals("angekommen", resource.bodyText());
        assertTrue(events.get(1) instanceof NetworkRequestEvent.Redirected);
    }

    @Test
    public void decodesBodyWithCharsetFromContentType() throws Exception {
        server.on("/latin", exchange -> LocalTestServer.respond(exchange, 200,
                "text/plain; charset=ISO-8859-1",
                "Grüße".getBytes(StandardCharsets.ISO_8859_1)));

        FetchResource resource = loader.fetchAsync(URI.create(server.url("/latin"))).await();

        assertEquals("Grüße", resource.bodyText());
    }

    @Test
    public void rejectsUnsupportedSchemesBeforeScheduling() {
        assertThrows(IllegalArgumentException.class,
                () -> loader.fetchAsync(URI.create("file:///etc/passwd")));
        assertTrue(events.isEmpty());
    }

    @Test
    public void connectionFailureFailsTheLoad() {
        FetchResourceLoad load = loader.fetchAsync(
                URI.create("http://127.0.0.1:9/unerreichbar"));

        assertThrows(IOException.class, load::await);
        assertTrue(events.getLast() instanceof NetworkRequestEvent.Failed);
    }

    @Test
    public void cancelBeforeCompletionEmitsCancelledEvent() {
        server.on("/langsam", exchange -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            LocalTestServer.respond(exchange, 200, "text/plain", new byte[0]);
        });

        FetchResourceLoad load = loader.fetchAsync(URI.create(server.url("/langsam")));
        assertTrue(load.cancel());

        assertTrue(load.isCancelled());
        assertFalse(load.cancel());
        assertTrue(events.stream().anyMatch(
                event -> event instanceof NetworkRequestEvent.Cancelled));
    }
}

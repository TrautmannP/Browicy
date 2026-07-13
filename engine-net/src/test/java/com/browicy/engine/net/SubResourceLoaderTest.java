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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertArrayEquals;

public class SubResourceLoaderTest {

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
    public void loadsCssAndEmitsIndependentNetworkEvents() throws IOException {
        server.serveText("/theme.css", "text/css; charset=utf-8",
                "p { color: red; }");

        TextResource resource = loader.load(
                URI.create(server.url("/theme.css")), NetworkResourceType.STYLESHEET);

        assertEquals("p { color: red; }", resource.content());
        assertEquals(NetworkResourceType.STYLESHEET, resource.resourceType());
        assertEquals(2, events.size());
        assertTrue(events.get(0) instanceof NetworkRequestEvent.Started);
        NetworkRequestEvent.Loaded loaded = (NetworkRequestEvent.Loaded) events.get(1);
        assertEquals(NetworkResourceType.STYLESHEET, loaded.resourceType());
        assertEquals(resource.sizeBytes(), loaded.sizeBytes());
    }

    @Test
    public void followsRedirectsForScripts() throws IOException {
        server.on("/old.js", exchange -> {
            exchange.getResponseHeaders().set("Location", "/app.js");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.serveText("/app.js", "application/javascript", "globalThis.loaded = true;");

        TextResource resource = loader.load(
                URI.create(server.url("/old.js")), NetworkResourceType.SCRIPT);

        assertEquals(URI.create(server.url("/app.js")), resource.uri());
        assertEquals(3, events.size());
        assertTrue(events.get(1) instanceof NetworkRequestEvent.Redirected);
    }

    @Test
    public void decodesCssCharsetDeclarationWhenHeaderHasNoCharset() throws IOException {
        String css = "@charset \"ISO-8859-1\"; p::before { content: 'Grüße'; }";
        server.on("/latin.css", exchange -> LocalTestServer.respond(exchange, 200,
                "text/css", css.getBytes(StandardCharsets.ISO_8859_1)));

        TextResource resource = loader.load(
                URI.create(server.url("/latin.css")), NetworkResourceType.STYLESHEET);

        assertTrue(resource.content().contains("Grüße"));
    }

    @Test
    public void nonSuccessfulStatusFailsOnlyTheResource() {
        server.on("/missing.js", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        assertThrows(IOException.class, () -> loader.load(
                URI.create(server.url("/missing.js")), NetworkResourceType.SCRIPT));
        assertTrue(events.getLast() instanceof NetworkRequestEvent.Failed);
    }

    @Test
    public void schedulingFailureEmitsFailedTerminalEvent() {
        loader.close();

        SubResourceLoad load = loader.loadAsync(
                URI.create(server.url("/not-scheduled.css")), NetworkResourceType.STYLESHEET);

        assertThrows(RuntimeException.class, load::await);
        assertEquals(2, events.size());
        assertTrue(events.getFirst() instanceof NetworkRequestEvent.Started);
        assertTrue(events.getLast() instanceof NetworkRequestEvent.Failed);
    }

    @Test
    public void rejectsUnsupportedSchemesBeforeScheduling() {
        assertThrows(IllegalArgumentException.class, () -> loader.loadAsync(
                URI.create("file:///tmp/app.js"), NetworkResourceType.SCRIPT));
        assertTrue(events.isEmpty());
    }

    @Test
    public void loadsImagesAsUnmodifiedBinaryResources() throws IOException {
        byte[] pngBytes = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x00, (byte) 0xff};
        server.on("/pixel.png", exchange -> LocalTestServer.respond(
                exchange, 200, "image/png", pngBytes));

        BinaryResource resource = loader.loadImage(URI.create(server.url("/pixel.png")));

        assertArrayEquals(pngBytes, resource.content());
        assertEquals(NetworkResourceType.IMAGE, resource.resourceType());
        assertEquals(NetworkResourceType.IMAGE,
                ((NetworkRequestEvent.Loaded) events.getLast()).resourceType());
    }
}

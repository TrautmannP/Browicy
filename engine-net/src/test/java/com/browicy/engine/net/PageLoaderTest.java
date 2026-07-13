package com.browicy.engine.net;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PageLoaderTest {

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

    @Test
    public void loadsHtmlPage() throws IOException {
        server.serveHtml("/", "<html><head><title>Start</title></head><body>Inhalt</body></html>");

        PageLoader.Page page = loader.load(server.url("/"));

        assertEquals(200, page.statusCode());
        assertEquals(URI.create(server.url("/")), page.uri());
        assertTrue(page.html().contains("<title>Start</title>"));
    }

    @Test
    public void followsRelativeRedirects() throws IOException {
        // Nachbildung von http://google.com -> http://www.google.com/
        server.on("/alt", exchange -> {
            exchange.getResponseHeaders().set("Location", "/ziel");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });
        server.serveHtml("/ziel", "<html><body>Angekommen</body></html>");

        PageLoader.Page page = loader.load(server.url("/alt"));

        assertEquals(200, page.statusCode());
        assertEquals(URI.create(server.url("/ziel")), page.uri());
        assertTrue(page.html().contains("Angekommen"));
    }

    @Test
    public void followsAbsoluteRedirects() throws IOException {
        server.on("/weiter", exchange -> {
            exchange.getResponseHeaders().set("Location", server.url("/ende"));
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.serveHtml("/ende", "<html><body>Ende</body></html>");

        PageLoader.Page page = loader.load(server.url("/weiter"));

        assertEquals(URI.create(server.url("/ende")), page.uri());
        assertTrue(page.html().contains("Ende"));
    }

    @Test
    public void failsOnRedirectLoops() {
        server.on("/schleife", exchange -> {
            exchange.getResponseHeaders().set("Location", "/schleife");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        IOException e = assertThrows(IOException.class, () -> loader.load(server.url("/schleife")));
        assertTrue(e.getMessage().contains("Weiterleitungen"));
    }

    @Test
    public void decodesCharsetFromContentTypeHeader() throws IOException {
        String html = "<html><body>Grüße</body></html>";
        server.on("/latin1", exchange -> LocalTestServer.respond(exchange, 200,
                "text/html; charset=ISO-8859-1", html.getBytes(StandardCharsets.ISO_8859_1)));

        PageLoader.Page page = loader.load(server.url("/latin1"));

        assertTrue(page.html().contains("Grüße"));
    }

    @Test
    public void sniffsMetaCharsetWhenHeaderHasNone() throws IOException {
        String html = "<html><head><meta charset=\"ISO-8859-1\"></head><body>Grüße</body></html>";
        server.on("/meta", exchange -> LocalTestServer.respond(exchange, 200,
                "text/html", html.getBytes(StandardCharsets.ISO_8859_1)));

        PageLoader.Page page = loader.load(server.url("/meta"));

        assertTrue(page.html().contains("Grüße"));
    }

    @Test
    public void normalizeAddsHttpSchemeWhenMissing() {
        assertEquals(URI.create("http://google.com"), PageLoader.normalize("google.com"));
        assertEquals(URI.create("http://google.com"), PageLoader.normalize("  google.com  "));
        assertEquals(URI.create("https://example.org/pfad"), PageLoader.normalize("https://example.org/pfad"));
        assertEquals(URI.create("about:blank"), PageLoader.normalize("about:blank"));
    }

    @Test
    public void normalizeRejectsEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> PageLoader.normalize("   "));
        assertThrows(IllegalArgumentException.class, () -> PageLoader.normalize(null));
    }
}

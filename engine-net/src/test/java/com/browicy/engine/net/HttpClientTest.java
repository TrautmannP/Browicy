package com.browicy.engine.net;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HttpClientTest {

    private LocalTestServer server;
    private final HttpClient client = new HttpClient();

    @Before
    public void startServer() throws IOException {
        server = new LocalTestServer();
    }

    @After
    public void stopServer() {
        server.close();
    }

    @Test
    public void getsSimpleHtmlPage() throws IOException {
        server.serveHtml("/", "<html><body><h1>Hallo</h1></body></html>");

        HttpResponse response = client.get(URI.create(server.url("/")));

        assertEquals(200, response.statusCode());
        assertEquals("<html><body><h1>Hallo</h1></body></html>",
                new String(response.body(), StandardCharsets.UTF_8));
        assertEquals("text/html; charset=utf-8", response.headers().first("Content-Type"));
        assertEquals(StandardCharsets.UTF_8, response.charsetFromHeaders().orElseThrow());
    }

    @Test
    public void sendsHostUserAgentAndConnectionCloseHeaders() throws IOException {
        AtomicReference<com.sun.net.httpserver.Headers> seen = new AtomicReference<>();
        server.on("/echo", exchange -> {
            seen.set(exchange.getRequestHeaders());
            LocalTestServer.respond(exchange, 200, "text/plain", "ok".getBytes(StandardCharsets.UTF_8));
        });

        client.get(URI.create(server.url("/echo")));

        assertEquals("127.0.0.1:" + server.port(), seen.get().getFirst("Host"));
        assertEquals("Browicy/0.1", seen.get().getFirst("User-Agent"));
        assertEquals("close", seen.get().getFirst("Connection"));
    }

    @Test
    public void readsChunkedResponses() throws IOException {
        server.on("/chunked", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write("Erster Teil, ".getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.write("zweiter Teil.".getBytes(StandardCharsets.UTF_8));
            }
        });

        HttpResponse response = client.get(URI.create(server.url("/chunked")));

        assertEquals(200, response.statusCode());
        assertEquals("Erster Teil, zweiter Teil.", new String(response.body(), StandardCharsets.UTF_8));
    }

    @Test
    public void decodesGzipBodies() throws IOException {
        byte[] compressed = gzip("Komprimierter Inhalt äöü".getBytes(StandardCharsets.UTF_8));
        server.on("/gzip", exchange -> {
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            LocalTestServer.respond(exchange, 200, "text/html; charset=utf-8", compressed);
        });

        HttpResponse response = client.get(URI.create(server.url("/gzip")));

        assertEquals("Komprimierter Inhalt äöü", new String(response.body(), StandardCharsets.UTF_8));
    }

    @Test
    public void rejectsGzipBodiesThatDecompressBeyondTheBodyLimit() throws IOException {
        byte[] bomb = gzip(new byte[33 * 1024 * 1024]);
        server.on("/bombe", exchange -> {
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            LocalTestServer.respond(exchange, 200, "text/html", bomb);
        });

        IOException e = assertThrows(IOException.class,
                () -> client.get(URI.create(server.url("/bombe"))));
        assertTrue(e.getMessage().contains("zu groß"));
    }

    @Test
    public void propagatesErrorStatusWithBody() throws IOException {
        server.on("/fehlt", exchange -> LocalTestServer.respond(exchange, 404,
                "text/html", "<h1>Nicht gefunden</h1>".getBytes(StandardCharsets.UTF_8)));

        HttpResponse response = client.get(URI.create(server.url("/fehlt")));

        assertEquals(404, response.statusCode());
        assertTrue(new String(response.body(), StandardCharsets.UTF_8).contains("Nicht gefunden"));
    }

    @Test
    public void keepsRedirectStatusAndLocationHeader() throws IOException {
        server.on("/alt", exchange -> {
            exchange.getResponseHeaders().set("Location", "/neu");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });

        HttpResponse response = client.get(URI.create(server.url("/alt")));

        assertTrue(response.isRedirect());
        assertEquals("/neu", response.location());
    }

    @Test
    public void enforcesSharedTransferBudgetBeforeReadingKnownBody() {
        server.on("/large", exchange -> LocalTestServer.respond(exchange, 200,
                "application/octet-stream", new byte[1024]));
        DownloadBudget budget = new DownloadBudget(100, 100);

        IOException failure = assertThrows(IOException.class, () -> client.get(
                URI.create(server.url("/large")), "*/*", budget));

        assertTrue(failure.getMessage().contains("Transferbudget"));
    }

    @Test
    public void rejectsUnsupportedSchemes() {
        IOException e = assertThrows(IOException.class,
                () -> client.get(URI.create("ftp://example.org/")));
        assertTrue(e.getMessage().contains("Schema"));
    }

    @Test
    public void failsWhenNothingListensOnPort() throws IOException {
        int freePort;
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }

        assertThrows(IOException.class,
                () -> client.get(URI.create("http://127.0.0.1:" + freePort + "/")));
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(buffer)) {
            out.write(data);
        }
        return buffer.toByteArray();
    }
}

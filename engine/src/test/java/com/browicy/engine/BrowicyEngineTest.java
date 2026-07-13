package com.browicy.engine;

import com.browicy.engine.dom.Document;
import com.browicy.engine.net.LocalTestServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BrowicyEngineTest {

    private LocalTestServer server;
    private final BrowicyEngine engine = new BrowicyEngine();

    @Before
    public void startServer() throws IOException {
        server = new LocalTestServer();
    }

    @After
    public void stopServer() {
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
        // google.com leitet auf www.google.com weiter — hier lokal nachgebildet
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
    public void servesHelloWorldForAboutUrls() {
        Document document = engine.loadPage("about:home");

        assertTrue(document.getTitle().contains("Hallo Welt"));
    }
}

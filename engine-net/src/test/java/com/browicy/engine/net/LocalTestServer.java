package com.browicy.engine.net;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lokaler HTTP-Server für Tests des Netzwerkstacks. Läuft auf einem
 * zufälligen freien Port, damit Tests deterministisch und ohne
 * Internetverbindung funktionieren. Handler laufen auf eigenen Threads,
 * damit auch parallele und absichtlich blockierende Requests möglich sind.
 */
public final class LocalTestServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService handlerThreads = Executors.newVirtualThreadPerTaskExecutor();

    public LocalTestServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(handlerThreads);
        server.start();
    }

    /** Registriert einen Handler für den angegebenen Pfad. */
    public void on(String path, HttpHandler handler) {
        server.createContext(path, handler);
    }

    /** Registriert eine statische HTML-Seite (UTF-8) unter dem Pfad. */
    public void serveHtml(String path, String html) {
        on(path, exchange -> respond(exchange, 200, "text/html; charset=utf-8",
                html.getBytes(StandardCharsets.UTF_8)));
    }

    public String url(String path) {
        return "http://127.0.0.1:" + port() + path;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    /** Beantwortet den Request mit Status, Content-Type und Rumpf. */
    public static void respond(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        if (contentType != null) {
            exchange.getResponseHeaders().set("Content-Type", contentType);
        }
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        handlerThreads.shutdownNow();
    }
}

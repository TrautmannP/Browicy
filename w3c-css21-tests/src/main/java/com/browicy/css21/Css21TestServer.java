package com.browicy.css21;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Minimaler HTTP-Server für die Testsuite: liefert Dateien aus
 * {@code /css21} mit den korrekten Content-Types aus
 * ({@code .xht} als {@code application/xhtml+xml}, wie vom W3C-Suite-Host
 * per {@code .htaccess} vorgesehen).
 */
public final class Css21TestServer implements AutoCloseable {

    private static final Map<String, String> MIME_TYPES = Map.ofEntries(
            Map.entry(".xht", "application/xhtml+xml; charset=utf-8"),
            Map.entry(".html", "text/html; charset=utf-8"),
            Map.entry(".htm", "text/html; charset=utf-8"),
            Map.entry(".css", "text/css; charset=utf-8"),
            Map.entry(".png", "image/png"),
            Map.entry(".gif", "image/gif"),
            Map.entry(".svg", "image/svg+xml"),
            Map.entry(".xml", "application/xml"),
            Map.entry(".cur", "image/x-icon"),
            Map.entry(".woff", "font/woff"),
            Map.entry(".txt", "text/plain; charset=utf-8"),
            Map.entry(".json", "application/json"));

    private final HttpServer server;
    private final Path root;

    private Css21TestServer(Path root) throws IOException {
        this.root = root;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public static Css21TestServer start() {
        try {
            return new Css21TestServer(Css21Suite.suiteRoot());
        } catch (IOException failure) {
            throw new IllegalStateException("Testserver konnte nicht starten", failure);
        }
    }

    /** Absolute URL eines Suite-Tests auf diesem Server. */
    public String url(String relativePath) {
        return "http://127.0.0.1:" + server.getAddress().getPort()
                + "/" + relativePath.replace('\\', '/');
    }

    private void handle(HttpExchange exchange) {
        try {
            String requestPath = exchange.getRequestURI().getPath();
            Path file = root.resolve(requestPath.startsWith("/")
                    ? requestPath.substring(1) : requestPath).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                send(exchange, 404, "text/plain", "not found: " + requestPath);
                return;
            }
            byte[] body = Files.readAllBytes(file);
            String name = file.getFileName().toString();
            String extension = name.substring(name.lastIndexOf('.'));
            exchange.getResponseHeaders().set("Content-Type",
                    MIME_TYPES.getOrDefault(extension.toLowerCase(java.util.Locale.ROOT),
                            "application/octet-stream"));
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        } catch (IOException failure) {
            try {
                send(exchange, 500, "text/plain", "server error");
            } catch (IOException ignored) {
                // Verbindung ist bereits weg; nichts weiter zu tun.
            }
        } finally {
            exchange.close();
        }
    }

    private static void send(HttpExchange exchange, int status, String type, String body)
            throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

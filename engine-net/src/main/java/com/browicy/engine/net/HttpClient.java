package com.browicy.engine.net;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class HttpClient {

    private static final String USER_AGENT = "Browicy/0.1";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_RESPONSE_DURATION_MS = 60_000;
    private static final int MAX_HEADER_COUNT = 200;
    private static final int MAX_LINE_LENGTH = 16 * 1024;
    private static final int MAX_BODY_BYTES = 32 * 1024 * 1024;
    private static final Set<String> GENERATED_REQUEST_HEADERS = Set.of(
            "host", "content-length", "transfer-encoding", "connection");
    private static final String TOKEN_SEPARATORS = "()<>@,;:\\\"/[]?={} \t";

    public HttpResponse get(URI url) throws IOException {
        return get(url, "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8");
    }

    public HttpResponse get(URI url, String accept) throws IOException {
        return get(url, accept, null);
    }

    HttpResponse get(URI url, String accept, DownloadBudget budget) throws IOException {
        return request(HttpRequest.get(url, accept), budget);
    }

    /**
     * Führt eine HTTP-Anfrage mit optionalem Body und Request-Headern aus.
     * {@code Content-Length}, {@code Host} und {@code Connection} werden vom
     * Client selbst erzeugt und können nicht durch Aufrufer überschrieben werden.
     */
    public HttpResponse request(String method, URI url, byte[] body, HttpHeaders headers)
            throws IOException {
        return request(new HttpRequest(method, url, headers, body));
    }

    public HttpResponse request(HttpRequest request) throws IOException {
        return request(request, null);
    }

    HttpResponse request(HttpRequest request, DownloadBudget budget) throws IOException {
        URI url = request.uri();
        String scheme = url.getScheme() == null
                ? "" : url.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IOException("Nicht unterstütztes URL-Schema: " + url);
        }
        String host = url.getHost();
        if (host == null) {
            throw new IOException("URL ohne Host: " + url);
        }
        byte[] requestBody = request.body();
        if (requestBody != null && requestBody.length > MAX_BODY_BYTES) {
            throw new IOException("Request-Body zu groß: " + requestBody.length + " Bytes");
        }

        boolean secure = scheme.equals("https");
        int defaultPort = secure ? 443 : 80;
        int port = url.getPort() != -1 ? url.getPort() : defaultPort;

        String requestHead = buildRequestHead(request, host, port, defaultPort, requestBody);
        try (Socket socket = openSocket(host, port, secure)) {
            socket.setSoTimeout(READ_TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            out.write(requestHead.getBytes(StandardCharsets.ISO_8859_1));
            if (requestBody != null) {
                out.write(requestBody);
            }
            out.flush();
            return readResponse(new BufferedInputStream(
                            new DeadlineInputStream(
                                    socket.getInputStream(), MAX_RESPONSE_DURATION_MS)),
                    budget, request.method());
        }
    }

    private static Socket openSocket(String host, int port, boolean secure) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            if (!secure) {
                return socket;
            }
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) factory.createSocket(socket, host, port, true);
            SSLParameters parameters = sslSocket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            sslSocket.setSSLParameters(parameters);
            sslSocket.startHandshake();
            return sslSocket;
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    private static String buildRequestHead(HttpRequest request,
                                           String host,
                                           int port,
                                           int defaultPort,
                                           byte[] body) {
        URI url = request.uri();
        String path = url.getRawPath() == null || url.getRawPath().isEmpty()
                ? "/" : url.getRawPath();
        String target = url.getRawQuery() == null ? path : path + "?" + url.getRawQuery();
        String formattedHost = host.indexOf(':') >= 0 ? "[" + host + "]" : host;
        String hostHeader = port == defaultPort ? formattedHost : formattedHost + ":" + port;
        HttpHeaders headers = request.headers();

        StringBuilder result = new StringBuilder()
                .append(request.method()).append(' ').append(target).append(" HTTP/1.1\r\n")
                .append("Host: ").append(hostHeader).append("\r\n");
        appendDefaultHeader(result, headers, "User-Agent", USER_AGENT);
        appendDefaultHeader(result, headers, "Accept", "*/*");
        appendDefaultHeader(result, headers, "Accept-Encoding", "gzip");

        int headerCount = 4;
        for (String name : headers.names()) {
            if (GENERATED_REQUEST_HEADERS.contains(name)) {
                continue;
            }
            validateHeaderName(name);
            for (String value : headers.all(name)) {
                if (++headerCount > MAX_HEADER_COUNT) {
                    throw new IllegalArgumentException("Zu viele Header in der Anfrage");
                }
                result.append(name).append(": ")
                        .append(sanitizeHeaderValue(value)).append("\r\n");
            }
        }
        if (body != null) {
            result.append("Content-Length: ").append(body.length).append("\r\n");
        }
        return result.append("Connection: close\r\n\r\n").toString();
    }

    private static void appendDefaultHeader(StringBuilder target,
                                            HttpHeaders headers,
                                            String name,
                                            String defaultValue) {
        if (!headers.contains(name)) {
            target.append(name).append(": ").append(defaultValue).append("\r\n");
        }
    }

    private static void validateHeaderName(String name) {
        if (name.isEmpty()) {
            throw new IllegalArgumentException("HTTP-Headername darf nicht leer sein");
        }
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (character <= 0x20 || character >= 0x7f
                    || TOKEN_SEPARATORS.indexOf(character) >= 0) {
                throw new IllegalArgumentException("Ungültiger HTTP-Headername: " + name);
            }
        }
    }

    private static String sanitizeHeaderValue(String value) {
        String normalized = value == null ? "" : value.strip();
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if ((character < 0x20 && character != '\t') || character == 0x7f) {
                throw new IllegalArgumentException("Ungültiger HTTP-Headerwert");
            }
        }
        return normalized;
    }

    private static HttpResponse readResponse(InputStream in,
                                             DownloadBudget budget,
                                             String requestMethod) throws IOException {
        String statusLine = readLine(in);
        if (statusLine == null || statusLine.isEmpty()) {
            throw new IOException("Leere Antwort vom Server");
        }
        String[] parts = statusLine.split(" ", 3);
        if (parts.length < 2 || !parts[0].startsWith("HTTP/")) {
            throw new IOException("Ungültige Statuszeile: " + statusLine);
        }
        int statusCode;
        try {
            statusCode = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IOException("Ungültiger Statuscode: " + statusLine);
        }
        String reasonPhrase = parts.length == 3 ? parts[2] : "";

        HttpHeaders headers = readHeaders(in);
        byte[] body = readBody(in, statusCode, headers, budget, requestMethod);
        body = decodeContentEncoding(body, headers, budget);
        return new HttpResponse(statusCode, reasonPhrase, headers, body);
    }

    private static HttpHeaders readHeaders(InputStream in) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        int count = 0;
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            if (++count > MAX_HEADER_COUNT) {
                throw new IOException("Zu viele Header in der Antwort");
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            headers.add(line.substring(0, colon), line.substring(colon + 1));
        }
        return headers;
    }

    private static byte[] readBody(InputStream in,
                                   int statusCode,
                                   HttpHeaders headers,
                                   DownloadBudget budget,
                                   String requestMethod) throws IOException {
        boolean bodyless = requestMethod.equals("HEAD")
                || statusCode / 100 == 1 || statusCode == 204 || statusCode == 304;
        if (bodyless) {
            return new byte[0];
        }
        if (headers.hasValue("Transfer-Encoding", "chunked")) {
            return readChunkedBody(in, budget);
        }
        String contentLength = headers.first("Content-Length");
        if (contentLength != null) {
            long length;
            try {
                length = Long.parseLong(contentLength.strip());
            } catch (NumberFormatException e) {
                throw new IOException("Ungültige Content-Length: " + contentLength);
            }
            if (length < 0) {
                throw new IOException("Ungültige Content-Length: " + contentLength);
            }
            if (length > MAX_BODY_BYTES) {
                throw new IOException("Antwort zu groß: " + length + " Bytes");
            }
            if (budget != null) budget.consumeTransfer(length);
            return readFully(in, (int) length, null);
        }
        return readUntilEof(in, budget);
    }

    private static byte[] readChunkedBody(InputStream in, DownloadBudget budget) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readLine(in);
            if (sizeLine == null) {
                throw new IOException("Verbindung endete mitten in einer Chunked-Antwort");
            }
            int extension = sizeLine.indexOf(';');
            String hex = (extension >= 0 ? sizeLine.substring(0, extension) : sizeLine).strip();
            int size;
            try {
                size = Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                throw new IOException("Ungültige Chunk-Größe: " + sizeLine);
            }
            if (size < 0) {
                throw new IOException("Ungültige Chunk-Größe: " + sizeLine);
            }
            if (size == 0) {
                String line;
                while ((line = readLine(in)) != null && !line.isEmpty()) {
                }
                return body.toByteArray();
            }
            if (body.size() + size > MAX_BODY_BYTES) {
                throw new IOException("Antwort zu groß (chunked)");
            }
            if (budget != null) budget.consumeTransfer(size);
            body.write(readFully(in, size, null));
            readLine(in);
        }
    }

    private static byte[] decodeContentEncoding(byte[] body, HttpHeaders headers,
                                                DownloadBudget budget) throws IOException {
        String encoding = headers.first("Content-Encoding");
        if (encoding == null || encoding.equalsIgnoreCase("identity") || body.length == 0) {
            if (budget != null) budget.consumeDecoded(body.length);
            return body;
        }
        if (encoding.equalsIgnoreCase("gzip") || encoding.equalsIgnoreCase("x-gzip")) {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(body))) {
                return readLimited(gzip, "Antwort zu groß (entpackt)", budget, true);
            }
        }
        throw new IOException("Nicht unterstütztes Content-Encoding: " + encoding);
    }

    private static byte[] readFully(InputStream in, int length, DownloadBudget budget)
            throws IOException {
        byte[] data = in.readNBytes(length);
        if (budget != null) budget.consumeTransfer(data.length);
        if (data.length < length) {
            throw new IOException("Verbindung endete vorzeitig: " + data.length
                    + " von " + length + " Bytes");
        }
        return data;
    }

    private static byte[] readUntilEof(InputStream in, DownloadBudget budget) throws IOException {
        return readLimited(in, "Antwort zu groß", budget, false);
    }

    private static byte[] readLimited(InputStream in, String tooLargeMessage,
                                      DownloadBudget budget, boolean decoded) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (budget != null) {
                if (decoded) budget.consumeDecoded(read);
                else budget.consumeTransfer(read);
            }
            if (body.size() + read > MAX_BODY_BYTES) {
                throw new IOException(tooLargeMessage);
            }
            body.write(buffer, 0, read);
        }
        return body.toByteArray();
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b = in.read();
        if (b == -1) {
            return null;
        }
        while (b != -1 && b != '\n') {
            if (line.size() > MAX_LINE_LENGTH) {
                throw new IOException("Header-Zeile zu lang");
            }
            line.write(b);
            b = in.read();
        }
        String text = line.toString(StandardCharsets.ISO_8859_1);
        return text.endsWith("\r") ? text.substring(0, text.length() - 1) : text;
    }

    private static final class DeadlineInputStream extends InputStream {

        private final InputStream delegate;
        private final long deadlineNanos;

        DeadlineInputStream(InputStream delegate, int durationMs) {
            this.delegate = delegate;
            this.deadlineNanos = System.nanoTime() + durationMs * 1_000_000L;
        }

        @Override
        public int read() throws IOException {
            checkDeadline();
            return delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            checkDeadline();
            return delegate.read(buffer, offset, length);
        }

        private void checkDeadline() throws IOException {
            if (System.nanoTime() - deadlineNanos >= 0) {
                throw new IOException("Zeitlimit für die Antwort überschritten ("
                        + MAX_RESPONSE_DURATION_MS + " ms)");
            }
        }
    }
}

package com.browicy.engine.net;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/**
 * Unveränderliche HTTP-Anfrage für den Netzwerk-Stack.
 * Ein {@code null}-Body bedeutet, dass kein Request-Body vorhanden ist;
 * ein leeres Byte-Array repräsentiert dagegen einen vorhandenen, leeren Body.
 */
public final class HttpRequest {

    private static final String TOKEN_SEPARATORS = "()<>@,;:\\\"/[]?={} \t";

    private final String method;
    private final URI uri;
    private final HttpHeaders headers;
    private final byte[] body;

    public HttpRequest(String method, URI uri, HttpHeaders headers, byte[] body) {
        this.method = normalizeMethod(method);
        this.uri = Objects.requireNonNull(uri, "uri");
        this.headers = (headers == null ? new HttpHeaders() : headers).immutableCopy();
        this.body = body == null ? null : body.clone();
    }

    public static HttpRequest get(URI uri, String accept) {
        HttpHeaders headers = new HttpHeaders();
        if (accept != null && !accept.isBlank()) {
            headers.set("Accept", accept);
        }
        return new HttpRequest("GET", uri, headers, null);
    }

    public String method() {
        return method;
    }

    public URI uri() {
        return uri;
    }

    public HttpHeaders headers() {
        return headers;
    }

    public byte[] body() {
        return body == null ? null : body.clone();
    }

    public boolean hasBody() {
        return body != null;
    }

    public HttpRequest withUri(URI target) {
        return new HttpRequest(method, target, headers, body);
    }

    public HttpRequest asGetWithoutBody(URI target) {
        HttpHeaders redirectedHeaders = headers.copy();
        redirectedHeaders.remove("Content-Encoding");
        redirectedHeaders.remove("Content-Language");
        redirectedHeaders.remove("Content-Length");
        redirectedHeaders.remove("Content-Location");
        redirectedHeaders.remove("Content-Type");
        return new HttpRequest("GET", target, redirectedHeaders, null);
    }

    public HttpRequest withoutSensitiveHeaders() {
        HttpHeaders redirectedHeaders = headers.copy();
        redirectedHeaders.remove("Authorization");
        redirectedHeaders.remove("Proxy-Authorization");
        redirectedHeaders.remove("Cookie");
        return new HttpRequest(method, uri, redirectedHeaders, body);
    }

    private static String normalizeMethod(String method) {
        Objects.requireNonNull(method, "method");
        String normalized = method.strip().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("HTTP-Methode darf nicht leer sein");
        }
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character <= 0x20 || character >= 0x7f
                    || TOKEN_SEPARATORS.indexOf(character) >= 0) {
                throw new IllegalArgumentException("Ungültige HTTP-Methode: " + method);
            }
        }
        return normalized;
    }
}

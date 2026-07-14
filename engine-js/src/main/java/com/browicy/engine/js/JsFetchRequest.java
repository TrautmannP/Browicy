package com.browicy.engine.js;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Unveränderliche Anfrage zwischen JavaScript-Runtime und Page-Netzwerk-Backend. */
public final class JsFetchRequest {

    private static final String TOKEN_SEPARATORS = "()<>@,;:\\\"/[]?={} \t";

    private final URI uri;
    private final String method;
    private final List<Header> headers;
    private final byte[] body;

    public JsFetchRequest(URI uri, String method, List<Header> headers, byte[] body) {
        this.uri = Objects.requireNonNull(uri, "uri");
        this.method = normalizeMethod(method);
        this.headers = List.copyOf(Objects.requireNonNull(headers, "headers"));
        this.body = body == null ? null : body.clone();
    }

    public static JsFetchRequest get(URI uri) {
        return new JsFetchRequest(uri, "GET", List.of(), null);
    }

    public URI uri() {
        return uri;
    }

    public String method() {
        return method;
    }

    public List<Header> headers() {
        return headers;
    }

    public byte[] body() {
        return body == null ? null : body.clone();
    }

    public boolean hasBody() {
        return body != null;
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

    public record Header(String name, String value) {
        public Header {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            name = name.strip().toLowerCase(Locale.ROOT);
            value = value.strip();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("HTTP-Headername darf nicht leer sein");
            }
            for (int index = 0; index < name.length(); index++) {
                char character = name.charAt(index);
                if (character <= 0x20 || character >= 0x7f
                        || TOKEN_SEPARATORS.indexOf(character) >= 0) {
                    throw new IllegalArgumentException(
                            "Ungültiger HTTP-Headername: " + name);
                }
            }
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if ((character < 0x20 && character != '\t') || character == 0x7f) {
                    throw new IllegalArgumentException("Ungültiger HTTP-Headerwert");
                }
            }
        }
    }
}

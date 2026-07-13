package com.browicy.engine.net;

import java.nio.charset.Charset;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Eine vollständig gelesene HTTP-Antwort: Statuszeile, Header und der
 * (bereits von Transfer- und Content-Encoding befreite) Rumpf als Bytes.
 */
public final class HttpResponse {

    private static final Pattern CHARSET_PARAM =
            Pattern.compile("charset\\s*=\\s*\"?([A-Za-z0-9_\\-.:]+)\"?", Pattern.CASE_INSENSITIVE);

    private final int statusCode;
    private final String reasonPhrase;
    private final HttpHeaders headers;
    private final byte[] body;

    public HttpResponse(int statusCode, String reasonPhrase, HttpHeaders headers, byte[] body) {
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
        this.headers = headers;
        this.body = body;
    }

    public int statusCode() {
        return statusCode;
    }

    public String reasonPhrase() {
        return reasonPhrase;
    }

    public HttpHeaders headers() {
        return headers;
    }

    public byte[] body() {
        return body;
    }

    /** {@code true} für die Weiterleitungs-Status 301, 302, 303, 307 und 308. */
    public boolean isRedirect() {
        return statusCode == 301 || statusCode == 302 || statusCode == 303
                || statusCode == 307 || statusCode == 308;
    }

    /** Ziel einer Weiterleitung ({@code Location}-Header) oder {@code null}. */
    public String location() {
        return headers.first("Location");
    }

    /** Zeichensatz aus dem {@code charset}-Parameter des {@code Content-Type}-Headers, falls angegeben und bekannt. */
    public Optional<Charset> charsetFromHeaders() {
        String contentType = headers.first("Content-Type");
        if (contentType == null) {
            return Optional.empty();
        }
        Matcher matcher = CHARSET_PARAM.matcher(contentType);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Charset.forName(matcher.group(1)));
        } catch (IllegalArgumentException unknownCharset) {
            return Optional.empty();
        }
    }
}

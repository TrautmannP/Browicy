package com.browicy.engine.net;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.nio.charset.Charset;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public final class HttpResponse {

    private static final Pattern CHARSET_PARAM =
            Pattern.compile("charset\\s*=\\s*\"?([A-Za-z0-9_\\-.:]+)\"?", Pattern.CASE_INSENSITIVE);

    private final int statusCode;
    private final String reasonPhrase;
    private final HttpHeaders headers;
    private final byte[] body;

    public boolean isRedirect() {
        return statusCode == 301 || statusCode == 302 || statusCode == 303
                || statusCode == 307 || statusCode == 308;
    }

    public String location() {
        return headers.first("Location");
    }

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

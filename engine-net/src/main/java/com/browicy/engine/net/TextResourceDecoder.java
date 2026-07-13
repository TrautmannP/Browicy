package com.browicy.engine.net;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TextResourceDecoder {

    private static final int SNIFF_BYTES = 1024;
    private static final Pattern CSS_CHARSET = Pattern.compile(
            "^\\s*@charset\\s+[\\\"']([^\\\"']+)[\\\"']\\s*;",
            Pattern.CASE_INSENSITIVE);

    private TextResourceDecoder() {
    }

    static String decode(HttpResponse response, NetworkResourceType type) {
        Charset charset = response.charsetFromHeaders()
                .or(() -> type == NetworkResourceType.STYLESHEET
                        ? sniffCssCharset(response.body())
                        : Optional.empty())
                .orElse(StandardCharsets.UTF_8);
        return new String(response.body(), charset);
    }

    private static Optional<Charset> sniffCssCharset(byte[] body) {
        String prefix = new String(body, 0, Math.min(body.length, SNIFF_BYTES),
                StandardCharsets.ISO_8859_1);
        Matcher matcher = CSS_CHARSET.matcher(prefix);
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

package com.browicy.engine.net;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record FetchResource(URI uri,
                            int statusCode,
                            String statusText,
                            HttpHeaders headers,
                            byte[] body) {

    public FetchResource {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(statusText, "statusText");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(body, "body");
    }

    public int sizeBytes() {
        return body.length;
    }

    public NetworkResourceType resourceType() {
        return NetworkResourceType.FETCH;
    }

    public String bodyText() {
        Charset charset = HttpResponse.charsetFrom(headers).orElse(StandardCharsets.UTF_8);
        return new String(body, charset);
    }
}

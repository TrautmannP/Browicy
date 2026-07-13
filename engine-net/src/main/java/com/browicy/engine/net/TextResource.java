package com.browicy.engine.net;

import java.net.URI;
import java.util.Objects;

public record TextResource(URI uri, int statusCode, String content, int sizeBytes,
                           NetworkResourceType resourceType) {

    public TextResource {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(resourceType, "resourceType");
    }
}

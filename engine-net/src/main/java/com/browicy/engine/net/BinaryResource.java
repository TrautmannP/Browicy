package com.browicy.engine.net;

import java.net.URI;
import java.util.Objects;

public record BinaryResource(URI uri, int statusCode, byte[] content,
                             NetworkResourceType resourceType) {

    public BinaryResource {
        Objects.requireNonNull(uri, "uri");
        content = Objects.requireNonNull(content, "content").clone();
        Objects.requireNonNull(resourceType, "resourceType");
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    public int sizeBytes() {
        return content.length;
    }
}

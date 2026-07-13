package com.browicy.engine.net;

import java.net.URI;
import java.time.Instant;

public sealed interface NetworkRequestEvent {

    long requestId();

    Instant at();

    NetworkResourceType resourceType();

    record Started(long requestId, Instant at, String url, NetworkResourceType resourceType)
            implements NetworkRequestEvent {
    }

    record Redirected(long requestId, Instant at, URI from, URI to, int statusCode,
                      NetworkResourceType resourceType) implements NetworkRequestEvent {
    }

    record Loaded(long requestId, Instant at, URI finalUri, int statusCode, int sizeBytes,
                  NetworkResourceType resourceType) implements NetworkRequestEvent {
    }

    record Failed(long requestId, Instant at, String url, Exception cause,
                  NetworkResourceType resourceType) implements NetworkRequestEvent {
    }

    record Cancelled(long requestId, Instant at, String url, NetworkResourceType resourceType)
            implements NetworkRequestEvent {
    }
}

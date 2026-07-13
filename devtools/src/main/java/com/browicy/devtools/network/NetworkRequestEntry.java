package com.browicy.devtools.network;

import com.browicy.engine.net.PageLoad;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public record NetworkRequestEntry(
        long loadId,
        String url,
        PageLoad.State state,
        Instant startedAt,
        Instant finishedAt,
        URI finalUri,
        Integer statusCode,
        int redirectCount,
        int htmlLength,
        String failureMessage) {

    public static NetworkRequestEntry started(long loadId, String url, Instant at) {
        return new NetworkRequestEntry(loadId, url, PageLoad.State.LOADING, at,
                null, null, null, 0, 0, null);
    }

    public NetworkRequestEntry redirected() {
        return new NetworkRequestEntry(loadId, url, state, startedAt,
                finishedAt, finalUri, statusCode, redirectCount + 1, htmlLength, failureMessage);
    }

    public NetworkRequestEntry loaded(URI finalUri, int statusCode, int htmlLength, Instant at) {
        return new NetworkRequestEntry(loadId, url, PageLoad.State.LOADED, startedAt,
                at, finalUri, statusCode, redirectCount, htmlLength, null);
    }

    public NetworkRequestEntry failed(String message, Instant at) {
        return new NetworkRequestEntry(loadId, url, PageLoad.State.FAILED, startedAt,
                at, finalUri, statusCode, redirectCount, htmlLength, message);
    }

    public NetworkRequestEntry cancelled(Instant at) {
        return new NetworkRequestEntry(loadId, url, PageLoad.State.CANCELLED, startedAt,
                at, finalUri, statusCode, redirectCount, htmlLength, null);
    }

    public Optional<Duration> duration() {
        return finishedAt == null
                ? Optional.empty()
                : Optional.of(Duration.between(startedAt, finishedAt));
    }

    public String displayUrl() {
        return finalUri != null ? finalUri.toString() : url;
    }
}

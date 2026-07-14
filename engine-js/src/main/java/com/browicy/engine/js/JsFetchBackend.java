package com.browicy.engine.js;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

public interface JsFetchBackend {

    CompletableFuture<JsFetchResponse> fetch(JsFetchRequest request);

    default CompletableFuture<JsFetchResponse> fetch(URI uri) {
        return fetch(JsFetchRequest.get(uri));
    }
}

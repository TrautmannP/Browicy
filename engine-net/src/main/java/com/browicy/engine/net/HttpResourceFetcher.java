package com.browicy.engine.net;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

final class HttpResourceFetcher {

    static final int MAX_REDIRECTS = 10;

    @FunctionalInterface
    interface RedirectListener {
        void redirected(URI from, URI to, int statusCode);
    }

    record FetchResult(URI uri, HttpResponse response) {
    }

    private HttpResourceFetcher() {
    }

    static FetchResult fetch(HttpClient client,
                             URI initialUri,
                             String accept,
                             BooleanSupplier cancelled,
                             RedirectListener redirectListener) throws IOException {
        URI uri = initialUri;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            if (cancelled.getAsBoolean()) {
                throw new CancellationException("Ladevorgang abgebrochen: " + initialUri);
            }
            HttpResponse response = client.get(uri, accept);
            String location = response.location();
            if (response.isRedirect() && location != null) {
                URI target;
                try {
                    target = uri.resolve(location.strip());
                } catch (IllegalArgumentException invalidLocation) {
                    throw new IOException("Ungültiges Redirect-Ziel: " + location, invalidLocation);
                }
                redirectListener.redirected(uri, target, response.statusCode());
                uri = target;
                continue;
            }
            return new FetchResult(uri, response);
        }
        throw new IOException("Zu viele Weiterleitungen (mehr als " + MAX_REDIRECTS + "): " + initialUri);
    }
}

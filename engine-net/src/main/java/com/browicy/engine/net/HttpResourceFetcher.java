package com.browicy.engine.net;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
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
        return fetch(client, initialUri, accept, cancelled, redirectListener, false);
    }

    static FetchResult fetch(HttpClient client,
                             URI initialUri,
                             String accept,
                             BooleanSupplier cancelled,
                             RedirectListener redirectListener,
                             boolean blockInsecureRedirects) throws IOException {
        return fetch(client, initialUri, accept, cancelled, redirectListener,
                blockInsecureRedirects, null, ignored -> { });
    }

    static FetchResult fetch(HttpClient client,
                             URI initialUri,
                             String accept,
                             BooleanSupplier cancelled,
                             RedirectListener redirectListener,
                             boolean blockInsecureRedirects,
                             DownloadBudget budget,
                             UriValidator validator) throws IOException {
        return fetch(client, HttpRequest.get(initialUri, accept), cancelled, redirectListener,
                blockInsecureRedirects, budget, validator);
    }

    static FetchResult fetch(HttpClient client,
                             HttpRequest initialRequest,
                             BooleanSupplier cancelled,
                             RedirectListener redirectListener,
                             boolean blockInsecureRedirects) throws IOException {
        return fetch(client, initialRequest, cancelled, redirectListener,
                blockInsecureRedirects, null, ignored -> { });
    }

    static FetchResult fetch(HttpClient client,
                             HttpRequest initialRequest,
                             BooleanSupplier cancelled,
                             RedirectListener redirectListener,
                             boolean blockInsecureRedirects,
                             DownloadBudget budget,
                             UriValidator validator) throws IOException {
        HttpRequest request = initialRequest;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            if (cancelled.getAsBoolean()) {
                throw new CancellationException(
                        "Ladevorgang abgebrochen: " + initialRequest.uri());
            }
            validator.validate(request.uri());
            HttpResponse response = client.request(request, budget);
            String location = response.location();
            if (response.isRedirect() && location != null) {
                URI target;
                try {
                    target = request.uri().resolve(location.strip());
                } catch (IllegalArgumentException invalidLocation) {
                    throw new IOException("Ungültiges Redirect-Ziel: " + location,
                            invalidLocation);
                }
                if (blockInsecureRedirects
                        && "https".equalsIgnoreCase(request.uri().getScheme())
                        && !"https".equalsIgnoreCase(target.getScheme())) {
                    throw new IOException(
                            "Unsichere Weiterleitung von HTTPS blockiert: "
                                    + request.uri() + " -> " + target);
                }
                redirectListener.redirected(request.uri(), target, response.statusCode());
                request = redirectedRequest(request, target, response.statusCode());
                continue;
            }
            return new FetchResult(request.uri(), response);
        }
        throw new IOException("Zu viele Weiterleitungen (mehr als " + MAX_REDIRECTS
                + "): " + initialRequest.uri());
    }

    private static HttpRequest redirectedRequest(HttpRequest request,
                                                 URI target,
                                                 int statusCode) {
        boolean switchToGet = (statusCode == 301 || statusCode == 302)
                && request.method().equals("POST");
        switchToGet |= statusCode == 303
                && !request.method().equals("GET") && !request.method().equals("HEAD");

        HttpRequest redirected = switchToGet
                ? request.asGetWithoutBody(target)
                : request.withUri(target);
        if (!sameOrigin(request.uri(), target)) {
            redirected = redirected.withoutSensitiveHeaders();
        }
        return redirected;
    }

    private static boolean sameOrigin(URI first, URI second) {
        String firstScheme = normalizedScheme(first);
        String secondScheme = normalizedScheme(second);
        if (!firstScheme.equals(secondScheme)) {
            return false;
        }
        String firstHost = first.getHost();
        String secondHost = second.getHost();
        if (firstHost == null || secondHost == null
                || !firstHost.equalsIgnoreCase(secondHost)) {
            return false;
        }
        return effectivePort(first, firstScheme) == effectivePort(second, secondScheme);
    }

    private static String normalizedScheme(URI uri) {
        return uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    }

    private static int effectivePort(URI uri, String scheme) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return scheme.equals("https") ? 443 : 80;
    }

    @FunctionalInterface
    interface UriValidator {
        void validate(URI uri) throws IOException;
    }
}

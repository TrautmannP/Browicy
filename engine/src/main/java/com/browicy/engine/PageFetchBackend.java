package com.browicy.engine;

import com.browicy.engine.js.JsCookieStore;
import com.browicy.engine.js.JsFetchBackend;
import com.browicy.engine.js.JsFetchRequest;
import com.browicy.engine.js.JsFetchResponse;
import com.browicy.engine.net.FetchResource;
import com.browicy.engine.net.FetchResourceLoad;
import com.browicy.engine.net.HttpHeaders;
import com.browicy.engine.net.HttpRequest;
import com.browicy.engine.net.ResourceLoad;
import com.browicy.engine.net.SubResourceLoader;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

final class PageFetchBackend implements JsFetchBackend, ResourceLoad {

    private final SubResourceLoader loader;
    private final String documentOrigin;
    private final JsCookieStore cookieStore;
    private final Set<FetchResourceLoad> activeLoads = ConcurrentHashMap.newKeySet();
    private volatile boolean cancelled;

    PageFetchBackend(SubResourceLoader loader, String documentUrl) {
        this(loader, documentUrl, null);
    }

    PageFetchBackend(SubResourceLoader loader, String documentUrl, JsCookieStore cookieStore) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.documentOrigin = originOf(documentUrl);
        this.cookieStore = cookieStore;
    }

    @Override
    public CompletableFuture<JsFetchResponse> fetch(JsFetchRequest request) {
        Objects.requireNonNull(request, "request");
        URI uri = request.uri();
        if (cancelled) {
            return CompletableFuture.failedFuture(
                    new IOException("Die Seite wurde geschlossen"));
        }
        FetchResourceLoad load;
        try {
            load = loader.fetchAsync(toHttpRequest(request));
        } catch (RuntimeException invalidRequest) {
            return CompletableFuture.failedFuture(invalidRequest);
        }
        activeLoads.add(load);
        if (cancelled) {
            load.cancel();
        }
        return load.future()
                .whenComplete((resource, failure) -> activeLoads.remove(load))
                .thenApply(resource -> toResponse(uri, resource));
    }


    private static HttpRequest toHttpRequest(JsFetchRequest request) {
        HttpHeaders headers = new HttpHeaders();
        for (JsFetchRequest.Header header : request.headers()) {
            if (!isForbiddenRequestHeader(header.name())) {
                headers.add(header.name(), header.value());
            }
        }
        return new HttpRequest(request.method(), request.uri(), headers, request.body());
    }

    private static boolean isForbiddenRequestHeader(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "accept-charset", "accept-encoding", "access-control-request-headers",
                    "access-control-request-method", "connection", "content-length",
                    "cookie", "cookie2", "date", "dnt", "expect", "host",
                    "keep-alive", "origin", "referer", "te", "trailer",
                    "transfer-encoding", "upgrade", "user-agent", "via" -> true;
            default -> normalized.startsWith("proxy-") || normalized.startsWith("sec-");
        };
    }

    @Override
    public boolean cancel() {
        cancelled = true;
        boolean anyCancelled = false;
        for (FetchResourceLoad load : List.copyOf(activeLoads)) {
            anyCancelled |= load.cancel();
        }
        return anyCancelled;
    }

    private JsFetchResponse toResponse(URI requestUri, FetchResource resource) {
        requireCorsAllowed(requestUri, resource);
        storeCookies(resource);
        List<JsFetchResponse.Header> headers = new ArrayList<>();
        for (String name : resource.headers().names()) {
            if (name.equals("set-cookie") || name.equals("set-cookie2")) {
                continue;
            }
            for (String value : resource.headers().all(name)) {
                headers.add(new JsFetchResponse.Header(name, value));
            }
        }
        return new JsFetchResponse(
                resource.uri().toString(),
                resource.statusCode(),
                resource.statusText(),
                headers,
                resource.bodyText());
    }

    private void storeCookies(FetchResource resource) {
        if (cookieStore == null) {
            return;
        }
        for (String value : resource.headers().all("set-cookie")) {
            cookieStore.storeFromHttp(resource.uri(), value);
        }
    }

    private void requireCorsAllowed(URI requestUri, FetchResource resource) {
        if (documentOrigin != null
                && documentOrigin.equals(originOf(requestUri.toString()))
                && documentOrigin.equals(originOf(resource.uri().toString()))) {
            return;
        }
        String allowOrigin = resource.headers().first("Access-Control-Allow-Origin");
        if (allowOrigin != null) {
            allowOrigin = allowOrigin.strip();
            if (allowOrigin.equals("*")
                    || (documentOrigin != null
                            && documentOrigin.equals(originOf(allowOrigin)))) {
                return;
            }
        }
        throw new CorsDeniedException(
                "Cross-Origin-Anfrage blockiert: " + resource.uri()
                        + " (fehlender oder unpassender Access-Control-Allow-Origin-Header)");
    }

    static String originOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(url.strip());
        } catch (java.net.URISyntaxException invalid) {
            return null;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            return null;
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return null;
        }
        int defaultPort = scheme.equals("https") ? 443 : 80;
        int port = uri.getPort();
        String portSuffix = port == -1 || port == defaultPort ? "" : ":" + port;
        return scheme + "://" + host.toLowerCase(Locale.ROOT) + portSuffix;
    }

    static final class CorsDeniedException extends RuntimeException {
        CorsDeniedException(String message) {
            super(message);
        }
    }
}

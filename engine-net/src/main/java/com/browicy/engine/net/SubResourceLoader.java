package com.browicy.engine.net;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SubResourceLoader implements AutoCloseable {

    private static final System.Logger LOGGER = System.getLogger(SubResourceLoader.class.getName());

    private final HttpClient client;
    private final ExecutorService executor;
    private final List<NetworkRequestObserver> observers = new CopyOnWriteArrayList<>();

    public SubResourceLoader() {
        this(new HttpClient());
    }

    public SubResourceLoader(HttpClient client) {
        this(client, Executors.newVirtualThreadPerTaskExecutor());
    }

    SubResourceLoader(HttpClient client, ExecutorService executor) {
        this.client = Objects.requireNonNull(client, "client");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public SubResourceLoad loadAsync(URI uri, NetworkResourceType resourceType) {
        Objects.requireNonNull(uri, "uri");
        if (resourceType != NetworkResourceType.STYLESHEET
                && resourceType != NetworkResourceType.SCRIPT) {
            throw new IllegalArgumentException("Keine Subresource-Art: " + resourceType);
        }
        validateHttpUri(uri);

        long requestId = RequestIds.next();
        CompletableFuture<TextResource> future = new CompletableFuture<>();
        SubResourceLoad load = new SubResourceLoad(
                requestId,
                uri,
                resourceType,
                future,
                resource -> emit(new NetworkRequestEvent.Loaded(
                        requestId, Instant.now(), resource.uri(), resource.statusCode(),
                        resource.sizeBytes(), resource.resourceType())),
                failure -> emit(new NetworkRequestEvent.Failed(
                        requestId, Instant.now(), uri.toString(), failure, resourceType)),
                () -> emit(new NetworkRequestEvent.Cancelled(
                        requestId, Instant.now(), uri.toString(), resourceType)));
        emit(new NetworkRequestEvent.Started(requestId, Instant.now(), uri.toString(), resourceType));

        try {
            executor.execute(() -> fetch(load));
        } catch (RuntimeException rejected) {
            load.completeFailed(rejected);
        }
        return load;
    }

    public TextResource load(URI uri, NetworkResourceType resourceType) throws IOException {
        try {
            return loadAsync(uri, resourceType).await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Laden unterbrochen: " + uri, exception);
        }
    }

    public void addObserver(NetworkRequestObserver observer) {
        observers.add(Objects.requireNonNull(observer, "observer"));
    }

    public void removeObserver(NetworkRequestObserver observer) {
        observers.remove(observer);
    }

    private void fetch(SubResourceLoad load) {
        try {
            HttpResourceFetcher.FetchResult result = HttpResourceFetcher.fetch(
                    client,
                    load.uri(),
                    acceptFor(load.resourceType()),
                    load::isCancelled,
                    (from, to, statusCode) -> emit(new NetworkRequestEvent.Redirected(
                            load.id(), Instant.now(), from, to, statusCode, load.resourceType())));
            HttpResponse response = result.response();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode() + " für " + result.uri());
            }
            load.completeLoaded(new TextResource(
                    result.uri(),
                    response.statusCode(),
                    TextResourceDecoder.decode(response, load.resourceType()),
                    response.body().length,
                    load.resourceType()));
        } catch (CancellationException cancellation) {
            load.cancel();
        } catch (Exception exception) {
            load.completeFailed(exception);
        }
    }

    private void emit(NetworkRequestEvent event) {
        for (NetworkRequestObserver observer : observers) {
            try {
                observer.onEvent(event);
            } catch (RuntimeException exception) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Beobachter eines Subresource-Requests warf eine Exception", exception);
            }
        }
    }

    private static String acceptFor(NetworkResourceType type) {
        return switch (type) {
            case STYLESHEET -> "text/css,*/*;q=0.1";
            case SCRIPT -> "text/javascript,application/javascript,*/*;q=0.1";
            case DOCUMENT -> throw new IllegalArgumentException("Dokument ist keine Subresource");
        };
    }

    private static void validateHttpUri(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Nicht unterstützte Subresource-URL: " + uri);
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("Subresource-URL ohne Host: " + uri);
        }
    }

    @Override
    public void close() {
        executor.close();
    }
}

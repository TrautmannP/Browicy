package com.browicy.engine.net;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PageLoader implements AutoCloseable {

    public record Page(URI uri, int statusCode, String html, int sizeBytes) {
        public Page(URI uri, int statusCode, String html) {
            this(uri, statusCode, html,
                    html == null ? 0 : html.getBytes(StandardCharsets.UTF_8).length);
        }
    }

    private static final Pattern HAS_SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.\\-]*:");

    private static final Pattern META_CHARSET = Pattern.compile(
            "<meta[^>]+charset\\s*=\\s*[\"']?([A-Za-z0-9_\\-.:]+)", Pattern.CASE_INSENSITIVE);

    private static final int CHARSET_SNIFF_BYTES = 2048;

    private static final System.Logger LOGGER = System.getLogger(PageLoader.class.getName());

    private final HttpClient client;
    private final ExecutorService executor;
    private final List<PageLoadObserver> observers = new CopyOnWriteArrayList<>();
    private final List<NetworkRequestObserver> networkObservers = new CopyOnWriteArrayList<>();

    public PageLoader() {
        this(new HttpClient());
    }

    public PageLoader(HttpClient client) {
        this(client, Executors.newVirtualThreadPerTaskExecutor());
    }

    public PageLoader(HttpClient client, ExecutorService executor) {
        this.client = Objects.requireNonNull(client, "client");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public static URI normalize(String input) {
        String trimmed = input == null ? "" : input.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Leere URL");
        }
        if (!HAS_SCHEME.matcher(trimmed).find()) {
            trimmed = "http://" + trimmed;
        }
        return URI.create(trimmed);
    }

    public Page load(String url) throws IOException {
        try {
            return loadAsync(url).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Laden unterbrochen: " + url, e);
        }
    }

    public PageLoad loadAsync(String url) {
        long loadId = RequestIds.next();
        PageLoad load = new PageLoad(loadId, url);
        load.onDone(this::emitTerminalEvent);
        Instant startedAt = Instant.now();
        emit(new PageLoadEvent.Started(loadId, startedAt, url));
        emit(new NetworkRequestEvent.Started(
                loadId, startedAt, url, NetworkResourceType.DOCUMENT));
        try {
            executor.execute(() -> {
                try {
                    load.completeLoaded(load(loadId, url, load::isCancelled));
                } catch (CancellationException alreadyCancelled) {
                } catch (Exception e) {
                    load.completeFailed(e);
                }
            });
        } catch (RuntimeException rejected) {
            load.completeFailed(rejected);
        }
        return load;
    }

    public void addObserver(PageLoadObserver observer) {
        observers.add(Objects.requireNonNull(observer, "observer"));
    }

    public void removeObserver(PageLoadObserver observer) {
        observers.remove(observer);
    }

    public void addNetworkObserver(NetworkRequestObserver observer) {
        networkObservers.add(Objects.requireNonNull(observer, "observer"));
    }

    public void removeNetworkObserver(NetworkRequestObserver observer) {
        networkObservers.remove(observer);
    }

    private Page load(long loadId, String url, BooleanSupplier cancelled) throws IOException {
        URI initialUri = normalize(url);
        HttpResourceFetcher.FetchResult result = HttpResourceFetcher.fetch(
                client,
                initialUri,
                "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8",
                cancelled,
                (from, to, statusCode) -> {
                    Instant at = Instant.now();
                    emit(new PageLoadEvent.Redirected(loadId, at, from, to, statusCode));
                    emit(new NetworkRequestEvent.Redirected(loadId, at, from, to, statusCode,
                            NetworkResourceType.DOCUMENT));
                });
        HttpResponse response = result.response();
        return new Page(result.uri(), response.statusCode(), decodeHtml(response), response.body().length);
    }

    private void emitTerminalEvent(PageLoad load) {
        Instant now = Instant.now();
        switch (load.state()) {
            case LOADED -> {
                Page page = load.page().orElseThrow();
                emit(new PageLoadEvent.Loaded(load.id(), now, page));
                emit(new NetworkRequestEvent.Loaded(load.id(), now, page.uri(), page.statusCode(),
                        page.sizeBytes(), NetworkResourceType.DOCUMENT));
            }
            case FAILED -> {
                Exception failure = load.failure().orElseThrow();
                emit(new PageLoadEvent.Failed(load.id(), now, load.url(), failure));
                emit(new NetworkRequestEvent.Failed(load.id(), now, load.url(), failure,
                        NetworkResourceType.DOCUMENT));
            }
            case CANCELLED -> {
                emit(new PageLoadEvent.Cancelled(load.id(), now, load.url()));
                emit(new NetworkRequestEvent.Cancelled(load.id(), now, load.url(),
                        NetworkResourceType.DOCUMENT));
            }
            case LOADING -> throw new IllegalStateException(
                    "onDone-Listener wurde außerhalb eines Endzustands aufgerufen");
        }
    }

    private void emit(PageLoadEvent event) {
        for (PageLoadObserver observer : observers) {
            try {
                observer.onEvent(event);
            } catch (RuntimeException e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Beobachter eines Ladevorgangs warf eine Exception", e);
            }
        }
    }

    private void emit(NetworkRequestEvent event) {
        for (NetworkRequestObserver observer : networkObservers) {
            try {
                observer.onEvent(event);
            } catch (RuntimeException e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Netzwerkbeobachter eines Ladevorgangs warf eine Exception", e);
            }
        }
    }

    @Override
    public void close() {
        executor.close();
        client.close();
    }

    private static String decodeHtml(HttpResponse response) {
        Charset charset = response.charsetFromHeaders()
                .or(() -> sniffMetaCharset(response.body()))
                .orElse(StandardCharsets.UTF_8);
        return new String(response.body(), charset);
    }

    private static Optional<Charset> sniffMetaCharset(byte[] body) {
        String prefix = new String(body, 0, Math.min(body.length, CHARSET_SNIFF_BYTES),
                StandardCharsets.ISO_8859_1);
        Matcher matcher = META_CHARSET.matcher(prefix);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Charset.forName(matcher.group(1)));
        } catch (IllegalArgumentException unknownCharset) {
            return Optional.empty();
        }
    }
}

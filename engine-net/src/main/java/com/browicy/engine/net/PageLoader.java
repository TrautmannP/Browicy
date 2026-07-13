package com.browicy.engine.net;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public final class PageLoader implements AutoCloseable {

    public record Page(URI uri, int statusCode, String html) {
    }

    private static final int MAX_REDIRECTS = 10;

    private static final Pattern HAS_SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.\\-]*:");

    private static final Pattern META_CHARSET = Pattern.compile(
            "<meta[^>]+charset\\s*=\\s*[\"']?([A-Za-z0-9_\\-.:]+)", Pattern.CASE_INSENSITIVE);

    private static final int CHARSET_SNIFF_BYTES = 2048;

    private static final System.Logger LOGGER = System.getLogger(PageLoader.class.getName());

    private final HttpClient client;
    private final ExecutorService executor;
    private final List<PageLoadObserver> observers = new CopyOnWriteArrayList<>();
    private final AtomicLong nextLoadId = new AtomicLong(1);

    public PageLoader() {
        this(new HttpClient());
    }

    public PageLoader(HttpClient client) {
        this(client, Executors.newVirtualThreadPerTaskExecutor());
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
        long loadId = nextLoadId.getAndIncrement();
        PageLoad load = new PageLoad(loadId, url);
        load.onDone(this::emitTerminalEvent);
        emit(new PageLoadEvent.Started(loadId, Instant.now(), url));
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
        observers.add(observer);
    }

    public void removeObserver(PageLoadObserver observer) {
        observers.remove(observer);
    }

    private Page load(long loadId, String url, BooleanSupplier cancelled) throws IOException {
        URI uri = normalize(url);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            if (cancelled.getAsBoolean()) {
                throw new CancellationException("Ladevorgang abgebrochen: " + url);
            }
            HttpResponse response = client.get(uri);
            String location = response.location();
            if (response.isRedirect() && location != null) {
                URI target = uri.resolve(location.strip());
                emit(new PageLoadEvent.Redirected(loadId, Instant.now(), uri, target,
                        response.statusCode()));
                uri = target;
                continue;
            }
            return new Page(uri, response.statusCode(), decodeHtml(response));
        }
        throw new IOException("Zu viele Weiterleitungen (mehr als " + MAX_REDIRECTS + "): " + url);
    }

    private void emitTerminalEvent(PageLoad load) {
        Instant now = Instant.now();
        switch (load.state()) {
            case LOADED -> emit(new PageLoadEvent.Loaded(load.id(), now, load.page().orElseThrow()));
            case FAILED -> emit(new PageLoadEvent.Failed(load.id(), now, load.url(),
                    load.failure().orElseThrow()));
            case CANCELLED -> emit(new PageLoadEvent.Cancelled(load.id(), now, load.url()));
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

    @Override
    public void close() {
        executor.close();
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

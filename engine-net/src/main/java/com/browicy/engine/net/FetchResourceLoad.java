package com.browicy.engine.net;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public final class FetchResourceLoad implements ResourceLoad {

    private final long id;
    private final URI uri;
    private final CompletableFuture<FetchResource> future;
    private final Consumer<FetchResource> loadedListener;
    private final Consumer<Exception> failedListener;
    private final Runnable cancelledListener;
    private volatile boolean cancelled;

    FetchResourceLoad(long id,
                      URI uri,
                      CompletableFuture<FetchResource> future,
                      Consumer<FetchResource> loadedListener,
                      Consumer<Exception> failedListener,
                      Runnable cancelledListener) {
        this.id = id;
        this.uri = Objects.requireNonNull(uri, "uri");
        this.future = Objects.requireNonNull(future, "future");
        this.loadedListener = Objects.requireNonNull(loadedListener, "loadedListener");
        this.failedListener = Objects.requireNonNull(failedListener, "failedListener");
        this.cancelledListener = Objects.requireNonNull(cancelledListener, "cancelledListener");
    }

    public long id() { return id; }
    public URI uri() { return uri; }
    public NetworkResourceType resourceType() { return NetworkResourceType.FETCH; }
    public CompletableFuture<FetchResource> future() { return future; }
    public boolean isDone() { return future.isDone(); }
    public boolean isCancelled() { return cancelled || future.isCancelled(); }

    @Override
    public synchronized boolean cancel() {
        if (future.isDone()) return false;
        cancelled = true;
        cancelledListener.run();
        return future.cancel(false);
    }

    public FetchResource await() throws IOException, InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException io) throw io;
            if (cause instanceof CancellationException cancellation) throw cancellation;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IOException(cause);
        }
    }

    synchronized void completeLoaded(FetchResource resource) {
        if (future.isDone()) return;
        loadedListener.accept(resource);
        future.complete(resource);
    }

    synchronized void completeFailed(Exception failure) {
        if (future.isDone()) return;
        failedListener.accept(failure);
        future.completeExceptionally(failure);
    }
}

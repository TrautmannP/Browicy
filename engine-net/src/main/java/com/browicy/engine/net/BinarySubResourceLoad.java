package com.browicy.engine.net;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public final class BinarySubResourceLoad implements ResourceLoad {

    private final long id;
    private final URI uri;
    private final NetworkResourceType resourceType;
    private final CompletableFuture<BinaryResource> future;
    private final Consumer<BinaryResource> loadedListener;
    private final Consumer<Exception> failedListener;
    private final Runnable cancelledListener;
    private volatile boolean cancelled;

    BinarySubResourceLoad(long id,
                          URI uri,
                          NetworkResourceType resourceType,
                          CompletableFuture<BinaryResource> future,
                          Consumer<BinaryResource> loadedListener,
                          Consumer<Exception> failedListener,
                          Runnable cancelledListener) {
        this.id = id;
        this.uri = Objects.requireNonNull(uri, "uri");
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType");
        this.future = Objects.requireNonNull(future, "future");
        this.loadedListener = Objects.requireNonNull(loadedListener, "loadedListener");
        this.failedListener = Objects.requireNonNull(failedListener, "failedListener");
        this.cancelledListener = Objects.requireNonNull(cancelledListener, "cancelledListener");
    }

    public long id() { return id; }
    public URI uri() { return uri; }
    public NetworkResourceType resourceType() { return resourceType; }
    public CompletableFuture<BinaryResource> future() { return future; }
    public boolean isDone() { return future.isDone(); }
    public boolean isCancelled() { return cancelled || future.isCancelled(); }

    @Override
    public synchronized boolean cancel() {
        if (future.isDone()) return false;
        cancelled = true;
        cancelledListener.run();
        return future.cancel(false);
    }

    public BinaryResource await() throws IOException, InterruptedException {
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

    synchronized void completeLoaded(BinaryResource resource) {
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

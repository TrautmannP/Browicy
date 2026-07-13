package com.browicy.engine.net;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

public final class PageLoad {

    private static final System.Logger LOGGER = System.getLogger(PageLoad.class.getName());

    public enum State {
        LOADING,
        LOADED,
        FAILED,
        CANCELLED
    }

    private final long id;
    private final String url;
    private final CountDownLatch done = new CountDownLatch(1);
    private final List<Consumer<PageLoad>> listeners = new CopyOnWriteArrayList<>();

    private volatile State state = State.LOADING;
    private volatile PageLoader.Page page;
    private volatile Exception failure;

    PageLoad(long id, String url) {
        this.id = id;
        this.url = url;
    }

    public long id() {
        return id;
    }

    public String url() {
        return url;
    }

    public State state() {
        return state;
    }

    public boolean isDone() {
        return state != State.LOADING;
    }

    public boolean isCancelled() {
        return state == State.CANCELLED;
    }

    public Optional<PageLoader.Page> page() {
        return Optional.ofNullable(page);
    }

    public Optional<Exception> failure() {
        return Optional.ofNullable(failure);
    }

    public void cancel() {
        finish(State.CANCELLED, null, null);
    }

    public void onDone(Consumer<PageLoad> listener) {
        synchronized (this) {
            if (state == State.LOADING) {
                listeners.add(listener);
                return;
            }
        }
        notifySafely(listener);
    }

    public PageLoader.Page await() throws java.io.IOException, InterruptedException {
        done.await();
        switch (state) {
            case LOADED:
                return page;
            case CANCELLED:
                throw new CancellationException("Ladevorgang abgebrochen: " + url);
            case FAILED:
                if (failure instanceof java.io.IOException io) {
                    throw io;
                }
                if (failure instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new java.io.IOException(failure);
            default:
                throw new IllegalStateException("Unerwarteter Zustand: " + state);
        }
    }

    void completeLoaded(PageLoader.Page result) {
        finish(State.LOADED, result, null);
    }

    void completeFailed(Exception cause) {
        finish(State.FAILED, null, cause);
    }

    private void finish(State terminal, PageLoader.Page result, Exception cause) {
        synchronized (this) {
            if (state != State.LOADING) {
                return;
            }
            this.page = result;
            this.failure = cause;
            this.state = terminal;
        }
        // Erst die Listener, dann das Latch: Wer await() verlässt, kann sich
        // darauf verlassen, dass alle Benachrichtigungen bereits erfolgt sind.
        for (Consumer<PageLoad> listener : listeners) {
            notifySafely(listener);
        }
        done.countDown();
    }

    private void notifySafely(Consumer<PageLoad> listener) {
        try {
            listener.accept(this);
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Listener eines Ladevorgangs warf eine Exception", e);
        }
    }
}

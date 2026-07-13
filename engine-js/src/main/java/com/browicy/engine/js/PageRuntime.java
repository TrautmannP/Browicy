package com.browicy.engine.js;

import com.browicy.engine.dom.Event;
import com.browicy.engine.dom.Node;
import java.util.concurrent.CompletableFuture;

/** Persistent, document-bound JavaScript runtime with a serialized event loop. */
public interface PageRuntime extends PageTaskQueue, AutoCloseable {

    static PageRuntime closed() {
        return ClosedPageRuntime.INSTANCE;
    }

    JsExecutionResult execute(JavaScriptSource source);

    /** Enqueues a DOM event and returns immediately. */
    void dispatchEvent(Node target, Event event);

    /** Enqueues a DOM event and exposes completion for lifecycle coordination and tests. */
    CompletableFuture<Boolean> submitEvent(Node target, Event event);

    default void enqueueTask(Runnable task) {
        submitTask(task);
    }

    CompletableFuture<Void> submitTask(Runnable task);

    default void enqueueMicrotask(Runnable task) {
        enqueueMicrotask(new PageTask.Callback(task));
    }

    /** Waits until all tasks that were queued before this call have completed. */
    void awaitIdle();

    boolean isClosed();

    @Override
    void close();

    @Override
    default void shutdown() {
        close();
    }

    enum ClosedPageRuntime implements PageRuntime {
        INSTANCE;

        @Override public JsExecutionResult execute(JavaScriptSource source) {
            throw new IllegalStateException("PageRuntime ist geschlossen");
        }
        @Override public void dispatchEvent(Node target, Event event) { }
        @Override public CompletableFuture<Boolean> submitEvent(Node target, Event event) {
            return CompletableFuture.failedFuture(new IllegalStateException("PageRuntime ist geschlossen"));
        }
        @Override public CompletableFuture<Void> submitTask(Runnable task) {
            return CompletableFuture.failedFuture(new IllegalStateException("PageRuntime ist geschlossen"));
        }
        @Override public void enqueue(PageTask task) { }
        @Override public void enqueueMicrotask(PageTask task) { }
        @Override public void awaitIdle() { }
        @Override public boolean isClosed() { return true; }
        @Override public void close() { }
    }
}

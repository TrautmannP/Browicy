package com.browicy.engine.js;

import com.browicy.engine.dom.Event;
import com.browicy.engine.dom.Node;
import java.util.concurrent.CompletableFuture;

public interface PageRuntime extends PageTaskQueue, AutoCloseable {

    static PageRuntime closed() {
        return ClosedPageRuntime.INSTANCE;
    }

    JsExecutionResult execute(JavaScriptSource source);

    void dispatchEvent(Node target, Event event);

    CompletableFuture<Boolean> submitEvent(Node target, Event event);

    default void enqueueTask(Runnable task) {
        submitTask(task);
    }

    CompletableFuture<Void> submitTask(Runnable task);

    default void enqueueMicrotask(Runnable task) {
        enqueueMicrotask(new PageTask.Callback(task));
    }

    void awaitIdle();

    JsExecutionResult snapshot();

    default PageRuntimeDiagnostics diagnostics() {
        return PageRuntimeDiagnostics.closedRuntime();
    }

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
        @Override public JsExecutionResult snapshot() { return JsExecutionResult.EMPTY; }
        @Override public boolean isClosed() { return true; }
        @Override public void close() { }
    }
}

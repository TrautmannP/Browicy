package com.browicy.engine.js;

/** Queue contract used by network, UI and timer producers without exposing the JS context. */
public interface PageTaskQueue {

    void enqueue(PageTask task);

    void enqueueMicrotask(PageTask task);

    void shutdown();
}

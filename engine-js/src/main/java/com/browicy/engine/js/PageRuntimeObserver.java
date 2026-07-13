package com.browicy.engine.js;

/** Observes completed event-loop turns, primarily to flush batched document invalidations. */
@FunctionalInterface
public interface PageRuntimeObserver {

    PageRuntimeObserver NO_OP = task -> { };

    void afterTask(PageTask task);
}

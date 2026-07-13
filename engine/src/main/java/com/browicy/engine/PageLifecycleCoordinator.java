package com.browicy.engine;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.DocumentReadyState;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Event;
import com.browicy.engine.js.PageRuntime;
import java.util.Objects;

/** Drives the document loading lifecycle on the page runtime thread. */
final class PageLifecycleCoordinator {

    private final Document document;
    private final PageRuntime runtime;

    PageLifecycleCoordinator(Document document, PageRuntime runtime) {
        this.document = Objects.requireNonNull(document, "document");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    void markInteractive() {
        runtime.enqueueTask(() -> document.transitionTo(DocumentReadyState.INTERACTIVE));
        runtime.submitEvent(document, new Event("DOMContentLoaded", true, false)).join();
    }

    void markComplete() {
        runtime.enqueueTask(() -> document.transitionTo(DocumentReadyState.COMPLETE));
        Element body = document.getBody();
        if (body != null) {
            runtime.submitEvent(body, new Event("load", false, false)).join();
        } else {
            runtime.awaitIdle();
        }
    }
}

package com.browicy.engine;

import com.browicy.engine.css.StyleSheetRegistry;
import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.DocumentReadyState;
import com.browicy.engine.js.PageRuntime;
import com.browicy.engine.net.SubResourceLoad;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns every resource whose lifetime is bound to one loaded document. */
public final class PageSession implements AutoCloseable {

    private final Document document;
    private final PageRuntime runtime;
    private final StyleSheetRegistry styleSheets;
    private final CompletableFuture<Void> resourcesLoaded;
    private final List<SubResourceLoad> cancellableLoads;
    private final DocumentUpdateCoordinator updateCoordinator;
    private final Runnable onClose;
    private final AtomicBoolean closed = new AtomicBoolean();

    PageSession(Document document,
                PageRuntime runtime,
                StyleSheetRegistry styleSheets,
                CompletableFuture<Void> resourcesLoaded,
                List<SubResourceLoad> cancellableLoads,
                DocumentUpdateCoordinator updateCoordinator,
                Runnable onClose) {
        this.document = Objects.requireNonNull(document, "document");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.styleSheets = Objects.requireNonNull(styleSheets, "styleSheets");
        this.resourcesLoaded = Objects.requireNonNull(resourcesLoaded, "resourcesLoaded");
        this.cancellableLoads = List.copyOf(cancellableLoads);
        this.updateCoordinator = updateCoordinator;
        this.onClose = Objects.requireNonNull(onClose, "onClose");
    }

    public static PageSession completed(Document document) {
        Objects.requireNonNull(document, "document")
                .transitionTo(DocumentReadyState.COMPLETE);
        return new PageSession(
                document,
                PageRuntime.closed(),
                new StyleSheetRegistry(),
                CompletableFuture.completedFuture(null),
                List.of(),
                null,
                () -> { });
    }

    public Document document() {
        return document;
    }

    public PageRuntime runtime() {
        return runtime;
    }

    public StyleSheetRegistry styleSheets() {
        return styleSheets;
    }

    public CompletableFuture<Void> resourcesLoaded() {
        return resourcesLoaded;
    }

    public void awaitResources() {
        resourcesLoaded.join();
        runtime.awaitIdle();
    }

    public boolean isClosed() {
        return closed.get();
    }

    /** Backwards-compatible alias for closing a page session. */
    public void cancel() {
        close();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            cancellableLoads.forEach(SubResourceLoad::cancel);
            if (updateCoordinator != null) {
                updateCoordinator.close();
            }
        } finally {
            try {
                runtime.close();
            } finally {
                onClose.run();
            }
        }
    }
}

package com.browicy.engine;

import com.browicy.engine.dom.Document;
import com.browicy.engine.net.SubResourceLoad;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class PageSession {

    private final Document document;
    private final CompletableFuture<Void> resourcesLoaded;
    private final List<SubResourceLoad> cancellableLoads;

    PageSession(Document document,
                CompletableFuture<Void> resourcesLoaded,
                List<SubResourceLoad> cancellableLoads) {
        this.document = Objects.requireNonNull(document, "document");
        this.resourcesLoaded = Objects.requireNonNull(resourcesLoaded, "resourcesLoaded");
        this.cancellableLoads = List.copyOf(cancellableLoads);
    }

    public static PageSession completed(Document document) {
        return new PageSession(document, CompletableFuture.completedFuture(null), List.of());
    }

    public Document document() {
        return document;
    }

    public CompletableFuture<Void> resourcesLoaded() {
        return resourcesLoaded;
    }

    public void awaitResources() {
        resourcesLoaded.join();
    }

    public void cancel() {
        cancellableLoads.forEach(SubResourceLoad::cancel);
    }
}

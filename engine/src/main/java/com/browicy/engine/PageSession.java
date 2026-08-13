package com.browicy.engine;

import com.browicy.engine.css.StyleSheetRegistry;
import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.DocumentReadyState;
import com.browicy.engine.js.JsCookieStore;
import com.browicy.engine.js.PageRuntime;
import com.browicy.engine.net.ResourceLoad;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Eine Tab-/Session-Instanz: hält den aktuellen Page-Load (Dokument, Runtime,
 * Ressourcen) und kann bei einer JavaScript-Navigation atomar auf einen neuen
 * Page-Load umschalten, ohne ihre Identität zu verlieren.
 */
public final class PageSession implements AutoCloseable {

    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object stateLock = new Object();
    private volatile State state;
    private long stateGeneration;
    private int pendingNavigations;

    PageSession(Document document,
                PageRuntime runtime,
                StyleSheetRegistry styleSheets,
                ImageResourceRegistry images,
                FontResourceRegistry fonts,
                JsCookieStore cookies,
                CompletableFuture<Void> resourcesLoaded,
                List<ResourceLoad> cancellableLoads,
                DocumentUpdateCoordinator updateCoordinator,
                PageLoadProgress progress,
                Runnable onClose) {
        this(new State(document, runtime, styleSheets, images, fonts, cookies,
                resourcesLoaded, cancellableLoads, updateCoordinator, progress, onClose));
    }

    private PageSession(State state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public static PageSession completed(Document document) {
        Objects.requireNonNull(document, "document")
                .transitionTo(DocumentReadyState.COMPLETE);
        PageLoadProgress progress = new PageLoadProgress();
        progress.phase(PageLoadProgress.Phase.COMPLETE, "");
        return new PageSession(
                document,
                PageRuntime.closed(),
                new StyleSheetRegistry(),
                new ImageResourceRegistry(),
                new FontResourceRegistry(),
                new JsCookieStore(),
                CompletableFuture.completedFuture(null),
                List.of(),
                null,
                progress,
                () -> { });
    }

    public Document document() {
        return state.document;
    }

    public PageRuntime runtime() {
        return state.runtime;
    }

    public StyleSheetRegistry styleSheets() {
        return state.styleSheets;
    }

    public ImageResourceRegistry images() {
        return state.images;
    }

    public FontResourceRegistry fonts() {
        return state.fonts;
    }

    public JsCookieStore cookies() {
        return state.cookies;
    }

    public CompletableFuture<Void> resourcesLoaded() {
        return state.resourcesLoaded;
    }

    public PageLoadProgress progress() {
        return state.progress;
    }

    /**
     * Wartet, bis der aktuelle Page-Load seine Ressourcen abgeschlossen hat.
     * Erfolgt währenddessen eine Navigation, wird auf den Folge-Load gewartet.
     */
    public void awaitResources() {
        while (true) {
            State current = state;
            current.resourcesLoaded.join();
            if (state == current) {
                try {
                    current.runtime.awaitIdle();
                } catch (RuntimeException closedRuntime) {
                    // Runtime wurde durch eine Navigation geschlossen – neuer Zustand folgt.
                }
            }
            synchronized (stateLock) {
                if (pendingNavigations == 0 && state == current) {
                    return;
                }
                try {
                    stateLock.wait(10);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** Zähler der abgeschlossenen Navigationen (für {@link #awaitNavigation}). */
    public long navigationGeneration() {
        synchronized (stateLock) {
            return stateGeneration;
        }
    }

    /**
     * Wartet, bis eine ab der übergebenen Generation begonnene Navigation
     * abgeschlossen ist, und dann auf die Ressourcen des Folge-Loads.
     *
     * @return {@code false}, wenn das Zeitlimit verstrich, ohne dass eine
     *         Navigation die Generation weiterschaltete
     */
    public boolean awaitNavigation(long fromGeneration, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        synchronized (stateLock) {
            while (stateGeneration == fromGeneration) {
                if (closed.get()) {
                    return false;
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                stateLock.wait(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
            }
        }
        awaitResources();
        return true;
    }

    public boolean isClosed() {
        return closed.get();
    }

    public void cancel() {
        close();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        State current;
        synchronized (stateLock) {
            current = state;
            stateLock.notifyAll();
        }
        current.close();
    }

    void navigationStarted() {
        synchronized (stateLock) {
            pendingNavigations++;
            stateLock.notifyAll();
        }
    }

    void navigationFinished() {
        synchronized (stateLock) {
            pendingNavigations--;
            stateLock.notifyAll();
        }
    }

    State state() {
        return state;
    }

    void replaceState(State replacement) {
        Objects.requireNonNull(replacement, "replacement");
        State old;
        synchronized (stateLock) {
            if (closed.get()) {
                old = null;
            } else {
                old = state;
                state = replacement;
                stateGeneration++;
                stateLock.notifyAll();
            }
        }
        if (old == null) {
            replacement.close();
        } else if (old != replacement) {
            old.close();
        }
    }

    /**
     * Die Ressourcen eines einzelnen Page-Loads. Wird bei einer Navigation
     * innerhalb derselben {@link PageSession} gegen einen neuen State getauscht.
     */
    static final class State {

        private final Document document;
        private final PageRuntime runtime;
        private final StyleSheetRegistry styleSheets;
        private final ImageResourceRegistry images;
        private final FontResourceRegistry fonts;
        private final JsCookieStore cookies;
        private final CompletableFuture<Void> resourcesLoaded;
        private final List<ResourceLoad> cancellableLoads;
        private final DocumentUpdateCoordinator updateCoordinator;
        private final PageLoadProgress progress;
        private final Runnable onClose;

        private State(Document document,
                      PageRuntime runtime,
                      StyleSheetRegistry styleSheets,
                      ImageResourceRegistry images,
                      FontResourceRegistry fonts,
                      JsCookieStore cookies,
                      CompletableFuture<Void> resourcesLoaded,
                      List<ResourceLoad> cancellableLoads,
                      DocumentUpdateCoordinator updateCoordinator,
                      PageLoadProgress progress,
                      Runnable onClose) {
            this.document = Objects.requireNonNull(document, "document");
            this.runtime = Objects.requireNonNull(runtime, "runtime");
            this.styleSheets = Objects.requireNonNull(styleSheets, "styleSheets");
            this.images = Objects.requireNonNull(images, "images");
            this.fonts = Objects.requireNonNull(fonts, "fonts");
            this.cookies = Objects.requireNonNull(cookies, "cookies");
            this.resourcesLoaded = Objects.requireNonNull(resourcesLoaded, "resourcesLoaded");
            this.cancellableLoads = Objects.requireNonNull(cancellableLoads, "cancellableLoads");
            this.updateCoordinator = updateCoordinator;
            this.progress = progress == null ? new PageLoadProgress() : progress;
            this.onClose = Objects.requireNonNull(onClose, "onClose");
        }

        private void close() {
            cancellableLoads.forEach(ResourceLoad::cancel);
            if (updateCoordinator != null) {
                updateCoordinator.close();
            }
            runtime.close();
            onClose.run();
        }
    }
}

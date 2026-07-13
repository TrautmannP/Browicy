package com.browicy.engine;

import com.browicy.engine.css.StyleApplicator;
import com.browicy.engine.css.StyleSheetRegistry;
import com.browicy.engine.dom.Document;
import com.browicy.engine.html.DocumentResourceScanner;
import com.browicy.engine.html.DocumentResources;
import com.browicy.engine.html.ScriptResource;
import com.browicy.engine.html.StyleSheetResource;
import com.browicy.engine.js.JavaScriptEngine;
import com.browicy.engine.js.JavaScriptSource;
import com.browicy.engine.net.NetworkResourceType;
import com.browicy.engine.net.SubResourceLoad;
import com.browicy.engine.net.SubResourceLoader;
import com.browicy.engine.net.TextResource;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

final class PageResourceCoordinator {

    private static final System.Logger LOGGER = System.getLogger(PageResourceCoordinator.class.getName());

    private final DocumentResourceScanner scanner;
    private final SubResourceLoader resourceLoader;
    private final JavaScriptEngine javaScriptEngine;
    private final StyleApplicator styleApplicator;

    PageResourceCoordinator(SubResourceLoader resourceLoader, JavaScriptEngine javaScriptEngine) {
        this(new DocumentResourceScanner(), resourceLoader, javaScriptEngine, new StyleApplicator());
    }

    PageResourceCoordinator(DocumentResourceScanner scanner,
                            SubResourceLoader resourceLoader,
                            JavaScriptEngine javaScriptEngine,
                            StyleApplicator styleApplicator) {
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
        this.javaScriptEngine = Objects.requireNonNull(javaScriptEngine, "javaScriptEngine");
        this.styleApplicator = Objects.requireNonNull(styleApplicator, "styleApplicator");
    }

    PageSession load(Document document, PageUpdateListener listener) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(listener, "listener");
        DocumentResources resources = scanner.scan(document);
        StyleSheetRegistry styleSheets = new StyleSheetRegistry();
        registerInlineStyleSheets(resources, styleSheets);

        Object documentLock = new Object();
        LoadingState state = new LoadingState();
        List<SubResourceLoad> cancellableLoads = new ArrayList<>();
        List<CompletableFuture<Void>> styleTasks = new ArrayList<>();
        List<CompletableFuture<Void>> renderBlockingTasks = new ArrayList<>();

        synchronized (documentLock) {
            styleApplicator.apply(document, styleSheets);
        }

        for (StyleSheetResource styleSheet : resources.styleSheets()) {
            if (!(styleSheet instanceof StyleSheetResource.External external)) {
                continue;
            }
            SubResourceLoad load = resourceLoader.loadAsync(
                    external.uri(), NetworkResourceType.STYLESHEET);
            cancellableLoads.add(load);
            CompletableFuture<Void> task = load.future().handle((resource, failure) -> {
                if (resource == null) {
                    return null;
                }
                styleSheets.register(external.sourceOrder(), resource.content());
                synchronized (documentLock) {
                    if (!state.scriptsFinished) {
                        state.pendingStyleUpdates.add(resource.uri());
                        return null;
                    }
                    styleApplicator.apply(document, styleSheets);
                }
                notifySafely(listener,
                        new PageUpdate.StylesChanged(document, List.of(resource.uri())));
                return null;
            });
            styleTasks.add(task);
            if (external.renderBlocking()) {
                renderBlockingTasks.add(task);
            }
        }

        Iterable<JavaScriptSource> scripts = scriptSequence(resources, cancellableLoads);
        List<URI> initiallyApplied;
        synchronized (documentLock) {
            javaScriptEngine.runScriptSequence(document, scripts);
            state.scriptsFinished = true;
            initiallyApplied = List.copyOf(state.pendingStyleUpdates);
            state.pendingStyleUpdates.clear();
            styleApplicator.apply(document, styleSheets);
        }
        if (!initiallyApplied.isEmpty()) {
            notifySafely(listener, new PageUpdate.StylesChanged(document, initiallyApplied));
        }

        awaitRenderBlocking(renderBlockingTasks);

        CompletableFuture<Void> allStyles = CompletableFuture.allOf(
                styleTasks.toArray(CompletableFuture[]::new));
        return new PageSession(document, allStyles, cancellableLoads);
    }

    private static void registerInlineStyleSheets(DocumentResources resources,
                                                  StyleSheetRegistry registry) {
        for (StyleSheetResource resource : resources.styleSheets()) {
            if (resource instanceof StyleSheetResource.Inline inline) {
                registry.register(inline.sourceOrder(), inline.css());
            }
        }
    }

    private Iterable<JavaScriptSource> scriptSequence(
            DocumentResources resources, List<SubResourceLoad> cancellableLoads) {
        return () -> new ScriptSourceIterator(resources.scripts().iterator(), cancellableLoads);
    }

    private final class ScriptSourceIterator implements Iterator<JavaScriptSource> {

        private final Iterator<ScriptResource> resources;
        private final List<SubResourceLoad> cancellableLoads;
        private JavaScriptSource next;
        private boolean prepared;
        private int inlineIndex;

        private ScriptSourceIterator(Iterator<ScriptResource> resources,
                                     List<SubResourceLoad> cancellableLoads) {
            this.resources = resources;
            this.cancellableLoads = cancellableLoads;
        }

        @Override
        public boolean hasNext() {
            prepareNext();
            return next != null;
        }

        @Override
        public JavaScriptSource next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            JavaScriptSource result = next;
            next = null;
            prepared = false;
            return result;
        }

        private void prepareNext() {
            if (prepared) {
                return;
            }
            prepared = true;
            while (resources.hasNext()) {
                ScriptResource resource = resources.next();
                if (resource instanceof ScriptResource.Inline inline) {
                    if (!inline.code().isBlank()) {
                        next = new JavaScriptSource(inline.code(), inline.element(),
                                "inline-script-" + (++inlineIndex) + ".js");
                        return;
                    }
                    continue;
                }

                ScriptResource.External external = (ScriptResource.External) resource;
                SubResourceLoad load;
                try {
                    load = resourceLoader.loadAsync(
                            external.uri(), NetworkResourceType.SCRIPT);
                } catch (IllegalArgumentException invalidUri) {
                    continue;
                }
                cancellableLoads.add(load);
                try {
                    TextResource downloaded = load.await();
                    next = new JavaScriptSource(downloaded.content(), external.element(),
                            downloaded.uri().toString());
                    return;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (IOException | RuntimeException failure) {
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "Externe JavaScript-Ressource konnte nicht geladen werden: "
                                    + external.uri(), failure);
                }
            }
        }
    }

    private static void awaitRenderBlocking(List<CompletableFuture<Void>> tasks) {
        if (!tasks.isEmpty()) {
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
        }
    }

    private static void notifySafely(PageUpdateListener listener, PageUpdate update) {
        try {
            listener.onUpdate(update);
        } catch (RuntimeException failure) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Listener einer Seitenaktualisierung warf eine Exception", failure);
        }
    }

    private static final class LoadingState {
        private boolean scriptsFinished;
        private final List<URI> pendingStyleUpdates = new ArrayList<>();
    }
}

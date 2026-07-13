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
import com.browicy.engine.js.PageRuntime;
import com.browicy.engine.net.NetworkResourceType;
import com.browicy.engine.net.SubResourceLoad;
import com.browicy.engine.net.SubResourceLoader;
import com.browicy.engine.net.TextResource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

final class PageResourceCoordinator {

    private static final System.Logger LOGGER =
            System.getLogger(PageResourceCoordinator.class.getName());

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
        return load(document, listener, () -> { });
    }

    PageSession load(Document document, PageUpdateListener listener, Runnable onClose) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(onClose, "onClose");

        DocumentResources resources = scanner.scan(document);
        StyleSheetRegistry styleSheets = new StyleSheetRegistry();
        registerInlineStyleSheets(resources, styleSheets);
        DocumentUpdateCoordinator updates = new DocumentUpdateCoordinator(
                document, styleSheets, styleApplicator, listener);
        PageRuntime runtime = javaScriptEngine.createPageRuntime(document, ignored -> updates.flush());
        List<SubResourceLoad> cancellableLoads = new ArrayList<>();

        try {
            StyleLoadPlan styleLoads = startStyleLoads(
                    resources, styleSheets, updates, runtime, cancellableLoads);
            synchronized (document) {
                styleApplicator.apply(document, styleSheets);
            }

            for (JavaScriptSource source : scriptSequence(resources, cancellableLoads)) {
                runtime.execute(source);
            }

            PageLifecycleCoordinator lifecycle = new PageLifecycleCoordinator(document, runtime);
            lifecycle.markInteractive();
            awaitRenderBlocking(styleLoads.renderBlockingTasks());
            lifecycle.markComplete();
            runtime.awaitIdle();
            updates.enableNotifications();

            CompletableFuture<Void> allStyles = CompletableFuture.allOf(
                    styleLoads.allTasks().toArray(CompletableFuture[]::new));
            return new PageSession(
                    document, runtime, styleSheets, allStyles, cancellableLoads, updates, onClose);
        } catch (RuntimeException failure) {
            cancellableLoads.forEach(SubResourceLoad::cancel);
            updates.close();
            runtime.close();
            throw failure;
        }
    }

    private StyleLoadPlan startStyleLoads(DocumentResources resources,
                                          StyleSheetRegistry styleSheets,
                                          DocumentUpdateCoordinator updates,
                                          PageRuntime runtime,
                                          List<SubResourceLoad> cancellableLoads) {
        List<CompletableFuture<Void>> allTasks = new ArrayList<>();
        List<CompletableFuture<Void>> renderBlockingTasks = new ArrayList<>();
        for (StyleSheetResource styleSheet : resources.styleSheets()) {
            if (!(styleSheet instanceof StyleSheetResource.External external)) {
                continue;
            }
            SubResourceLoad load = resourceLoader.loadAsync(
                    external.uri(), NetworkResourceType.STYLESHEET);
            cancellableLoads.add(load);
            CompletableFuture<Void> task = load.future()
                    .handle((resource, failure) -> resource)
                    .thenCompose(resource -> applyStyleSheet(
                            resource, external, styleSheets, updates, runtime))
                    .exceptionally(failure -> null);
            allTasks.add(task);
            if (external.renderBlocking()) {
                renderBlockingTasks.add(task);
            }
        }
        return new StyleLoadPlan(allTasks, renderBlockingTasks);
    }

    private static CompletableFuture<Void> applyStyleSheet(
            TextResource resource,
            StyleSheetResource.External external,
            StyleSheetRegistry styleSheets,
            DocumentUpdateCoordinator updates,
            PageRuntime runtime) {
        if (resource == null || runtime.isClosed()) {
            return CompletableFuture.completedFuture(null);
        }
        return runtime.submitTask(() -> {
            styleSheets.register(external.sourceOrder(), resource.content());
            updates.stylesheetChanged(resource.uri());
        }).exceptionally(failure -> null);
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
                        String code = inline.module()
                                ? bundleModule(documentUri(inline.element()), inline.code())
                                : inline.code();
                        next = new JavaScriptSource(code, inline.element(),
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
                    String code = external.module()
                            ? new ModuleScriptBundler(resourceLoader, cancellableLoads)
                                    .bundle(downloaded.uri(), downloaded.content())
                            : downloaded.content();
                    next = new JavaScriptSource(code, external.element(),
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

        private String bundleModule(java.net.URI uri, String code) {
            try {
                return new ModuleScriptBundler(resourceLoader, cancellableLoads).bundle(uri, code);
            } catch (IOException | InterruptedException failure) {
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                return "throw new Error(" + jsString(failure.getMessage()) + ");";
            }
        }

        private java.net.URI documentUri(com.browicy.engine.dom.Element element) {
            return java.net.URI.create(element.getOwnerDocument().getUrl());
        }

        private String jsString(String value) {
            String safe = value == null ? "ES-Modul konnte nicht geladen werden" : value;
            return "'" + safe.replace("\\", "\\\\").replace("'", "\\'")
                    .replace("\r", "\\r").replace("\n", "\\n") + "'";
        }
    }

    private static void awaitRenderBlocking(List<CompletableFuture<Void>> tasks) {
        if (!tasks.isEmpty()) {
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
        }
    }

    private record StyleLoadPlan(List<CompletableFuture<Void>> allTasks,
                                 List<CompletableFuture<Void>> renderBlockingTasks) {
        private StyleLoadPlan {
            allTasks = List.copyOf(allTasks);
            renderBlockingTasks = List.copyOf(renderBlockingTasks);
        }
    }
}

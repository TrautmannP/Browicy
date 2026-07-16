package com.browicy.engine;

import com.browicy.engine.css.StyleApplicator;
import com.browicy.engine.css.StyleSheetRegistry;
import com.browicy.engine.css.CssFontFace;
import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Event;
import com.browicy.engine.html.DocumentResourceScanner;
import com.browicy.engine.html.DocumentResources;
import com.browicy.engine.html.ImageResource;
import com.browicy.engine.html.ScriptResource;
import com.browicy.engine.html.StyleSheetResource;
import com.browicy.engine.js.JavaScriptEngine;
import com.browicy.engine.js.JavaScriptSource;
import com.browicy.engine.js.JsCookieStore;
import com.browicy.engine.js.PageRuntime;
import com.browicy.engine.net.NetworkResourceType;
import com.browicy.engine.net.BinaryResource;
import com.browicy.engine.net.BinarySubResourceLoad;
import com.browicy.engine.net.DownloadBudget;
import com.browicy.engine.net.ResourceLoad;
import com.browicy.engine.net.SubResourceLoad;
import com.browicy.engine.net.SubResourceLoader;
import com.browicy.engine.net.TextResource;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.awt.Font;
import java.awt.FontFormatException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

final class PageResourceCoordinator {

    private static final System.Logger LOGGER =
            System.getLogger(PageResourceCoordinator.class.getName());

    private static final int MAX_IMAGE_LOADS_PER_PAGE = 256;
    private static final long MAX_TOTAL_IMAGE_BYTES = 128L * 1024 * 1024;
    private static final int MAX_FONT_LOADS_PER_PAGE = 32;
    private static final long MAX_TOTAL_FONT_BYTES = 32L * 1024 * 1024;
    private static final int MAX_DYNAMIC_SCRIPT_EXECUTIONS = 64;
    private static final long RENDER_BLOCKING_TIMEOUT_MILLIS = 5_000;

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
        return load(document, listener, () -> { }, new PageLoadProgress());
    }

    PageSession load(Document document, PageUpdateListener listener, Runnable onClose) {
        return load(document, listener, onClose, new PageLoadProgress());
    }

    PageSession load(Document document, PageUpdateListener listener, Runnable onClose,
                     PageLoadProgress progress) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(onClose, "onClose");
        Objects.requireNonNull(progress, "progress");

        DocumentResources resources = scanner.scan(document);
        StyleSheetRegistry styleSheets = new StyleSheetRegistry();
        registerStyleSheets(resources, styleSheets);
        DocumentUpdateCoordinator updates = new DocumentUpdateCoordinator(
                document, styleSheets, styleApplicator, listener);
        JsCookieStore cookies = new JsCookieStore();
        PageFetchBackend fetchBackend = new PageFetchBackend(
                resourceLoader, document.getUrl(), cookies);
        PageRuntime runtime = javaScriptEngine.createPageRuntime(
                document, ignored -> updates.flush(), fetchBackend, cookies,
                styleSheets, () -> updates.invalidate(InvalidationType.STYLE));
        List<ResourceLoad> cancellableLoads = new java.util.concurrent.CopyOnWriteArrayList<>();
        cancellableLoads.add(fetchBackend);
        ImageResourceRegistry images = new ImageResourceRegistry();
        FontResourceRegistry fonts = new FontResourceRegistry();
        URI pageUri = documentUri(document);
        DownloadBudget imageBudget = new DownloadBudget(
                MAX_TOTAL_IMAGE_BYTES, MAX_TOTAL_IMAGE_BYTES);
        DownloadBudget fontBudget = new DownloadBudget(
                MAX_TOTAL_FONT_BYTES, MAX_TOTAL_FONT_BYTES);

        try {
            progress.phase(PageLoadProgress.Phase.APPLYING_STYLES, "");
            StyleLoadPlan styleLoads = startStyleLoads(
                    resources, styleSheets, updates, runtime, cancellableLoads, progress);
            Map<ScriptResource.External, SubResourceLoad> prefetchedScripts =
                    prefetchScripts(resources, cancellableLoads);
            synchronized (document) {
                styleApplicator.apply(document, styleSheets);
            }

            progress.phase(PageLoadProgress.Phase.APPLYING_STYLES,
                    "Warte auf render-blockierende Stylesheets");
            awaitRenderBlocking(styleLoads.renderBlockingTasks());
            synchronized (document) {
                styleApplicator.apply(document, styleSheets);
            }
            progress.markFirstRender();

            progress.phase(PageLoadProgress.Phase.LOADING_RESOURCES, "");
            Map<URI, CompletableFuture<BinaryResource>> imageLoadsByUri =
                    new java.util.concurrent.ConcurrentHashMap<>();
            List<CompletableFuture<Void>> imageLoads = new ArrayList<>(startImageLoads(
                    resources, images, updates, cancellableLoads, pageUri, imageBudget, progress,
                    imageLoadsByUri));
            imageLoads.addAll(startCssImageLoads(
                    document, images, updates, cancellableLoads, pageUri, imageBudget, progress,
                    imageLoadsByUri));
            List<CompletableFuture<Void>> fontLoads = startFontLoads(
                    styleSheets, fonts, updates, cancellableLoads, pageUri, fontBudget, progress);

            resources.scripts().forEach(script -> progress.scriptPlanned());
            updates.enableNotifications();
            CompletableFuture<Void> scriptsDone = runScriptsInBackground(
                    document, resources, prefetchedScripts, runtime, cancellableLoads,
                    progress, updates, images, pageUri, imageBudget, imageLoadsByUri);

            List<CompletableFuture<Void>> allResources = new ArrayList<>(styleLoads.allTasks());
            allResources.addAll(imageLoads);
            allResources.addAll(fontLoads);
            allResources.add(scriptsDone);
            CompletableFuture<Void> resourcesLoaded = CompletableFuture.allOf(
                    allResources.toArray(CompletableFuture[]::new));
            resourcesLoaded.whenComplete((ignored, failure) ->
                    progress.phase(PageLoadProgress.Phase.COMPLETE, ""));
            return new PageSession(
                    document, runtime, styleSheets, images, fonts, cookies, resourcesLoaded,
                    cancellableLoads, updates, progress, onClose);
        } catch (RuntimeException failure) {
            progress.phase(PageLoadProgress.Phase.FAILED, String.valueOf(failure.getMessage()));
            cancellableLoads.forEach(ResourceLoad::cancel);
            updates.close();
            runtime.close();
            throw failure;
        }
    }

    private Map<ScriptResource.External, SubResourceLoad> prefetchScripts(
            DocumentResources resources, List<ResourceLoad> cancellableLoads) {
        Map<ScriptResource.External, SubResourceLoad> prefetched = new java.util.IdentityHashMap<>();
        for (ScriptResource resource : resources.scripts()) {
            if (!(resource instanceof ScriptResource.External external)) {
                continue;
            }
            try {
                SubResourceLoad load = resourceLoader.loadAsync(
                        external.uri(), NetworkResourceType.SCRIPT);
                cancellableLoads.add(load);
                prefetched.put(external, load);
            } catch (IllegalArgumentException invalidUri) {
            }
        }
        return prefetched;
    }

    private CompletableFuture<Void> runScriptsInBackground(
            Document document,
            DocumentResources resources,
            Map<ScriptResource.External, SubResourceLoad> prefetchedScripts,
            PageRuntime runtime,
            List<ResourceLoad> cancellableLoads,
            PageLoadProgress progress,
            DocumentUpdateCoordinator updates,
            ImageResourceRegistry images,
            URI pageUri,
            DownloadBudget imageBudget,
            Map<URI, CompletableFuture<BinaryResource>> imageLoadsByUri) {
        CompletableFuture<Void> scriptsDone = new CompletableFuture<>();
        Thread.ofVirtual().name("browicy-page-scripts").start(() -> {
            try {
                progress.phase(PageLoadProgress.Phase.RUNNING_SCRIPTS, "");
                for (JavaScriptSource source
                        : scriptSequence(resources, prefetchedScripts, cancellableLoads, progress)) {
                    if (runtime.isClosed()) {
                        return;
                    }
                    progress.activity(source.sourceName());
                    runtime.execute(source);
                    progress.scriptExecuted();
                }
                Set<Element> executedScripts = Collections.newSetFromMap(new IdentityHashMap<>());
                resources.scripts().stream()
                        .map(ScriptResource::element)
                        .forEach(executedScripts::add);
                runDynamicallyAddedScripts(
                        document, runtime, cancellableLoads, progress, executedScripts);
                PageLifecycleCoordinator lifecycle = new PageLifecycleCoordinator(document, runtime);
                lifecycle.markInteractive();
                lifecycle.markComplete();
                runtime.awaitIdle();
                loadScriptAddedImages(document, updates, images, cancellableLoads,
                        pageUri, imageBudget, progress, imageLoadsByUri, runtime);
            } catch (Throwable failure) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Skriptausführung der Seite endete vorzeitig", failure);
            } finally {
                scriptsDone.complete(null);
            }
        });
        return scriptsDone;
    }

    private void runDynamicallyAddedScripts(
            Document document,
            PageRuntime runtime,
            List<ResourceLoad> cancellableLoads,
            PageLoadProgress progress,
            Set<Element> executedScripts) {
        int remaining = MAX_DYNAMIC_SCRIPT_EXECUTIONS;
        while (!runtime.isClosed() && remaining > 0) {
            DocumentResources current;
            synchronized (document) {
                current = scanner.scan(document);
            }
            List<ScriptResource> added = current.scripts().stream()
                    .filter(script -> !executedScripts.contains(script.element()))
                    .limit(remaining)
                    .toList();
            if (added.isEmpty()) {
                return;
            }
            added.stream().map(ScriptResource::element).forEach(executedScripts::add);
            remaining -= added.size();
            added.forEach(ignored -> progress.scriptPlanned());

            DocumentResources dynamicResources = new DocumentResources(
                    List.of(), added, List.of());
            Map<ScriptResource.External, SubResourceLoad> prefetchedScripts =
                    prefetchScripts(dynamicResources, cancellableLoads);
            for (JavaScriptSource source : scriptSequence(
                    dynamicResources, prefetchedScripts, cancellableLoads, progress)) {
                if (runtime.isClosed()) {
                    return;
                }
                progress.activity(source.sourceName());
                runtime.execute(source);
                progress.scriptExecuted();
                if (source.element() != null) {
                    runtime.submitEvent(
                            source.element(), new Event("load", false, false)).join();
                }
            }
            runtime.awaitIdle();
        }
    }

    private void loadScriptAddedImages(
            Document document,
            DocumentUpdateCoordinator updates,
            ImageResourceRegistry images,
            List<ResourceLoad> cancellableLoads,
            URI pageUri,
            DownloadBudget imageBudget,
            PageLoadProgress progress,
            Map<URI, CompletableFuture<BinaryResource>> imageLoadsByUri,
            PageRuntime runtime) {
        if (runtime.isClosed() || pageUri == null) {
            return;
        }
        DocumentResources lateResources;
        synchronized (document) {
            lateResources = scanner.scan(document);
        }
        List<CompletableFuture<Void>> lateLoads = new ArrayList<>(startImageLoads(
                lateResources, images, updates, cancellableLoads, pageUri, imageBudget, progress,
                imageLoadsByUri));
        lateLoads.addAll(startCssImageLoads(
                document, images, updates, cancellableLoads, pageUri, imageBudget, progress,
                imageLoadsByUri));
        if (!lateLoads.isEmpty()) {
            CompletableFuture.allOf(lateLoads.toArray(CompletableFuture[]::new)).join();
        }
    }

    private StyleLoadPlan startStyleLoads(DocumentResources resources,
                                          StyleSheetRegistry styleSheets,
                                          DocumentUpdateCoordinator updates,
                                          PageRuntime runtime,
                                          List<ResourceLoad> cancellableLoads,
                                          PageLoadProgress progress) {
        List<CompletableFuture<Void>> allTasks = new ArrayList<>();
        List<CompletableFuture<Void>> renderBlockingTasks = new ArrayList<>();
        for (StyleSheetResource styleSheet : resources.styleSheets()) {
            if (!(styleSheet instanceof StyleSheetResource.External external)) {
                continue;
            }
            SubResourceLoad load = resourceLoader.loadAsync(
                    external.uri(), NetworkResourceType.STYLESHEET);
            cancellableLoads.add(load);
            progress.stylesheetStarted();
            CompletableFuture<Void> task = load.future()
                    .handle((resource, failure) -> resource)
                    .thenCompose(resource -> applyStyleSheet(
                            resource, external, styleSheets, updates, runtime))
                    .exceptionally(failure -> null)
                    .whenComplete((ignored, failure) -> progress.stylesheetFinished());
            allTasks.add(task);
            if (external.renderBlocking()) {
                renderBlockingTasks.add(task);
            }
        }
        return new StyleLoadPlan(allTasks, renderBlockingTasks);
    }

    private List<CompletableFuture<Void>> startImageLoads(
            DocumentResources resources,
            ImageResourceRegistry images,
            DocumentUpdateCoordinator updates,
            List<ResourceLoad> cancellableLoads,
            URI pageUri,
            DownloadBudget budget,
            PageLoadProgress progress,
            Map<URI, CompletableFuture<BinaryResource>> loadsByUri) {
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        if (pageUri == null) return List.of();
        for (ImageResource image : resources.images()) {
            CompletableFuture<BinaryResource> shared = loadsByUri.get(image.uri());
            if (shared == null) {
                if (loadsByUri.size() >= MAX_IMAGE_LOADS_PER_PAGE) {
                    continue;
                }
                BinarySubResourceLoad load;
                try {
                    load = resourceLoader.loadImageAsync(image.uri(), pageUri, budget);
                } catch (IllegalArgumentException invalidUri) {
                    continue;
                }
                cancellableLoads.add(load);
                progress.imageStarted();
                shared = load.future();
                shared.whenComplete((ignored, failure) -> progress.imageFinished());
                loadsByUri.put(image.uri(), shared);
            }
            CompletableFuture<Void> task = shared
                    .thenAccept(resource -> {
                        if (resource != null) {
                            images.register(image.element(), resource);
                            updates.invalidate(InvalidationType.RENDER_TREE);
                        }
                    })
                    .exceptionally(failure -> null);
            tasks.add(task);
        }
        return List.copyOf(tasks);
    }

    private List<CompletableFuture<Void>> startCssImageLoads(
            Document document,
            ImageResourceRegistry images,
            DocumentUpdateCoordinator updates,
            List<ResourceLoad> cancellableLoads,
            URI pageUri,
            DownloadBudget budget,
            PageLoadProgress progress,
            Map<URI, CompletableFuture<BinaryResource>> loadsByUri) {
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        if (pageUri == null) return List.of();
        for (com.browicy.engine.dom.Element element : document.getElementsByTagName("*")) {
            LinkedHashSet<URI> imageUris = new LinkedHashSet<>();
            for (Map<String, String> styles : List.of(
                    element.getComputedStyles(),
                    element.getPseudoComputedStyles("before"),
                    element.getPseudoComputedStyles("after"))) {
                URI uri = cssImageUri(styles.get("background-image"), pageUri);
                if (uri != null) imageUris.add(uri);
            }
            for (URI uri : imageUris) {
                if (loadsByUri.size() >= MAX_IMAGE_LOADS_PER_PAGE) break;
                CompletableFuture<BinaryResource> shared = loadsByUri.get(uri);
                if (shared == null) {
                    BinarySubResourceLoad load;
                    try {
                        load = resourceLoader.loadImageAsync(uri, pageUri, budget);
                    } catch (IllegalArgumentException invalidUri) {
                        continue;
                    }
                    cancellableLoads.add(load);
                    progress.imageStarted();
                    shared = load.future();
                    shared.whenComplete((ignored, failure) -> progress.imageFinished());
                    loadsByUri.put(uri, shared);
                }
                CompletableFuture<Void> task = shared.thenAccept(resource -> {
                    if (resource != null) {
                        images.register(uri, resource);
                        updates.invalidate(InvalidationType.PAINT);
                    }
                }).exceptionally(failure -> null);
                tasks.add(task);
            }
        }
        return List.copyOf(tasks);
    }

    private static URI cssImageUri(String value, URI base) {
        if (value == null) return null;
        String source = com.browicy.engine.render.CssUrl.parseSingle(value);
        if (source == null) return null;
        try {
            return base.resolve(source);
        } catch (IllegalArgumentException invalidUri) {
            return null;
        }
    }

    private List<CompletableFuture<Void>> startFontLoads(
            StyleSheetRegistry styleSheets,
            FontResourceRegistry fonts,
            DocumentUpdateCoordinator updates,
            List<ResourceLoad> cancellableLoads,
            URI pageUri,
            DownloadBudget budget,
            PageLoadProgress progress) {
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        if (pageUri == null) return List.of();
        Map<URI, CompletableFuture<BinaryResource>> loadsByUri = new HashMap<>();
        for (CssFontFace face : styleSheets.fontFaces()) {
            if (tasks.size() >= MAX_FONT_LOADS_PER_PAGE) break;
            CssFontFace.Source source = preferredFontSource(face.sources());
            if (source == null) continue;
            URI uri;
            try {
                uri = URI.create(source.url());
            } catch (IllegalArgumentException invalidUri) {
                continue;
            }
            CompletableFuture<BinaryResource> shared = loadsByUri.get(uri);
            if (shared == null) {
                BinarySubResourceLoad load;
                try {
                    load = resourceLoader.loadFontAsync(uri, pageUri, budget);
                } catch (IllegalArgumentException invalidUri) {
                    continue;
                }
                cancellableLoads.add(load);
                progress.fontStarted();
                shared = load.future();
                shared.whenComplete((ignored, failure) -> progress.fontFinished());
                loadsByUri.put(uri, shared);
            }
            CompletableFuture<Void> task = shared.thenAccept(resource -> {
                if (resource != null && corsAllows(pageUri, resource)) {
                    Font parsed = parseFont(resource);
                    if (parsed == null) return;
                    fonts.register(face.family(), resource, parsed);
                    updates.invalidate(InvalidationType.LAYOUT);
                }
            }).exceptionally(failure -> null);
            tasks.add(task);
        }
        return List.copyOf(tasks);
    }

    private static CssFontFace.Source preferredFontSource(List<CssFontFace.Source> sources) {
        for (CssFontFace.Source source : sources) {
            if (source.format().equals("truetype")
                    || source.url().toLowerCase(java.util.Locale.ROOT).endsWith(".ttf")) {
                return source;
            }
        }
        return sources.isEmpty() ? null : sources.getFirst();
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
            styleSheets.register(external.sourceOrder(), external.element(),
                    resource.uri().toString(),
                    absolutizeCssUrls(resource.content(), resource.uri()));
            updates.stylesheetChanged(resource.uri());
        }).exceptionally(failure -> null);
    }

    private static String absolutizeCssUrls(String css, URI base) {
        return com.browicy.engine.render.CssUrl.rewrite(css, source -> {
            try {
                URI uri = URI.create(source);
                if (!uri.isAbsolute() && !source.startsWith("#") && !source.startsWith("data:")) {
                    return base.resolve(uri).toString();
                }
            } catch (IllegalArgumentException ignored) {
            }
            return source;
        });
    }

    private static Font parseFont(BinaryResource resource) {
        try {
            return Font.createFont(Font.TRUETYPE_FONT,
                    new ByteArrayInputStream(resource.content()));
        } catch (FontFormatException | IOException | RuntimeException invalidFont) {
            return null;
        }
    }

    static boolean corsAllows(URI pageUri, BinaryResource resource) {
        if (sameOrigin(pageUri, resource.uri())) return true;
        String allowed = resource.corsAllowOrigin();
        if (allowed == null) return false;
        String pageOrigin = origin(pageUri);
        allowed = allowed.strip();
        return allowed.equals("*") || allowed.equalsIgnoreCase(pageOrigin);
    }

    private static boolean sameOrigin(URI first, URI second) {
        String firstOrigin = origin(first);
        return !firstOrigin.isEmpty() && firstOrigin.equalsIgnoreCase(origin(second));
    }

    private static String origin(URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) return "";
        int defaultPort = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        String port = uri.getPort() >= 0 && uri.getPort() != defaultPort
                ? ":" + uri.getPort() : "";
        return uri.getScheme() + "://" + uri.getHost() + port;
    }

    private static URI documentUri(Document document) {
        try {
            return URI.create(document.getUrl());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static void registerStyleSheets(DocumentResources resources,
                                            StyleSheetRegistry registry) {
        for (StyleSheetResource resource : resources.styleSheets()) {
            if (resource instanceof StyleSheetResource.Inline inline) {
                registry.register(inline.sourceOrder(), inline.element(), inline.css());
            } else if (resource instanceof StyleSheetResource.External external) {
                registry.register(external.sourceOrder(), external.element(),
                        external.uri().toString(), "");
            }
        }
    }

    private Iterable<JavaScriptSource> scriptSequence(
            DocumentResources resources,
            Map<ScriptResource.External, SubResourceLoad> prefetchedScripts,
            List<ResourceLoad> cancellableLoads,
            PageLoadProgress progress) {
        return () -> new ScriptSourceIterator(
                resources.scripts().iterator(), prefetchedScripts, cancellableLoads, progress);
    }

    private final class ScriptSourceIterator implements Iterator<JavaScriptSource> {

        private final Iterator<ScriptResource> resources;
        private final Map<ScriptResource.External, SubResourceLoad> prefetchedScripts;
        private final List<ResourceLoad> cancellableLoads;
        private final PageLoadProgress progress;
        private JavaScriptSource next;
        private boolean prepared;
        private int inlineIndex;

        private ScriptSourceIterator(Iterator<ScriptResource> resources,
                                     Map<ScriptResource.External, SubResourceLoad> prefetchedScripts,
                                     List<ResourceLoad> cancellableLoads,
                                     PageLoadProgress progress) {
            this.resources = resources;
            this.prefetchedScripts = prefetchedScripts;
            this.cancellableLoads = cancellableLoads;
            this.progress = progress;
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
                SubResourceLoad load = prefetchedScripts.get(external);
                if (load == null) {
                    try {
                        load = resourceLoader.loadAsync(
                                external.uri(), NetworkResourceType.SCRIPT);
                    } catch (IllegalArgumentException invalidUri) {
                        continue;
                    }
                    cancellableLoads.add(load);
                }
                progress.activity("Lade Skript " + external.uri());
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
        if (tasks.isEmpty()) {
            return;
        }
        try {
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new))
                    .get(RENDER_BLOCKING_TIMEOUT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException timeout) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Render-blockierende Stylesheets waren nach " + RENDER_BLOCKING_TIMEOUT_MILLIS
                            + " ms nicht geladen – Seite wird ohne sie dargestellt");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException failure) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Render-blockierendes Stylesheet schlug fehl", failure);
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

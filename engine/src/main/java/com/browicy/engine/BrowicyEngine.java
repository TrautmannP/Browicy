package com.browicy.engine;

import com.browicy.engine.dom.Document;
import com.browicy.engine.html.HtmlParser;
import com.browicy.engine.js.JavaScriptEngine;
import com.browicy.engine.js.JsCookieStore;
import com.browicy.engine.js.JsExecutionResult;
import com.browicy.engine.net.NetworkRequestObserver;
import com.browicy.engine.net.PageLoadObserver;
import com.browicy.engine.net.PageLoader;
import com.browicy.engine.net.SubResourceLoader;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class BrowicyEngine implements AutoCloseable {

    private static final String HELLO_WORLD_HTML = """
            <!DOCTYPE html>
            <html>
              <head>
                <title>Hallo Welt &ndash; Browicy</title>
              </head>
              <body>
                <h1>Hallo Welt!</h1>
                <p>Diese Seite wurde von der Browicy-Engine geparst und gerendert.</p>
                <p>HTML &rarr; Tokenizer &rarr; DOM &rarr; Anzeige &#128640;</p>
              </body>
            </html>
            """;

    private final HtmlParser parser;
    private final JavaScriptEngine jsEngine;
    private final PageLoader pageLoader;
    private final SubResourceLoader subResourceLoader;
    private final PageResourceCoordinator resourceCoordinator;
    private final JsCookieStore cookieStore = new JsCookieStore();
    private final Map<Document, PageSession> activeSessions = new ConcurrentHashMap<>();
    private volatile com.browicy.engine.js.LayoutMetricsAccess layoutMetricsAccess;

    private static final System.Logger LOGGER = System.getLogger(BrowicyEngine.class.getName());

    public BrowicyEngine() {
        this(new com.browicy.engine.net.HttpClient());
    }

    private BrowicyEngine(com.browicy.engine.net.HttpClient sharedClient) {
        this(new PageLoader(sharedClient), new SubResourceLoader(sharedClient),
                new HtmlParser(), new JavaScriptEngine());
    }

    public BrowicyEngine(PageLoader pageLoader) {
        this(pageLoader, new SubResourceLoader(), new HtmlParser(), new JavaScriptEngine());
    }

    BrowicyEngine(PageLoader pageLoader,
                  SubResourceLoader subResourceLoader,
                  HtmlParser parser,
                  JavaScriptEngine jsEngine) {
        this.pageLoader = Objects.requireNonNull(pageLoader, "pageLoader");
        this.subResourceLoader = Objects.requireNonNull(subResourceLoader, "subResourceLoader");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.jsEngine = Objects.requireNonNull(jsEngine, "jsEngine");
        this.resourceCoordinator = new PageResourceCoordinator(subResourceLoader, jsEngine);
        pageLoader.setCookieProvider(cookieStore::cookiesForRequest);
        pageLoader.setCookieSink((uri, header) ->
                cookieStore.storeFromHttp(uri, header, JsCookieStore.SOURCE_DOCUMENT));
        subResourceLoader.setCookieProvider(cookieStore::cookiesForRequest);
    }

    public void addNetworkObserver(PageLoadObserver observer) {
        pageLoader.addObserver(observer);
    }

    /**
     * Registriert den Layout-Zugriff für die JS-APIs ({@code getBoundingClientRect},
     * {@code offset*} / {@code client*}, Used Values in {@code getComputedStyle}).
     * Ohne Registrierung liefern diese APIs Nullen bzw. Rohwerte der Kaskade.
     */
    public void setLayoutMetricsAccess(com.browicy.engine.js.LayoutMetricsAccess access) {
        this.layoutMetricsAccess = access;
    }

    public void removeNetworkObserver(PageLoadObserver observer) {
        pageLoader.removeObserver(observer);
    }

    public void addRequestObserver(NetworkRequestObserver observer) {
        pageLoader.addNetworkObserver(observer);
        subResourceLoader.addObserver(observer);
    }

    public void removeRequestObserver(NetworkRequestObserver observer) {
        pageLoader.removeNetworkObserver(observer);
        subResourceLoader.removeObserver(observer);
    }

    public Document loadPage(String url) {
        PageSession session = loadPageSession(url, PageUpdateListener.NO_OP);
        try {
            session.awaitResources();
            return session.document();
        } finally {
            session.close();
        }
    }

    public PageSession loadPageSession(String url, PageUpdateListener listener) {
        return loadPageSession(url, listener, new PageLoadProgress());
    }

    public PageSession loadPageSession(String url, PageUpdateListener listener,
                                       PageLoadProgress progress) {
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(progress, "progress");
        URI uri;
        try {
            uri = PageLoader.normalize(url);
        } catch (IllegalArgumentException invalidUrl) {
            progress.phase(PageLoadProgress.Phase.FAILED, "Ungültige URL");
            return createSession(
                    errorPage(url, "Die Adresse ist keine gültige URL."), listener, progress);
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            Document document = parser.parse(HELLO_WORLD_HTML, url);
            return createSession(document, listener, progress);
        }
        try {
            progress.phase(PageLoadProgress.Phase.FETCHING_HTML, url);
            PageLoader.Page page = pageLoader.load(url);
            progress.phase(PageLoadProgress.Phase.PARSING, page.uri().toString());
            Document document = parser.parse(page.html(), page.uri().toString());
            return createSession(document, listener, progress);
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            progress.phase(PageLoadProgress.Phase.FAILED, message);
            return createSession(errorPage(url, message), listener, progress);
        }
    }

    public Document parseHtml(String html, String url) {
        return parser.parse(html, url);
    }

    public JsExecutionResult executeScripts(Document document) {
        return jsEngine.runScripts(document);
    }

    private PageSession createSession(Document document, PageUpdateListener listener,
                                      PageLoadProgress progress) {
        SessionNavigationHandler navigationHandler =
                new SessionNavigationHandler(this, listener, progress);
        PageSession session = resourceCoordinator.load(
                document, listener, () -> activeSessions.remove(document), progress,
                cookieStore, navigationHandler, layoutMetricsAccess);
        activeSessions.put(document, session);
        return session;
    }

    void navigateTo(SessionNavigationHandler navigationHandler, PageSession target, String url) {
        String resolved = resolveNavigationUrl(url, target.document().getUrl());
        if (resolved == null) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Navigation auf nicht unterstützte URL verworfen: " + url);
            return;
        }
        PageUpdateListener listener = navigationHandler.listener();
        PageLoadProgress progress = navigationHandler.progress();
        target.navigationStarted();
        Thread.ofVirtual().name("browicy-navigation").start(() -> {
            try {
                PageLoader.Page page = pageLoader.load(resolved);
                Document document = parser.parse(page.html(), page.uri().toString());
                PageSession replacement = resourceCoordinator.load(
                        document, listener, () -> activeSessions.remove(document), progress,
                        cookieStore, navigationHandler, layoutMetricsAccess);
                activeSessions.put(document, target);
                target.replaceState(replacement.state());
                LOGGER.log(System.Logger.Level.INFO,
                        "Navigation abgeschlossen: {0} -> {1}", url, resolved);
            } catch (Throwable failure) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Navigation nach " + resolved + " schlug fehl", failure);
            } finally {
                target.navigationFinished();
            }
        });
    }

    private static String resolveNavigationUrl(String url, String documentUrl) {
        if (url == null) {
            return null;
        }
        try {
            URI base = documentUrl == null || documentUrl.isBlank()
                    ? null : new URI(documentUrl);
            URI resolved = base == null ? new URI(url) : base.resolve(url);
            String scheme = resolved.getScheme();
            if (scheme == null
                    || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return null;
            }
            return resolved.toString();
        } catch (java.net.URISyntaxException | IllegalArgumentException invalidUrl) {
            return null;
        }
    }

    private Document errorPage(String url, String message) {
        String html = """
                <!DOCTYPE html>
                <html>
                  <head><title>Seite konnte nicht geladen werden</title></head>
                  <body>
                    <h1>Seite konnte nicht geladen werden</h1>
                    <p>%s</p>
                    <p>%s</p>
                  </body>
                </html>
                """.formatted(escapeHtml(url), escapeHtml(message));
        return parser.parse(html, "about:error");
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public void close() {
        for (PageSession session : List.copyOf(activeSessions.values())) {
            session.close();
        }
        activeSessions.clear();
        subResourceLoader.close();
        pageLoader.close();
    }
}

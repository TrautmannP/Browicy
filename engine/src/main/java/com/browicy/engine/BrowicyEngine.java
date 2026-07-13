package com.browicy.engine;

import com.browicy.engine.dom.Document;
import com.browicy.engine.html.HtmlParser;
import com.browicy.engine.js.JavaScriptEngine;
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
    private final Map<Document, PageSession> activeSessions = new ConcurrentHashMap<>();

    public BrowicyEngine() {
        this(new PageLoader(), new SubResourceLoader(), new HtmlParser(), new JavaScriptEngine());
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
    }

    public void addNetworkObserver(PageLoadObserver observer) {
        pageLoader.addObserver(observer);
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
        Objects.requireNonNull(listener, "listener");
        URI uri;
        try {
            uri = PageLoader.normalize(url);
        } catch (IllegalArgumentException invalidUrl) {
            return createSession(
                    errorPage(url, "Die Adresse ist keine gültige URL."), listener);
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            Document document = parser.parse(HELLO_WORLD_HTML, url);
            return createSession(document, listener);
        }
        try {
            PageLoader.Page page = pageLoader.load(url);
            Document document = parser.parse(page.html(), page.uri().toString());
            return createSession(document, listener);
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return createSession(errorPage(url, message), listener);
        }
    }

    public Document parseHtml(String html, String url) {
        return parser.parse(html, url);
    }

    public JsExecutionResult executeScripts(Document document) {
        return jsEngine.runScripts(document);
    }

    private PageSession createSession(Document document, PageUpdateListener listener) {
        PageSession session = resourceCoordinator.load(
                document, listener, () -> activeSessions.remove(document));
        activeSessions.put(document, session);
        return session;
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

package com.browicy.engine;

import lombok.RequiredArgsConstructor;

import com.browicy.engine.dom.Document;
import com.browicy.engine.html.HtmlParser;
import com.browicy.engine.js.JavaScriptEngine;
import com.browicy.engine.js.JsExecutionResult;
import com.browicy.engine.net.PageLoadObserver;
import com.browicy.engine.net.PageLoader;

import java.net.URI;

@RequiredArgsConstructor
public final class BrowicyEngine {

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

    private final HtmlParser parser = new HtmlParser();
    private final JavaScriptEngine jsEngine = new JavaScriptEngine();
    private final PageLoader pageLoader;

    public BrowicyEngine() {
        this(new PageLoader());
    }

    public void addNetworkObserver(PageLoadObserver observer) {
        pageLoader.addObserver(observer);
    }

    public void removeNetworkObserver(PageLoadObserver observer) {
        pageLoader.removeObserver(observer);
    }

    public Document loadPage(String url) {
        URI uri;
        try {
            uri = PageLoader.normalize(url);
        } catch (IllegalArgumentException invalidUrl) {
            return errorPage(url, "Die Adresse ist keine gültige URL.");
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return parser.parse(HELLO_WORLD_HTML, url);
        }
        try {
            PageLoader.Page page = pageLoader.load(url);
            Document document = parser.parse(page.html(), page.uri().toString());
            jsEngine.runScripts(document);
            return document;
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return errorPage(url, message);
        }
    }

    public Document parseHtml(String html, String url) {
        return parser.parse(html, url);
    }

    public JsExecutionResult executeScripts(Document document) {
        return jsEngine.runScripts(document);
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
        return parser.parse(html, url);
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

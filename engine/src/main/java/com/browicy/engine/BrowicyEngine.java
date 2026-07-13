package com.browicy.engine;

import com.browicy.engine.dom.Document;
import com.browicy.engine.html.HtmlParser;
import com.browicy.engine.net.PageLoader;

import java.net.URI;

/**
 * Fassade der Browicy-Browser-Engine. Die UI (der Browser) spricht
 * ausschließlich mit dieser Klasse und erhält fertige DOM-Dokumente.
 *
 * <p>Aktueller Stand: Netzwerk-Laden über einen eigenen HTTP/1.1-Client
 * ({@code com.browicy.engine.net}) und HTML-Parsing zu einem DOM-Baum.
 * CSS, JavaScript und Layout folgen in späteren Ausbaustufen. Fehler beim
 * Laden führen nicht zu Exceptions, sondern zu einer gerenderten
 * Fehlerseite — die UI erhält immer ein Dokument.</p>
 */
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
    private final PageLoader pageLoader;

    public BrowicyEngine() {
        this(new PageLoader());
    }

    /** Für Tests: Engine mit eigenem {@link PageLoader} (z.B. gegen einen lokalen Server). */
    public BrowicyEngine(PageLoader pageLoader) {
        this.pageLoader = pageLoader;
    }

    /**
     * Lädt die Seite hinter der angegebenen URL über das Netzwerk und liefert
     * das geparste Dokument. Nicht-HTTP-URLs (z.B. {@code about:}-Seiten)
     * liefern die eingebaute Hallo-Welt-Seite, Ladefehler eine Fehlerseite.
     */
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
            return parser.parse(page.html(), page.uri().toString());
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return errorPage(url, message);
        }
    }

    /**
     * Parst den übergebenen HTML-Quelltext direkt zu einem Dokument.
     */
    public Document parseHtml(String html, String url) {
        return parser.parse(html, url);
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

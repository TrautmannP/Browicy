package com.browicy.engine;

import com.browicy.engine.dom.Document;
import com.browicy.engine.html.HtmlParser;

/**
 * Fassade der Browicy-Browser-Engine. Die UI (der Browser) spricht
 * ausschließlich mit dieser Klasse und erhält fertige DOM-Dokumente.
 *
 * <p>Aktueller Stand: HTML-Parsing zu einem DOM-Baum. Netzwerk-Laden,
 * CSS und Layout folgen in späteren Ausbaustufen — {@link #loadPage(String)}
 * liefert deshalb für jede URL eine eingebaute Hallo-Welt-Seite.</p>
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

    /**
     * Lädt die Seite hinter der angegebenen URL und liefert das geparste Dokument.
     * Solange es keinen Netzwerk-Stack gibt, wird für jede URL die eingebaute
     * Hallo-Welt-Seite geliefert.
     */
    public Document loadPage(String url) {
        return parser.parse(HELLO_WORLD_HTML, url);
    }

    /**
     * Parst den übergebenen HTML-Quelltext direkt zu einem Dokument.
     */
    public Document parseHtml(String html, String url) {
        return parser.parse(html, url);
    }
}

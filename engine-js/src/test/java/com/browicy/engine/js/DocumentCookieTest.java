package com.browicy.engine.js;

import com.browicy.engine.dom.Document;
import com.browicy.engine.html.HtmlParser;
import java.net.URI;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class DocumentCookieTest {

    private static final String DOCUMENT_URL = "https://app.example/pfad/seite.html";

    private final HtmlParser parser = new HtmlParser();
    private final JavaScriptEngine engine = new JavaScriptEngine();

    @Test
    public void cookiesRoundTripThroughDocumentCookie() {
        Document document = parse();

        try (PageRuntime runtime = engine.createPageRuntime(document)) {
            JsExecutionResult result = runtime.execute(new JavaScriptSource("""
                    document.cookie = 'sitzung=abc123';
                    document.cookie = 'theme=dunkel; Path=/';
                    document.getElementById('out').textContent = document.cookie;
                    """, null, "cookie-roundtrip.js"));
            assertFalse(String.valueOf(result.errors()), result.hasErrors());
            runtime.awaitIdle();
        }

        assertEquals("sitzung=abc123; theme=dunkel", output(document));
    }

    @Test
    public void assignmentsAreAdditiveLikeInBrowsers() {
        Document document = parse();

        try (PageRuntime runtime = engine.createPageRuntime(document)) {
            runtime.execute(new JavaScriptSource("""
                    document.cookie = 'a=1';
                    document.cookie = 'b=2';
                    document.cookie = 'a=3';
                    document.getElementById('out').textContent = document.cookie;
                    """, null, "cookie-additiv.js"));
            runtime.awaitIdle();
        }

        assertEquals("a=3; b=2", output(document));
    }

    @Test
    public void httpOnlyCookiesFromTheStoreStayHidden() {
        Document document = parse();
        JsCookieStore store = new JsCookieStore();
        store.storeFromHttp(URI.create(DOCUMENT_URL), "geheim=intern; HttpOnly; Path=/");
        store.storeFromHttp(URI.create(DOCUMENT_URL), "offen=sichtbar; Path=/");

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, null, store)) {
            runtime.execute(new JavaScriptSource("""
                    document.cookie = 'geheim=uebernahme';
                    document.getElementById('out').textContent = document.cookie;
                    """, null, "cookie-httponly.js"));
            runtime.awaitIdle();
        }

        assertEquals("geheim=uebernahme; offen=sichtbar", output(document));
    }

    @Test
    public void deletionViaMaxAgeWorksFromScripts() {
        Document document = parse();

        try (PageRuntime runtime = engine.createPageRuntime(document)) {
            runtime.execute(new JavaScriptSource("""
                    document.cookie = 'kurz=wert';
                    document.cookie = 'bleibt=ja';
                    document.cookie = 'kurz=; Max-Age=0';
                    document.getElementById('out').textContent = document.cookie;
                    """, null, "cookie-loeschen.js"));
            runtime.awaitIdle();
        }

        assertEquals("bleibt=ja", output(document));
    }

    @Test
    public void documentsWithoutHttpUrlHaveNoCookies() {
        Document document = parser.parse(
                "<html><body><output id=\"out\"></output></body></html>", "about:blank");

        try (PageRuntime runtime = engine.createPageRuntime(document)) {
            JsExecutionResult result = runtime.execute(new JavaScriptSource("""
                    document.cookie = 'nix=wert';
                    document.getElementById('out').textContent =
                        'leer:' + (document.cookie === '');
                    """, null, "cookie-ohne-url.js"));
            assertFalse(String.valueOf(result.errors()), result.hasErrors());
            runtime.awaitIdle();
        }

        assertEquals("leer:true", output(document));
    }

    private Document parse() {
        return parser.parse("""
                <html><body><output id="out"></output></body></html>
                """, DOCUMENT_URL);
    }

    private static String output(Document document) {
        return document.getElementById("out").getTextContent();
    }
}

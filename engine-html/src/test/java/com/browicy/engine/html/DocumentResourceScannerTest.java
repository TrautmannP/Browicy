package com.browicy.engine.html;

import com.browicy.engine.dom.Document;
import java.net.URI;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DocumentResourceScannerTest {

    private final HtmlParser parser = new HtmlParser();
    private final DocumentResourceScanner scanner = new DocumentResourceScanner();

    @Test
    public void resolvesResourcesAgainstFirstBaseHref() {
        Document document = parser.parse("""
                <html><head>
                  <base href="/assets/v2/">
                  <base href="/ignored/">
                  <link rel="preload stylesheet" href="theme.css#fragment">
                  <script src="../js/app.js"></script>
                </head><body></body></html>
                """, "https://example.test/pages/index.html");

        DocumentResources resources = scanner.scan(document);

        assertEquals(URI.create("https://example.test/assets/v2/"), document.getBaseUri());
        StyleSheetResource.External style =
                (StyleSheetResource.External) resources.styleSheets().getFirst();
        ScriptResource.External script =
                (ScriptResource.External) resources.scripts().getFirst();
        assertEquals(URI.create("https://example.test/assets/v2/theme.css"), style.uri());
        assertEquals(URI.create("https://example.test/assets/js/app.js"), script.uri());
        assertTrue(style.renderBlocking());
    }

    @Test
    public void preservesStyleAndScriptTreeOrder() {
        Document document = parser.parse("""
                <html><head>
                  <style>p { color: red; }</style>
                  <link rel="stylesheet" href="/second.css">
                  <script>globalThis.one = 1;</script>
                  <script src="/two.js" async></script>
                </head><body>
                  <link rel="stylesheet" href="/body.css">
                </body></html>
                """, "https://example.test/index.html");

        DocumentResources resources = scanner.scan(document);

        assertEquals(3, resources.styleSheets().size());
        assertEquals(0, resources.styleSheets().get(0).sourceOrder());
        assertEquals(1, resources.styleSheets().get(1).sourceOrder());
        assertEquals(2, resources.styleSheets().get(2).sourceOrder());
        assertFalse(((StyleSheetResource.External) resources.styleSheets().get(2)).renderBlocking());
        assertTrue(resources.scripts().get(0) instanceof ScriptResource.Inline);
        assertTrue(resources.scripts().get(1) instanceof ScriptResource.External);
        assertTrue(resources.scripts().get(1).async());
    }

    @Test
    public void includesModulesButIgnoresUnsupportedOrInvalidResourceUrls() {
        Document document = parser.parse("""
                <html><head>
                  <link rel="stylesheet" href="file:///tmp/theme.css">
                  <link rel="stylesheet" href="http://exa mple.test/broken.css">
                  <script src="ftp://example.test/app.js"></script>
                  <script type="module" src="/module.js"></script>
                </head><body></body></html>
                """, "https://example.test/index.html");

        DocumentResources resources = scanner.scan(document);

        assertTrue(resources.styleSheets().isEmpty());
        assertEquals(1, resources.scripts().size());
        assertTrue(resources.scripts().getFirst().module());
    }

    @Test
    public void resolvesImageSourcesAgainstTheDocumentBaseUri() {
        Document document = parser.parse("""
                <html><head><base href="/assets/"></head><body>
                  <img id="logo" src="images/logo.png#ignored">
                  <img src="data:image/png;base64,AAAA">
                </body></html>
                """, "https://example.test/page/index.html");

        DocumentResources resources = scanner.scan(document);

        assertEquals(1, resources.images().size());
        assertEquals(document.getElementById("logo"), resources.images().getFirst().element());
        assertEquals(URI.create("https://example.test/assets/images/logo.png"),
                resources.images().getFirst().uri());
    }
}

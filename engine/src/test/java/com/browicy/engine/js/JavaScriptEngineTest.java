package com.browicy.engine.js;

import com.browicy.engine.dom.Document;
import com.browicy.engine.html.HtmlParser;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JavaScriptEngineTest {

    private final HtmlParser parser = new HtmlParser();
    private final JavaScriptEngine engine = new JavaScriptEngine();

    private Document parse(String html) {
        return parser.parse(html, "about:test");
    }

    // --- DOM-Zugriff -----------------------------------------------------

    @Test
    public void scriptModifiesTextContentOfElement() {
        Document document = parse("""
                <html><head><title>Test</title></head>
                <body>
                  <p id="greeting">Alter Text</p>
                  <script>document.getElementById('greeting').textContent = 'Hallo aus JavaScript';</script>
                </body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals("Hallo aus JavaScript",
                document.getElementById("greeting").getTextContent());
    }

    @Test
    public void scriptReadsAndWritesDocumentTitle() {
        Document document = parse("""
                <html><head><title>Alter Titel</title></head>
                <body><script>
                  console.log('Titel war: ' + document.title);
                  document.title = 'Neuer Titel';
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals("Neuer Titel", document.getTitle());
        assertEquals(List.of("log: Titel war: Alter Titel"), result.consoleMessages());
    }

    @Test
    public void scriptCreatesAndAppendsElements() {
        Document document = parse("""
                <html><head><title>Test</title></head>
                <body><script>
                  var p = document.createElement('p');
                  p.id = 'dynamisch';
                  p.setAttribute('class', 'hinweis');
                  p.textContent = 'Von JavaScript erzeugt';
                  document.body.appendChild(p);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        var created = document.getElementById("dynamisch");
        assertNotNull(created);
        assertEquals("p", created.getTagName());
        assertEquals("hinweis", created.getAttribute("class"));
        assertEquals("Von JavaScript erzeugt", created.getTextContent());
    }

    @Test
    public void scriptReadsAttributesTagNamesAndChildren() {
        Document document = parse("""
                <html><head><title>Test</title></head>
                <body>
                  <div id="wrapper" data-info="42"><p>eins</p><p>zwei</p></div>
                  <script>
                    var wrapper = document.getElementById('wrapper');
                    console.log(wrapper.tagName, wrapper.getAttribute('data-info'),
                                wrapper.children.length, wrapper.hasAttribute('fehlt'));
                    console.log(document.getElementsByTagName('p').length);
                  </script>
                </body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: DIV 42 2 false", "log: 2"), result.consoleMessages());
    }

    @Test
    public void elementWrappersKeepIdentityLikeInBrowsers() {
        Document document = parse("""
                <html><head><title>Test</title></head>
                <body><script>
                  console.log(document.body === document.body);
                  console.log(document.getElementById('x') === null);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: true", "log: true"), result.consoleMessages());
    }

    // --- Skript-Semantik --------------------------------------------------

    @Test
    public void multipleScriptsShareGlobalState() {
        Document document = parse("""
                <html><head><title>Test</title></head>
                <body>
                  <script>var zaehler = 41;</script>
                  <script>zaehler++; console.log('zaehler=' + zaehler);</script>
                </body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: zaehler=42"), result.consoleMessages());
    }

    @Test
    public void scriptErrorsAreCollectedAndDoNotStopFollowingScripts() {
        Document document = parse("""
                <html><head><title>Test</title></head>
                <body>
                  <script>nichtDefiniert();</script>
                  <script>console.log('läuft trotzdem');</script>
                </body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertTrue(result.hasErrors());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0), result.errors().get(0).contains("nichtDefiniert"));
        assertEquals(List.of("log: läuft trotzdem"), result.consoleMessages());
    }

    @Test
    public void externalScriptsAreSkippedInPrototype() {
        Document document = parse("""
                <html><head><title>Test</title>
                <script src="https://example.com/app.js"></script></head>
                <body><script>console.log('inline');</script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: inline"), result.consoleMessages());
    }

    @Test
    public void pagesWithoutScriptsReturnEmptyResult() {
        Document document = parse("<html><body><p>Nur Text</p></body></html>");

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(result.hasErrors());
        assertTrue(result.consoleMessages().isEmpty());
    }

    @Test
    public void modernEcmaScriptFeaturesWork() {
        Document document = parse("""
                <html><head><title>Test</title></head>
                <body><script>
                  const quadrate = [1, 2, 3].map(n => n ** 2);
                  const { length } = quadrate;
                  console.log(`Quadrate: ${quadrate.join(',')} (n=${length})`);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: Quadrate: 1,4,9 (n=3)"), result.consoleMessages());
    }

    @Test
    public void bodyOnloadAndTimersCanUpdateSiblingTextNodes() {
        Document document = parse("""
                <html><body onload="update()">
                  <p><span id="score">JS</span><span class="hidden">/</span><span>?</span></p>
                  <script>
                    var score = 0;
                    function update() {
                      var span = document.getElementById('score');
                      span.nextSibling.removeAttribute('class');
                      span.nextSibling.nextSibling.firstChild.data = 100;
                      score += 1;
                      span.firstChild.data = score;
                      if (score < 2) setTimeout(update, 0);
                    }
                  </script>
                </body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals("2/100", document.getElementsByTagName("p").get(0).getTextContent());
    }

    @Test
    public void documentWriteInsertsParsedHtmlAfterCurrentScript() {
        Document document = parse("""
                <html><body><p>vorher</p>
                  <script>document.write('<map><area href=""><iframe>fallback<\\/iframe><\\/map>');</script>
                  <p id="after">nachher</p>
                </body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(1, document.getElementsByTagName("map").size());
        assertEquals(1, document.getElementsByTagName("area").size());
        assertEquals("fallback", document.getElementsByTagName("iframe").get(0).getTextContent());
        assertEquals("nachher", document.getElementById("after").getTextContent());
    }

    // --- Sandbox ----------------------------------------------------------

    @Test
    public void sandboxBlocksAccessToJavaClasses() {
        Document document = parse("""
                <html><head><title>Test</title></head>
                <body><script>Java.type('java.lang.System').exit(1);</script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        // Wichtig: Der Zugriff schlägt fehl, statt Host-Code auszuführen
        assertTrue(result.hasErrors());
    }

    @Test
    public void statementLimitStopsInfiniteLoops() {
        JavaScriptEngine limitedEngine = new JavaScriptEngine(10_000);
        Document document = parse("""
                <html><head><title>Test</title></head>
                <body><script>while (true) {}</script></body></html>
                """);

        JsExecutionResult result = limitedEngine.runScripts(document);

        assertTrue(result.hasErrors());
    }
}

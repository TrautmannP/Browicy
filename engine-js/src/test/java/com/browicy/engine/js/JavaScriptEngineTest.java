package com.browicy.engine.js;

import com.browicy.engine.css.StyleApplicator;
import com.browicy.engine.css.StyleSheetRegistry;
import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.html.HtmlParser;
import org.junit.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JavaScriptEngineTest {

    @Test
    public void exposesElementConstructorAndPrototypeHierarchy() {
        Document document = parse("""
                <!doctype html><html><body><main id="target"></main><script>
                  const target = document.getElementById('target');
                  console.log(typeof Element, target instanceof Element,
                              target instanceof HTMLElement, target instanceof Node,
                              HTMLElement.prototype instanceof Element);
                  const text = document.createTextNode('text');
                  const comment = document.createComment('comment');
                  console.log(typeof CharacterData, text instanceof CharacterData,
                              comment instanceof CharacterData,
                              document.childNodes[0] instanceof DocumentType);
                  const detached = new Document();
                  console.log(document instanceof Document, detached instanceof Document,
                              detached.documentElement.tagName, detached.body.tagName);
                  const fragment = new DocumentFragment();
                  fragment.appendChild(document.createElement('span'));
                  console.log(fragment instanceof DocumentFragment, fragment.childNodes.length);
                  try { new Element(); } catch (error) { console.log(error.name); }
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: function true true true true",
                        "log: function true true true", "log: true true HTML BODY",
                        "log: true 1", "log: TypeError"),
                result.consoleMessages());
    }

    @Test
    public void exposesHighResolutionTimeApiShape() {
        Document document = parse("""
                <html><body><script>
                  console.log(typeof performance.now, performance.timeOrigin > 0,
                              performance.timing.navigationStart === performance.timeOrigin,
                              performance.now() >= 0,
                              performance.getEntriesByType('navigation').length,
                              document.fonts.check('10px sans-serif'),
                              typeof navigator.sendBeacon);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: function true true true 1 true function"),
                result.consoleMessages());
    }

    @Test
    public void documentAcceptsScriptExpandoProperties() {
        Document document = parse("""
                <html><body><script>
                  document.customState = { ready: true };
                  console.log(document.customState.ready, '__missing' in document);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: true false"), result.consoleMessages());
    }

    @Test
    public void exposesDocumentHeadAndElementMatchingHelpers() {
        Document document = parse("""
                <html><head></head><body><main class="shell"><span id="child"></span></main>
                <script>
                  const child = document.getElementById('child');
                  console.log(document.head.tagName, child.matches('span#child'),
                              child.closest('.shell').tagName,
                              child.closest('.missing') === null);
                  document.head.append(document.createElement('meta'));
                  child.scrollIntoView();
                  console.log(document.head.children.length);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: HEAD true MAIN true", "log: 1"),
                result.consoleMessages());
    }

    @Test
    public void iframeExposesStableInitialAboutBlankBrowsingContext() {
        Document document = parse("""
                <html><body><iframe id="frame"></iframe><script>
                  const frame = document.getElementById('frame');
                  const content = frame.contentDocument;
                  console.log(content.URL, content.documentElement.tagName,
                              content.body.tagName, content.readyState);
                  console.log(frame.contentDocument === content,
                              frame.contentWindow.document === content,
                              content.defaultView === frame.contentWindow,
                              frame.contentWindow.window === frame.contentWindow);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of(
                "log: about:blank HTML BODY complete",
                "log: true true true true"), result.consoleMessages());
    }

    @Test
    public void topParentAndFramesExposeTheTopLevelBrowsingContextAndNamedIframes() {
        Document document = parse("""
                <html><body><iframe id="by-id" name="named"></iframe><script>
                  console.log(top === window, parent === window, frames.length,
                              frames[0] === document.getElementById('by-id').contentWindow,
                              frames.named === frames[0], frames['by-id'] === frames[0]);
                  const dynamic = document.createElement('iframe');
                  dynamic.name = '__tcfapiLocator';
                  document.body.appendChild(dynamic);
                  console.log(frames.length, frames.__tcfapiLocator.document.URL);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: true true 1 true true true",
                "log: 2 about:blank"), result.consoleMessages());
    }

    @Test
    public void iframeSrcdocCreatesAReplaceableStyledDocument() {
        Document document = parse("""
                <html><body><iframe id="frame"></iframe><script>
                  const frame = document.getElementById('frame');
                  frame.srcdoc = '<style>p { color: green }</style><p id="message">hello</p>';
                  const first = frame.contentDocument;
                  console.log(first.getElementById('message').textContent,
                              frame.contentWindow.getComputedStyle(
                                first.getElementById('message')).color);
                  frame.srcdoc = '<p id="replacement">new</p>';
                  console.log(frame.contentDocument !== first,
                              frame.contentDocument.getElementById('replacement').textContent);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: hello green", "log: true new"),
                result.consoleMessages());
    }

    @Test
    public void exposesComputedStylesAsAReadOnlyCssStyleDeclaration() {
        Document document = parse("""
                <html><head><style>#target { color: red; font-size: 18px }</style></head>
                <body><div id="target"></div><script>
                  var target = document.getElementById('target');
                  var style = window.getComputedStyle(target);
                  console.log(style.color, style.fontSize, style.getPropertyValue('font-size'));
                  console.log(style.length, style.item(0), style[1], style.parentRule === null);
                  style.color = 'blue';
                  console.log(style.color, target.style.color);
                </script></body></html>
                """);
        new StyleApplicator().apply(document);
        Element target = document.getElementById("target");

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of(
                "log: red 18px 18px",
                "log: 2 color font-size true",
                "log: red "), result.consoleMessages());
        assertEquals("red", target.getComputedStyles().get("color"));
    }

    @Test
    public void exposesLiveCssStyleSheetAndCssRuleBindings() {
        Document document = parse("""
                <html><head><style id="theme">p { color: red; }</style></head><body>
                <script>
                  const sheet = document.getElementById('theme').sheet;
                  const rules = sheet.cssRules;
                  console.log(document.styleSheets.length, document.styleSheets[0] === sheet,
                              sheet.ownerNode.id, sheet.href === null);
                  console.log(rules.length, rules[0].selectorText, rules[0].type,
                              rules[0].parentStyleSheet === sheet);
                  console.log(sheet.insertRule('p { color: blue; }', 1), rules.length,
                              rules.item(1).cssText);
                  sheet.deleteRule(0);
                  console.log(rules.length, rules[0].selectorText);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of(
                "log: 1 true theme true",
                "log: 1 p 1 true",
                "log: 1 2 p { color: blue; }",
                "log: 1 p"), result.consoleMessages());
    }

    @Test
    public void createsAStyleSheetBindingForDynamicallyCreatedStyleElements() {
        Document document = parse("""
                <html><head></head><body><script>
                  const style = document.createElement('style');
                  style.textContent = 'p { color: green; }';
                  document.getElementsByTagName('head')[0].appendChild(style);
                  console.log(style.sheet !== null, style.sheet.cssRules.length,
                              style.sheet.cssRules[0].selectorText);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: true 1 p"), result.consoleMessages());
    }

    private final HtmlParser parser = new HtmlParser();
    private final JavaScriptEngine engine = new JavaScriptEngine();

    private Document parse(String html) {
        return parser.parse(html, "about:test");
    }

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

    @Test
    public void supportsLiveHtmlCollectionsFormsAndTableDom() {
        Document document = parse("""
                <html><body>
                  <form id="login"><input name="user"><select name="role"><option>A</option></select></form>
                  <table id="grid"><tbody><tr><td>A</td></tr></tbody></table>
                  <script>
                    var forms = document.forms;
                    var form = forms.login;
                    var extra = document.createElement('input'); extra.name = 'token'; form.appendChild(extra);
                    var table = document.getElementById('grid');
                    var row = table.insertRow(-1); row.insertCell(-1).textContent = 'B';
                    console.log(forms.length, form.elements.length, form.elements.token === extra);
                    console.log(table.rows.length, row.rowIndex, row.cells.length, row.cells.item(0).cellIndex);
                    console.log(form.elements.role.options.length, form.elements.role.selectedIndex);
                  </script>
                </body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: 1 3 true", "log: 2 1 1 0", "log: 1 0"), result.consoleMessages());
    }

    @Test
    public void documentLinksExposesAnchorsAndAreasWithHref() {
        Document document = parse("""
                <html><body>
                  <a id="one" href="/ziel">Eins</a>
                  <a id="two">Ohne href</a>
                  <area id="map" href="/karte">
                  <script>
                    var links = document.links;
                    var viaLength = [];
                    for (var i = 0; i < links.length; i++) { viaLength.push(links[i].id); }
                    console.log(links.length, viaLength.join(','), links.item(1).id, links.map.id);
                  </script>
                </body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: 2 one,map map map"), result.consoleMessages());
    }

    @Test
    public void clonesInputStateAndEnforcesRadioGroups() {
        Document document = parse("""
                <html><body><form id="form"><script>
                  var form = document.getElementById('form');
                  var first = document.createElement('input');
                  first.type = 'radio'; first.name = 'group'; first.setAttribute('value', 'default');
                  first.value = 'live'; first.checked = true; form.appendChild(first);
                  var second = first.cloneNode(true); form.appendChild(second); second.checked = true;
                  var third = document.createElement('input');
                  third.type = 'radio'; third.name = 'other'; form.appendChild(third); third.checked = true;
                  console.log(first.value, first.getAttribute('value'), first.checked, second.checked, third.checked);
                  second.setAttribute('checked', 'checked');
                  console.log(second.defaultChecked, second.checked);
                  var deep = form.cloneNode(true);
                  console.log(deep.parentNode === null, deep.elements.length, deep.elements[0].value);
                </script></form></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: live default false true true", "log: true true", "log: true 3 live"),
                result.consoleMessages());
    }

    @Test
    public void classListIsLiveMutableAndKeepsWrapperIdentity() {
        Document document = parse("""
                <html><body><div id="box" class="card active"></div><script>
                  var box = document.getElementById('box');
                  var classes = box.classList;
                  console.log(classes === box.classList, classes.length, classes.item(0));
                  classes.add('wide', 'active');
                  classes.remove('card');
                  console.log(classes.toggle('open'), classes.toggle('active', false));
                  box.setAttribute('class', 'external synced');
                  console.log(classes.contains('external'), classes.value, box.className);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of(
                "log: true 2 card",
                "log: true false",
                "log: true external synced external synced"), result.consoleMessages());
        assertEquals("external synced", document.getElementById("box").getAttribute("class"));
    }

    @Test
    public void querySelectorsWorkOnDocumentElementsAndDocumentFragments() {
        Document document = parse("""
                <html><body>
                  <section id="main"><p id="first" class="note"></p><div><span id="second" class="note"></span></div></section>
                  <script>
                    var matches = document.querySelectorAll('#main .note');
                    var main = document.querySelector('#main');
                    var later = document.createElement('p'); later.className = 'note'; main.appendChild(later);
                    var fragment = document.createDocumentFragment();
                    var wrapper = document.createElement('div');
                    var target = document.createElement('b'); target.className = 'target'; wrapper.appendChild(target); fragment.appendChild(wrapper);
                    console.log(document.querySelector('section > p.note').id,
                                matches.length, matches.item(1).id, matches[0] === document.getElementById('first'));
                    console.log(main.querySelectorAll('.note').length, main.querySelector('#main') === null,
                                fragment.querySelector('div > .target') === target);
                  </script>
                </body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: first 2 second true", "log: 3 true true"),
                result.consoleMessages());
    }

    @Test
    public void invalidQuerySelectorRaisesDomSyntaxError() {
        Document document = parse("""
                <html><body><script>
                  try { document.querySelector('div > > p'); }
                  catch (error) { console.log(error.name, error.code); }
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: SyntaxError 12"), result.consoleMessages());
    }

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

    @Test
    public void domCoreCreatesAndSplicesGenericNodeTypes() {
        Document document = parse("""
                <!doctype html><html><body><div id="target"></div><script>
                  var target = document.getElementById('target');
                  var fragment = document.createDocumentFragment();
                  var text = document.createTextNode('eins');
                  var comment = document.createComment('messbar');
                  fragment.appendChild(text);
                  fragment.appendChild(comment);
                  target.appendChild(fragment);
                  console.log(document.nodeType, document.firstChild.nodeType,
                              target.nodeType, target.childNodes.length,
                              text.nodeType, comment.nodeType, fragment.childNodes.length,
                              target.contains(comment), target.hasChildNodes());
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: 9 10 1 2 3 8 0 true true"), result.consoleMessages());
    }

    @Test
    public void nodeIteratorTraversesLiveTreeInBothDirections() {
        Document document = parse("""
                <html><body><main id="root"><a></a><b><c></c></b></main><script>
                  var root = document.getElementById('root');
                  var iterator = document.createNodeIterator(root, NodeFilter.SHOW_ELEMENT, null);
                  var forward = [], node;
                  while ((node = iterator.nextNode())) {
                    forward.push(node.nodeName);
                    if (node.nodeName == 'A') root.insertBefore(document.createElement('x'), node.nextSibling);
                  }
                  var backward = [];
                  while ((node = iterator.previousNode())) backward.push(node.nodeName);
                  console.log(forward.join(','));
                  console.log(backward.join(','));
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: MAIN,A,X,B,C", "log: C,B,X,A,MAIN"), result.consoleMessages());
    }

    @Test
    public void nodeIteratorTreatsRejectLikeSkipAndForwardsFilterExceptions() {
        Document document = parse("""
                <html><body><main id="root"><a><b></b></a><c></c></main><script>
                  var root = document.getElementById('root');
                  var iterator = document.createNodeIterator(root, NodeFilter.SHOW_ELEMENT,
                    function (node) { return node.nodeName == 'A' ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT; });
                  var names = [], node;
                  while ((node = iterator.nextNode())) names.push(node.nodeName);
                  console.log(names.join(','));
                  var expected = {};
                  var throwing = document.createNodeIterator(root, NodeFilter.SHOW_ALL, function () { throw expected; });
                  try { throwing.nextNode(); } catch (error) { console.log(error === expected); }
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: MAIN,B,C", "log: true"), result.consoleMessages());
    }

    @Test
    public void treeWalkerDistinguishesSkippedAndRejectedSubtrees() {
        Document document = parse("""
                <html><body><main id="root"><skip><a></a></skip><reject><b></b></reject><c></c></main><script>
                  var root = document.getElementById('root');
                  var walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT, function (node) {
                    if (node.nodeName == 'SKIP') return NodeFilter.FILTER_SKIP;
                    if (node.nodeName == 'REJECT') return NodeFilter.FILTER_REJECT;
                    return NodeFilter.FILTER_ACCEPT;
                  });
                  var names = [walker.currentNode.nodeName], node;
                  while ((node = walker.nextNode())) names.push(node.nodeName);
                  console.log(names.join(','));
                  console.log(walker.previousNode().nodeName, walker.parentNode().nodeName);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: MAIN,A,C", "log: A MAIN"), result.consoleMessages());
    }

    @Test
    public void rangesExtractInsertAndTrackLiveMutationsFromJavaScript() {
        Document document = parse("""
                <html><body><div id="root"><span>12345</span><b>ABCDE</b><i>tail</i></div><script>
                  var root = document.getElementById('root');
                  var text = root.firstChild.firstChild;
                  var bold = root.childNodes[1];
                  var range = document.createRange();
                  console.log(range.collapsed, range.startContainer === document, range.startOffset);
                  range.setStart(text, 2);
                  range.setEnd(text, 3);
                  range.insertNode(bold.firstChild);
                  console.log(root.textContent, range.toString());
                  range.selectNode(root.lastChild);
                  var extracted = range.extractContents();
                  console.log(extracted.firstChild.nodeName, root.childNodes.length, range.collapsed);
                  range.selectNodeContents(root);
                  root.insertBefore(document.createElement('u'), root.firstChild);
                  console.log(range.startOffset, range.endOffset);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of(
                "log: true true 0",
                "log: 12ABCDE345tail ABCDE3",
                "log: I 2 true",
                "log: 0 3"), result.consoleMessages());
    }

    @Test
    public void nodeComparisonMethodsAndConstantsAreExposedToJavaScript() {
        Document document = parse("""
                <html><body><div id="root"><a></a><b></b></div><script>
                  var root = document.getElementById('root');
                  var a = root.firstChild;
                  var b = root.lastChild;
                  var clone = document.createElement('a');
                  console.log(a.compareDocumentPosition(b) === Node.DOCUMENT_POSITION_FOLLOWING);
                  console.log(root.compareDocumentPosition(a) ===
                    (root.DOCUMENT_POSITION_FOLLOWING | root.DOCUMENT_POSITION_CONTAINED_BY));
                  console.log(a.isSameNode(a), a.isSameNode(clone), a.isEqualNode(clone));
                  clone.setAttribute('x', '1');
                  console.log(a.isEqualNode(clone));
                  console.log(new Range().collapsed, Range.START_TO_START);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: true", "log: true", "log: true false true", "log: false",
                "log: true 0"), result.consoleMessages());
    }

    @Test
    public void rangeFailuresAreExposedAsDomExceptions() {
        Document document = parse("""
                <html><body><script>
                  var range = document.createRange();
                  try {
                    range.setEndBefore(document);
                  } catch (error) {
                    console.log(error.name, error.code, error.INVALID_NODE_TYPE_ERR,
                      error instanceof DOMException);
                  }
                  range.selectNode(document.firstChild);
                  try {
                    range.surroundContents(document.createElement('a'));
                  } catch (error) {
                    console.log(error.name, error.code, error.HIERARCHY_REQUEST_ERR,
                      error instanceof DOMException);
                  }
                </script></body></html>
                """);
        document.insertBefore(document.createComment("range target"), document.getDocumentElement());

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of(
                "log: InvalidNodeTypeError 24 24 true",
                "log: HierarchyRequestError 3 3 true"), result.consoleMessages());
    }

    @Test
    public void domImplementationCreatesNamespacedDocuments() {
        Document document = parse("""
                <html><body><script>
                  var type = document.implementation.createDocumentType('root', 'public', 'system');
                  var xml = document.implementation.createDocument('urn:test', 'p:root', type);
                  var child = xml.createElementNS('urn:child', 'c:item');
                  xml.documentElement.appendChild(child);
                  console.log(xml.firstChild === type, type.ownerDocument === xml,
                              type.name, type.publicId, type.systemId);
                  console.log(xml.documentElement.tagName, xml.documentElement.namespaceURI,
                              xml.documentElement.prefix, xml.documentElement.localName);
                  console.log(child.nodeName, child.namespaceURI, xml.childNodes.length);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of(
                "log: true true root public system",
                "log: p:root urn:test p root",
                "log: c:item urn:child 2"), result.consoleMessages());
    }

    @Test
    public void domImplementationCreatesHtmlDocuments() {
        Document document = parse("""
                <html><body><script>
                  var created = document.implementation.createHTMLDocument('Created title');
                  console.log(created.URL, created.title, created.documentElement.tagName,
                              created.body.tagName, created.childNodes.length);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: about:blank Created title HTML BODY 2"),
                result.consoleMessages());
    }

    @Test
    public void nodeListsCanBeConsumedAsArrayLikeValues() {
        Document document = parse("""
                <html><body><p>one</p><p>two</p><script>
                  var target = [];
                  Array.prototype.push.apply(target, document.querySelectorAll('p'));
                  console.log(target.length, target[0].textContent, target[1].textContent);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: 2 one two"), result.consoleMessages());
    }

    @Test
    public void htmlCollectionsCanBeConsumedAsArrayLikeValues() {
        Document document = parse("""
                <html><body><p>one</p><p>two</p><script>
                  var target = [];
                  var collection = document.getElementsByTagName('p');
                  Array.prototype.push.apply(target, collection);
                  console.log(target.length, target[0].textContent, target[1].textContent,
                              collection[collection.length] === undefined);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: 2 one two true"), result.consoleMessages());
    }

    @Test
    public void invalidQualifiedNamesProduceDomExceptions() {
        Document document = parse("""
                <html><body><script>
                  for (const operation of [
                    () => document.createElement('bad name'),
                    () => document.createElementNS(null, 'p:name'),
                    () => document.createElementNS('urn:test', 'xml:name')
                  ]) {
                    try { operation(); } catch (error) { console.log(error.name, error.code); }
                  }
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of(
                "log: InvalidCharacterError 5",
                "log: NamespaceError 14",
                "log: NamespaceError 14"), result.consoleMessages());
    }

    @Test
    public void uiEventsDispatchOnElementsAndTextNodesAndCanBeRemoved() {
        Document document = parse("""
                <html><body><div id="result"><span id="score"></span>text</div><script>
                  var count = 0;
                  var valid = true;
                  var listener = function (event) {
                    valid = valid && event.detail === 6 && event.type === 'test';
                    count++;
                  };
                  var result = document.getElementById('result');
                  var score = document.getElementById('score');
                  result.addEventListener('test', listener, false);
                  var event = document.createEvent('UIEvents');
                  event.initUIEvent('test', true, false, null, 6);
                  console.log(score.dispatchEvent(event));
                  console.log(score.nextSibling.dispatchEvent(event));
                  result.removeEventListener('test', listener, false);
                  console.log(score.dispatchEvent(event), count, valid);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: true", "log: true", "log: true 2 true"),
                result.consoleMessages());
    }

    @Test
    public void clickEventCapturesStopsAndBubblesThroughDocument() {
        Document document = parse("""
                <html><body><script>
                  var input = document.createElement('input');
                  var div = document.createElement('div');
                  div.appendChild(input);
                  document.body.appendChild(div);
                  var captureCount = 0;
                  var bodyBubbleCount = 0;
                  var valid = true;
                  function capture(event) {
                    valid = valid && event.type === 'click' && event.target === input &&
                            event.currentTarget === div && event.eventPhase === 1 &&
                            event.bubbles && event.cancelable && this === div;
                    captureCount++;
                    event.stopPropagation();
                  }
                  div.addEventListener('click', function (event) { capture.call(this, event); }, true);
                  div.addEventListener('click', function (event) { capture.call(this, event); }, true);
                  document.body.addEventListener('click', function () { bodyBubbleCount++; }, false);
                  input.type = 'reset';
                  input.click();
                  console.log(captureCount, bodyBubbleCount, valid, input.type);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: 2 0 true reset"), result.consoleMessages());
    }

    @Test
    public void eventConstructorPreventDefaultAndObjectListenersWork() {
        Document document = parse("""
                <html><body><button id="button"></button><script>
                  var button = document.getElementById('button');
                  var calls = 0;
                  var listener = { handleEvent(event) { calls++; event.preventDefault(); } };
                  button.addEventListener('save', listener, {capture: false});
                  var event = new Event('save', {bubbles: true, cancelable: true});
                  console.log(button.dispatchEvent(event), event.defaultPrevented, calls);
                  button.removeEventListener('save', listener, {capture: false});
                  console.log(button.dispatchEvent(new Event('save')), calls);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: false true 1", "log: true 1"), result.consoleMessages());
    }

    @Test
    public void customEventCarriesDetailThroughConstructionAndDispatch() {
        Document document = parse("""
                <html><body><button id="button"></button><script>
                  const button = document.getElementById('button');
                  let received = null;
                  button.addEventListener('article', event => received = event.detail.slug);
                  const event = new CustomEvent('article', {
                    bubbles: true, cancelable: true, detail: {slug: 'heise'}
                  });
                  console.log(event instanceof CustomEvent, event.type, event.bubbles,
                              event.cancelable, event.detail.slug, button.dispatchEvent(event), received);
                  const legacy = document.createEvent('CustomEvent');
                  legacy.initCustomEvent('legacy', false, false, 42);
                  console.log(legacy.detail);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: true article true true heise true heise", "log: 42"),
                result.consoleMessages());
    }

    @Test
    public void loadAttributeAndRegisteredLoadListenersUseEventSystem() {
        Document document = parse("""
                <html><body onload="console.log('inline', event.type, this === document.body)"><script>
                  document.body.addEventListener('load', function (event) {
                    console.log('listener', event.target === document.body, event.eventPhase);
                  });
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: listener true 2", "log: inline load true"),
                result.consoleMessages());
    }

    @Test
    public void sandboxBlocksAccessToJavaClasses() {
        Document document = parse("""
                <html><head><title>Test</title></head>
                <body><script>Java.type('java.lang.System').exit(1);</script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

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

    @Test
    public void preparedExternalAndInlineSourcesShareGlobalContext() {
        Document document = parse("""
                <html><body><p id="message">vorher</p></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document, List.of(
                new JavaScriptSource("globalThis.shared = 'extern';", null,
                        "https://example.test/app.js"),
                new JavaScriptSource("document.getElementById('message').textContent = shared + '-inline';",
                        null, "inline.js")));

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals("extern-inline", document.getElementById("message").getTextContent());
    }

    @Test
    public void exposesCssStyleDeclarationAndCssSupportsWithoutOverReporting() {
        Document document = parse("""
                <html><body><div id="target"></div><script>
                  const target = document.getElementById('target');
                  target.style.color = 'red';
                  target.style.display = 'grid';
                  target.append('hello');
                  target.innerHTML = '<strong>world</strong>';
                  target.setAttribute('data-css', CSS.supports('color', 'red') + ':'
                    + CSS.supports('display', 'grid') + ':' + ('color' in target.style));
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals("color:red;display:grid;",
                document.getElementById("target").getAttribute("style"));
        assertEquals("true:true:true",
                document.getElementById("target").getAttribute("data-css"));
        assertEquals("world", document.getElementById("target").getTextContent());
    }

    @Test
    public void exposesIndeterminateStateAndSelectorSupportsForNewPseudoClasses() {
        Document document = parse("""
                <html><body>
                  <input id="check" type="checkbox">
                  <input id="radioA" type="radio" name="group">
                  <input id="radioB" type="radio" name="group">
                  <p id="linkTarget"><a id="href" href="https://example.test/">x</a></p>
                  <script>
                    const check = document.getElementById('check');
                    const href = document.getElementById('href');
                    check.indeterminate = true;
                    check.setAttribute('data-state',
                      check.indeterminate + ':' + document.querySelectorAll(':indeterminate').length
                      + ':' + document.querySelectorAll('input[name=group]:indeterminate').length);
                    const style = document.createElement('style');
                    style.textContent = '@namespace "http://www.w3.org/1999/xhtml";';
                    document.documentElement.appendChild(style);
                    const namespaceSheet = style.sheet;
                    style.textContent = '@namespace svg "http://www.w3.org/2000/svg";';
                    href.setAttribute('data-selectors', [
                      CSS.supports('selector(:nth-last-child(even))'),
                      CSS.supports('selector(a[href^="https"])'),
                      CSS.supports('selector(p::first-letter)'),
                      CSS.supports('selector(:not(.class):not(#id):not([attr]):not(:link))'),
                      CSS.supports('selector(:target)'),
                      CSS.supports('selector(:indeterminate)'),
                      CSS.supports('selector(*|html)'),
                      CSS.supports('selector([*|attr])'),
                      CSS.supports('color', 'hsl(0,0%,0%)'),
                      CSS.supports('color', 'currentColor'),
                      CSS.supports('opacity', '-5'),
                      namespaceSheet.cssRules.length
                    ].join(':'));
                  </script>
                </body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals("true:3:2", document.getElementById("check").getAttribute("data-state"));
        assertEquals("true:true:true:true:true:true:true:true:true:true:true:1",
                document.getElementById("href").getAttribute("data-selectors"));
    }

    @Test
    public void invokesWindowOnloadWithLocationAndUrlSearchParams() {
        Document document = parser.parse("""
                <html><body data-filter=""><script>
                  onload = () => {
                    const filter = new URLSearchParams(location.search).get('filter');
                    document.body.setAttribute('data-filter', filter);
                  };
                </script></body></html>
                """, "https://example.test/?filter=css2007");

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals("css2007", document.getBody().getAttribute("data-filter"));
    }

    @Test
    public void exposesSvgAndMathMlElementConstructorsWithoutClaimingHtmlElements() {
        Document document = parse("""
                <html><body><div id="target"></div><script>
                  const target = document.getElementById('target');
                  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
                  const math = document.createElementNS(
                      'http://www.w3.org/1998/Math/MathML', 'math');
                  console.log(typeof SVGElement, typeof MathMLElement,
                              target instanceof SVGElement, svg instanceof SVGElement,
                              svg instanceof HTMLElement, target instanceof MathMLElement,
                              math instanceof MathMLElement,
                              svg.namespaceURI, math.namespaceURI);
                  try { new SVGElement(); } catch (error) { console.log(error.name); }
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: function function false true false false true "
                                + "http://www.w3.org/2000/svg http://www.w3.org/1998/Math/MathML",
                        "log: TypeError"),
                result.consoleMessages());
    }

    @Test
    public void innerHtmlSerializesStructureAttributesAndEscapesText() {
        Document document = parse("""
                <html><body><div id="target"><p class="a">A &amp; B</p><!--note--><br><img src="x.png"></div><script>
                  const target = document.getElementById('target');
                  console.log(target.innerHTML);
                  target.innerHTML = '<p id="fresh">Neu &lt;Inhalt&gt;</p><span data-x="a&quot;b">S</span>';
                  console.log(target.querySelector('#fresh').textContent,
                              target.querySelector('span').getAttribute('data-x'),
                              target.childNodes.length);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: <p class=\"a\">A &amp; B</p><!--note--><br><img src=\"x.png\">",
                        "log: Neu <Inhalt> a\"b 2"),
                result.consoleMessages());
    }

    @Test
    public void mountsAVue3ApplicationAndRendersReactiveContent() throws Exception {
        String vue = new String(getClass().getResourceAsStream(
                "/vue3/vue.global.js").readAllBytes(), StandardCharsets.UTF_8);
        Document document = parser.parse("""
                <html><body><div id="app">
                  <h1>{{ title }}</h1>
                  <button id="plus" @click="increment">+1</button>
                  <p id="count">Wert: <strong>{{ count }}</strong></p>
                </div></body></html>
                """.replace("</body>",
                "<script>" + vue + "</script>"
                        + "<script>"
                        + "  const { createApp, ref, computed } = Vue;\n"
                        + "  const app = createApp({\n"
                        + "    setup() {\n"
                        + "      const title = ref('Vue 3 läuft');\n"
                        + "      const count = ref(5);\n"
                        + "      const doubled = computed(() => count.value * 2);\n"
                        + "      function increment() { count.value++; }\n"
                        + "      return { title, count, doubled, increment };\n"
                        + "    }\n"
                        + "  });\n"
                        + "  app.mount('#app');\n"
                        + "  document.getElementById('plus').dispatchEvent(\n"
                        + "      new Event('click', { bubbles: true }));\n"
                        + "  Vue.nextTick().then(() => {\n"
                        + "    console.log(document.getElementById('count').textContent);\n"
                        + "  });\n"
                        + "</script></body>"), "http://example.test/app.html");

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals("Vue 3 läuft", document.getBody().findFirst("h1").getTextContent());
        assertEquals("6", document.getBody().querySelector("#count strong").getTextContent());
        assertTrue(result.consoleMessages().stream()
                .anyMatch(message -> message.startsWith("log: Wert: 6")));
    }

    @Test
    public void lazyScriptSequenceExecutesBeforeRequestingNextSource() {
        Document document = parse("""
                <html><body><p id="message">before</p></body></html>
                """);
        List<JavaScriptSource> sources = List.of(
                new JavaScriptSource(
                        "document.getElementById('message').textContent = 'first';",
                        null, "first.js"),
                new JavaScriptSource(
                        "document.getElementById('message').textContent += '-second';",
                        null, "second.js"));
        Iterable<JavaScriptSource> lazySources = () -> new Iterator<>() {
            private final Iterator<JavaScriptSource> delegate = sources.iterator();
            private int requested;

            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public JavaScriptSource next() {
                if (requested++ == 1) {
                    assertEquals("first",
                            document.getElementById("message").getTextContent());
                }
                return delegate.next();
            }
        };

        JsExecutionResult result = engine.runScriptSequence(document, lazySources);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals("first-second",
                document.getElementById("message").getTextContent());
    }

    @Test
    public void locationNavigationMethodsInvokeHookWithResolvedUrls() {
        Document document = parser.parse("""
                <html><body><script>
                  location.replace('/ziel');
                  location.assign('/andere?q=1');
                  location.reload();
                  location.href = '/neu';
                  const popup = window.open('/blank', '_blank');
                  const same = window.open('/fenster', '_self');
                  console.log(typeof location.replace, typeof location.assign,
                              typeof location.reload, popup === null, same === window,
                              location.protocol, location.hostname, location.port,
                              location.pathname, location.search, location.origin);
                </script></body></html>
                """, "http://example.test:8080/pfad/index.html?x=1");

        List<String> navigated = new ArrayList<>();
        List<Boolean> replaceModes = new ArrayList<>();
        PageNavigationHandler handler = (url, replace) -> {
            navigated.add(url);
            replaceModes.add(replace);
        };
        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, null, null,
                new StyleSheetRegistry(), () -> {
                }, handler)) {
            JsExecutionResult result = runtime.execute(new JavaScriptSource(
                    document.getElementsByTagName("script").get(0).getTextContent(),
                    null, "nav-test.js"));

            assertFalse(String.valueOf(result.errors()), result.hasErrors());
            assertEquals(List.of(
                            "log: function function function true true "
                                    + "http: example.test 8080 /pfad/index.html ?x=1 "
                                    + "http://example.test:8080"),
                    result.consoleMessages());
        }

        assertEquals(List.of(
                "http://example.test:8080/ziel",
                "http://example.test:8080/andere?q=1",
                "http://example.test:8080/pfad/index.html?x=1",
                "http://example.test:8080/neu",
                "http://example.test:8080/fenster"), navigated);
        assertEquals(List.of(true, false, true, true, false), replaceModes);
    }

    @Test
    public void locationHashChangesOnlyTheFragmentWithoutNavigating() {
        Document document = parser.parse("""
                <html><body><script>
                  location.hash = 'abschnitt';
                  console.log(location.hash, location.href);
                </script></body></html>
                """, "http://example.test/pfad/index.html");

        List<String> navigated = new ArrayList<>();
        PageNavigationHandler handler = (url, replace) -> navigated.add(url);
        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, null, null,
                new StyleSheetRegistry(), () -> {
                }, handler)) {
            JsExecutionResult result = runtime.execute(new JavaScriptSource(
                    document.getElementsByTagName("script").get(0).getTextContent(),
                    null, "hash-test.js"));

            assertFalse(String.valueOf(result.errors()), result.hasErrors());
            assertEquals(List.of("log: #abschnitt http://example.test/pfad/index.html#abschnitt"),
                    result.consoleMessages());
        }

        assertTrue("Hash-Änderung darf nicht navigieren: " + navigated, navigated.isEmpty());
    }

    @Test
    public void httpOnlyCookiesAreSentOnRequestsButHiddenFromScripts() {
        Document document = parser.parse("""
                <html><body></body></html>
                """, "http://example.test/index.html");
        JsCookieStore store = new JsCookieStore();
        URI pageUri = URI.create("http://example.test/index.html");
        store.storeFromHttp(pageUri, "sichtbar=ja; Path=/");
        store.storeFromHttp(pageUri, "geheim=intern; Path=/; HttpOnly");

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, null, store)) {
            JsExecutionResult result = runtime.execute(new JavaScriptSource(
                    "console.log(document.cookie);", null, "cookie-test.js"));

            assertFalse(String.valueOf(result.errors()), result.hasErrors());
            assertEquals(List.of("log: sichtbar=ja"), result.consoleMessages());
        }

        assertEquals("sichtbar=ja; geheim=intern", store.cookiesForRequest(pageUri));
    }

    @Test
    public void anchorElementsExposeResolvedUrlParts() {
        Document document = parser.parse("""
                <html><body>
                  <area id="map" href="/karte">
                  <span id="plain">x</span>
                  <script>
                  // Muster von axios isURLSameOrigin: <a> als URL-Referenz nutzen.
                  const n = document.createElement('a');
                  n.setAttribute('href', window.location.href);
                  const first = n.href;
                  n.setAttribute('href', '/pfad?x=1#anker');
                  console.log(first === window.location.href,
                              n.protocol, n.host, n.hostname, n.port, n.pathname,
                              n.search, n.hash, n.origin, n.href);
                  const area = document.getElementById('map');
                  console.log(area.hostname, area.pathname);
                  const plain = document.getElementById('plain');
                  console.log(plain.href, plain.pathname, typeof plain.pathname);
                  </script></body></html>
                """, "http://example.test:8080/base/index.html");
        JsExecutionResult result = engine.runScripts(document);
        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of(
                "log: true http: example.test:8080 example.test 8080 "
                        + "/pfad ?x=1 #anker http://example.test:8080 "
                        + "http://example.test:8080/pfad?x=1#anker",
                "log: example.test /karte",
                "log:   string"), result.consoleMessages());
    }

    @Test
    public void cryptoSubtleDigestHashesBytes() {
        Document document = parser.parse("""
                <html><body><script>
                  // Muster von h3/ofetch: crypto.subtle.digest für Request-Hashing.
                  const subtle = globalThis.crypto && globalThis.crypto.subtle;
                  console.log(typeof subtle, typeof subtle.digest);
                  const data = new TextEncoder().encode('browicy');
                  subtle.digest('SHA-256', data).then(result => {
                    const hex = Array.from(new Uint8Array(result))
                        .map(byte => byte.toString(16).padStart(2, '0')).join('');
                    console.log(hex);
                  }).catch(error => console.log('FEHLER', String(error)));
                  subtle.digest({ name: 'SHA-1' }, data).then(result => {
                    console.log(new Uint8Array(result).length);
                  });
                  subtle.digest('NOPE', data).then(
                      () => console.log('unexpected'),
                      error => console.log('abgelehnt', String(error).slice(0, 20)));
                </script></body></html>
                """, "http://example.test/index.html");
        JsExecutionResult result = engine.runScripts(document);
        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of(
                "log: object function",
                "log: 22ea6b7ff506049ada6da7f78903fd0874486c1f0c1f36fceb16a60648cedfe2",
                "log: 20",
                "log: abgelehnt NotSupportedError: N"), result.consoleMessages());
    }

    @Test
    public void urlConstructorResolvesRelativeUrlsAndExposesParts() {
        Document document = parser.parse("""
                <html><body><script>
                  const a = new URL('/pfad?x=1#anker', 'http://example.test:8080/base/index.html');
                  const b = new URL('https://andere.test/absolut');
                  const c = new URL('../hoch', 'http://example.test:8080/base/tief/index.html');
                  console.log(a.href, a.origin, a.pathname, a.search, a.hash, a.toString());
                  console.log(b.origin, b.pathname, b.toString());
                  console.log(c.href);
                  try { new URL('kein schema'); } catch (error) { console.log(error.name); }
                </script></body></html>
                """, "http://example.test/index.html");

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of(
                "log: http://example.test:8080/pfad?x=1#anker http://example.test:8080 "
                        + "/pfad ?x=1 #anker http://example.test:8080/pfad?x=1#anker",
                "log: https://andere.test /absolut https://andere.test/absolut",
                "log: http://example.test:8080/base/hoch",
                "log: TypeError"), result.consoleMessages());
    }

    @Test
    public void documentReferrerIsAStringAndWindowDispatchesCustomEvents() {
        Document document = parser.parse("""
                <html><body><script>
                  const received = [];
                  window.addEventListener('kampagne', event => {
                    received.push(event.detail && event.detail.wert);
                  });
                  const ok = window.dispatchEvent(
                      new CustomEvent('kampagne', { detail: { wert: 42 } }));
                  console.log(document.referrer, typeof document.referrer, ok, received.join(','));
                </script></body></html>
                """, "http://example.test/index.html");

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log:  string true 42"), result.consoleMessages());
    }

    @Test
    public void performanceMarksAndMeasuresAreQueryable() {
        Document document = parser.parse("""
                <html><body><script>
                  performance.mark('anfang');
                  performance.mark('ende');
                  const start = performance.getEntriesByName('anfang')[0];
                  console.log(start && start.entryType, start && start.startTime >= 0,
                              performance.getEntriesByType('mark').length,
                              performance.getEntriesByType('navigation').length,
                              typeof performance.measure);
                </script></body></html>
                """, "http://example.test/index.html");

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: mark true 2 1 function"), result.consoleMessages());
    }

    @Test
    public void datasetReflectsDataAttributesAndContentWindowExposesPromise() {
        Document document = parser.parse("""
                <html><body><script>
                  const script = document.createElement('script');
                  script.dataset.golemTag = 'true';
                  script.setAttribute('data-weitere-info', 'x');
                  const frame = document.createElement('iframe');
                  document.body.appendChild(frame);
                  console.log(script.dataset.golemTag, script.getAttribute('data-golem-tag'),
                              script.dataset.weitereInfo,
                              typeof frame.contentWindow.Promise,
                              frame.contentWindow.Promise === Promise);
                </script></body></html>
                """, "http://example.test/index.html");

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: true true x function true"), result.consoleMessages());
    }

    @Test
    public void abortControllerScreenAndNodeExpandosAreAvailable() {
        Document document = parser.parse("""
                <html><body><script>
                  const controller = new AbortController();
                  let aborted = false;
                  controller.signal.addEventListener('abort', () => { aborted = true; });
                  controller.abort(new Error('stopp'));
                  const text = document.createTextNode('x');
                  Object.defineProperty(text, '__vue__', { value: { marker: 7 } });
                  console.log(controller.signal.aborted, aborted,
                              typeof screen.width, screen.width > 0,
                              text.__vue__.marker);
                </script></body></html>
                """, "http://example.test/index.html");

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: true true number true 7"), result.consoleMessages());
    }

    @Test
    public void cssEscapeAndLegacyEscapeGlobalsAreAvailable() {
        Document document = parser.parse("""
                <html><body><script>
                  console.log(typeof CSS.escape, CSS.escape('.klasse#id'),
                              CSS.escape('a b'), CSS.escape('123'),
                              escape('a b c'), unescape('%E4%20x'),
                              unescape('%u00E4'));
                </script></body></html>
                """, "http://example.test/index.html");

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: function \\.klasse\\#id a\\ b \\31 23 "
                        + "a%20b%20c ä x ä"),
                result.consoleMessages());
    }

    @Test
    public void mediaAndObserverAndCodingGlobalsAreAvailable() {
        Document document = parser.parse("""
                <html><head></head><body>
                  <script id="s"></script>
                  <div id="d"></div>
                </body></html>
                """, "http://example.test/index.html");

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, null, null,
                new StyleSheetRegistry(), () -> {
                })) {
            JsExecutionResult result = runtime.execute(new JavaScriptSource("""
                    const script = document.getElementById('s');
                    const div = document.getElementById('d');
                    const video = document.createElement('video');
                    const io = new IntersectionObserver(() => {});
                    const ro = new ResizeObserver(() => {});
                    io.observe(div); ro.observe(div);
                    const controller = new AbortController();
                    console.log(script instanceof HTMLScriptElement,
                                div instanceof HTMLDivElement,
                                video instanceof HTMLVideoElement,
                                typeof video.play, typeof div.getBoundingClientRect,
                                document.location.protocol, document.location.hostname,
                                document.scripts.length,
                                typeof atob, atob('SGVsbG8='), btoa('Hello'),
                                typeof requestAnimationFrame,
                                typeof PromiseRejectionEvent,
                                new PromiseRejectionEvent('unhandledrejection',
                                    { promise: Promise.resolve(), reason: 'x' }).reason,
                                new Blob(['abc']).size, typeof io.takeRecords);
                    """, null, "globals-test.js"));
            runtime.awaitIdle();
            result = runtime.snapshot();

            assertFalse(String.valueOf(result.errors()), result.hasErrors());
            assertEquals(List.of("log: true true true function function "
                            + "http: example.test 1 function Hello SGVsbG8= "
                            + "function function x 3 function"),
                    result.consoleMessages());
        }
    }

    @Test
    public void supportsLivingStandardNodeAndParentNodeMutations() {
        Document document = parse("""
                <html><body><div id="host"><span id="old"></span></div>
                <script>
                  const host = document.getElementById('host');
                  const old = document.getElementById('old');
                  const observer = new MutationObserver(() => {});
                  observer.observe(host, { childList: true });
                  const div1 = document.createElement('div');
                  const div2 = document.createElement('div');
                  host.replaceChildren(div1, 'Text', div2);
                  let records = observer.takeRecords();
                  console.log(records.length, records[0].addedNodes.length,
                              records[0].addedNodes[1].nodeType, records[0].removedNodes.length,
                              records[0].removedNodes[0] === old, records[0].previousSibling === old,
                              records[0].nextSibling === null, host.childNodes.length,
                              host.childNodes[0] === div1, host.childNodes[1].textContent,
                              host.childNodes[2] === div2, old.parentNode === null);
                  const em = document.createElement('em');
                  const textNode = host.childNodes[1];
                  div2.before(em);
                  records = observer.takeRecords();
                  console.log(records.length, records[0].addedNodes[0] === em,
                              records[0].previousSibling === textNode,
                              records[0].nextSibling === div2);
                  div1.remove();
                  console.log(host.childNodes.length, div1.parentNode === null);
                  host.append('tail', null);
                  host.prepend(div1);
                  console.log(host.firstChild === div1, host.lastChild.textContent,
                              host.children.length);
                  const fragment = document.createDocumentFragment();
                  fragment.append(document.createElement('p'));
                  host.replaceChildren(fragment);
                  console.log(host.childNodes.length, host.firstChild.tagName,
                              fragment.childNodes.length);
                </script></body></html>
                """);

        JsExecutionResult result = engine.runScripts(document);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of(
                        "log: 1 3 3 1 true true true 3 true Text true true",
                        "log: 1 true true true",
                        "log: 3 true",
                        "log: true tail 3",
                        "log: 1 P 0"),
                result.consoleMessages());
    }

}

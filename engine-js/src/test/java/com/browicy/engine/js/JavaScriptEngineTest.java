package com.browicy.engine.js;

import com.browicy.engine.css.StyleApplicator;
import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.html.HtmlParser;
import org.junit.Test;

import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JavaScriptEngineTest {

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
        assertEquals("color:red;", document.getElementById("target").getAttribute("style"));
        assertEquals("true:false:true", document.getElementById("target").getAttribute("data-css"));
        assertEquals("world", document.getElementById("target").getTextContent());
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

}

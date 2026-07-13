package com.browicy.engine.js;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.html.HtmlParser;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class MutationObserverTest {

    private final HtmlParser parser = new HtmlParser();
    private final JavaScriptEngine engine = new JavaScriptEngine();

    @Test
    public void batchesAndFiltersMutationsAtTheMicrotaskCheckpoint() {
        Document document = parse("""
                <html><body><div id="target">before<em id="tail"></em></div></body></html>
                """);

        JsExecutionResult result = engine.execute(document, """
                const target = document.getElementById('target');
                const tail = document.getElementById('tail');
                let synchronous = true;
                const observer = new MutationObserver((records, deliveredObserver) => {
                  console.log('delivery', synchronous, records.length, deliveredObserver === observer);
                  for (const record of records) {
                    console.log(record.type, record.attributeName, record.oldValue,
                                record.addedNodes.length, record.removedNodes.length,
                                record.previousSibling && record.previousSibling.nodeName,
                                record.nextSibling && record.nextSibling.nodeName);
                  }
                });
                observer.observe(target, {
                  subtree: true, childList: true,
                  attributes: true, attributeOldValue: true,
                  characterData: true, characterDataOldValue: true,
                  attributeFilter: ['data-state']
                });
                target.setAttribute('class', 'ignored');
                target.setAttribute('data-state', 'one');
                target.setAttribute('data-state', 'two');
                const added = document.createElement('span');
                target.insertBefore(added, tail);
                target.firstChild.data = 'after';
                synchronous = false;
                """);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of(
                "log: delivery false 4 true",
                "log: attributes data-state null 0 0 null null",
                "log: attributes data-state one 0 0 null null",
                "log: childList null null 1 0 #text EM",
                "log: characterData null before 0 0 null null"),
                result.consoleMessages());
    }

    @Test
    public void takeRecordsEmptiesTheQueueAndDisconnectDropsFurtherChanges() {
        Document document = parse("""
                <html><body><div id="target"></div></body></html>
                """);

        JsExecutionResult result = engine.execute(document, """
                const target = document.getElementById('target');
                let callbacks = 0;
                const observer = new MutationObserver(() => callbacks++);
                observer.observe(target, { childList: true });
                target.appendChild(document.createElement('span'));
                const records = observer.takeRecords();
                console.log(records.length, records[0].type, records[0].target === target);
                observer.disconnect();
                target.appendChild(document.createElement('em'));
                queueMicrotask(() => console.log('callbacks', callbacks, observer.takeRecords().length));
                """);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of(
                "log: 1 childList true",
                "log: callbacks 0 0"), result.consoleMessages());
    }

    @Test
    public void observesJavaSideMutationsAndSupportsImplicitOptions() {
        Document document = parse("""
                <html><body><div id="target"></div><output id="result"></output></body></html>
                """);
        Element target = document.getElementById("target");

        try (PageRuntime runtime = engine.createPageRuntime(document)) {
            JsExecutionResult setup = runtime.execute(new JavaScriptSource("""
                    const observer = new MutationObserver(records => {
                      document.getElementById('result').textContent =
                          records[0].attributeName + ':' + records[0].oldValue;
                    });
                    observer.observe(document.getElementById('target'), { attributeOldValue: true });
                    """, null, "mutation-observer.js"));
            assertFalse(String.valueOf(setup.errors()), setup.hasErrors());

            runtime.submitTask(() -> target.setAttribute("data-ready", "yes")).join();

            assertEquals("data-ready:null",
                    document.getElementById("result").getTextContent());
        }
    }

    @Test
    public void rejectsInvalidCallbacksAndObservationOptions() {
        Document document = parse("<html><body></body></html>");

        JsExecutionResult result = engine.execute(document, """
                for (const operation of [
                  () => new MutationObserver(null),
                  () => new MutationObserver(() => {}).observe(document.body, {}),
                  () => new MutationObserver(() => {}).observe(
                      document.body, { attributes: false, attributeOldValue: true }),
                  () => new MutationObserver(() => {}).observe(
                      document.body, { characterData: false, characterDataOldValue: true })
                ]) {
                  try { operation(); } catch (error) { console.log(error instanceof TypeError); }
                }
                """);

        assertFalse(String.valueOf(result.errors()), result.hasErrors());
        assertEquals(List.of("log: true", "log: true", "log: true", "log: true"),
                result.consoleMessages());
    }

    private Document parse(String html) {
        return parser.parse(html, "about:test");
    }
}

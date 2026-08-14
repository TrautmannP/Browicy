package com.browicy.integration;

import com.browicy.engine.css.StyleSheetRegistry;
import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.html.HtmlParser;
import com.browicy.engine.js.JavaScriptEngine;
import com.browicy.engine.js.JavaScriptSource;
import com.browicy.engine.js.JsExecutionResult;
import com.browicy.engine.js.PageNavigationHandler;
import com.browicy.engine.js.PageRuntime;
import com.browicy.engine.js.PageRuntimeObserver;
import com.browicy.ui.render.RenderLayoutMetrics;

/**
 * Test-Harness für JS-Layout-Integrationstests: parst ein HTML-Dokument,
 * registriert dessen {@code <style>}-Blöcke und treibt eine echte
 * {@link PageRuntime} mit Layout-Metrik-Zugriff ({@link RenderLayoutMetrics}).
 */
final class JsLayoutHarness implements AutoCloseable {

    private final Document document;
    private final PageRuntime runtime;
    private final StyleSheetRegistry styleSheets;
    private final int viewportWidth;
    private final int viewportHeight;

    private JsLayoutHarness(Document document, PageRuntime runtime,
                            StyleSheetRegistry styleSheets,
                            int viewportWidth, int viewportHeight) {
        this.document = document;
        this.runtime = runtime;
        this.styleSheets = styleSheets;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
    }

    static JsLayoutHarness open(String html, int viewportWidth, int viewportHeight) {
        Document document = new HtmlParser().parse(html, "about:test");
        StyleSheetRegistry styleSheets = new StyleSheetRegistry();
        int sourceOrder = 0;
        for (Element style : document.getElementsByTagName("style")) {
            styleSheets.register(sourceOrder++, style.getTextContent());
        }
        RenderLayoutMetrics metrics = new RenderLayoutMetrics(
                styleSheets, viewportWidth, viewportHeight);
        PageRuntime runtime = new JavaScriptEngine().createPageRuntime(
                document, PageRuntimeObserver.NO_OP, null, null, styleSheets,
                () -> { }, PageNavigationHandler.NO_OP, metrics);
        return new JsLayoutHarness(document, runtime, styleSheets,
                viewportWidth, viewportHeight);
    }

    /** Führt das Skript aus und wartet, bis der Event-Loop inaktiv ist. */
    JsExecutionResult execute(String script) {
        JsExecutionResult result = runtime.execute(
                new JavaScriptSource(script, null, "js-layout-test.js"));
        runtime.awaitIdle();
        return result;
    }

    Document document() {
        return document;
    }

    PageRuntime runtime() {
        return runtime;
    }

    StyleSheetRegistry styleSheets() {
        return styleSheets;
    }

    int viewportWidth() {
        return viewportWidth;
    }

    int viewportHeight() {
        return viewportHeight;
    }

    @Override
    public void close() {
        runtime.close();
    }
}

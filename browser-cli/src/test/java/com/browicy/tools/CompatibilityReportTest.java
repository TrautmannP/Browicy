package com.browicy.tools;

import com.browicy.engine.css.StyleSheetRegistry;
import com.browicy.engine.dom.Document;
import com.browicy.engine.html.HtmlParser;
import com.browicy.engine.js.JsExecutionResult;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CompatibilityReportTest {

    @Test
    public void reportsAndDeduplicatesUnsupportedFeatures() {
        Document document = new HtmlParser().parse("""
                <html><head><style>.card { filter: blur(5px); display: grid; color: red }</style></head>
                <body><div id='app' style='filter: grayscale(1)'><canvas></canvas><my-widget></my-widget></div></body></html>
                """, "https://example.test/");
        StyleSheetRegistry styles = new StyleSheetRegistry();
        styles.register(0, document.getElementsByTagName("style").getFirst(),
                ".card { filter: blur(5px); display: grid; color: red }");
        JsExecutionResult javascript = new JsExecutionResult(List.of(), List.of(
                "ReferenceError: ResizeObserver is not defined (app.js:3:2)"));

        Map<String, Object> report = CompatibilityReport.build(document, styles, javascript);

        assertEquals(5, report.get("unsupportedFeatures"));
        assertEquals(6, report.get("occurrences"));
        String issues = report.get("issues").toString();
        assertTrue(issues.contains("property:filter"));
        assertTrue(issues.contains("value:display"));
        assertTrue(issues.contains("global:ResizeObserver"));
        assertTrue(issues.contains("element:canvas"));
        assertTrue(issues.contains("custom-elements"));
    }

    @Test
    public void doesNotReportSupportedCssOrArbitraryApplicationErrors() {
        Document document = new HtmlParser().parse(
                "<div style='color:red;display:flex;gap:1rem;aspect-ratio:16/9'>"
                        + "<img style='object-fit:cover'></div>", "https://example.test/");
        JsExecutionResult javascript = new JsExecutionResult(List.of(), List.of(
                "Error: application failed (app.js:1:1)"));

        Map<String, Object> report = CompatibilityReport.build(
                document, new StyleSheetRegistry(), javascript);

        assertEquals(0, report.get("unsupportedFeatures"));
    }

    @Test
    public void doesNotReportSupportedFontFaceOrMediaRules() {
        Document document = new HtmlParser().parse(
                "<html><body><p>ok</p></body></html>", "https://example.test/");
        StyleSheetRegistry styles = new StyleSheetRegistry();
        styles.register(0, """
                @font-face { font-family: Demo; src: url(demo.ttf) format('truetype') }
                @media (min-width: 10px) { .wide { color: red } }
                """);

        Map<String, Object> report = CompatibilityReport.build(
                document, styles, JsExecutionResult.EMPTY);

        assertEquals(0, report.get("unsupportedFeatures"));
        assertTrue(!report.get("issues").toString().contains("at-rule:@media"));
        assertTrue(!report.get("issues").toString().contains("font-face"));
    }

    @Test
    public void doesNotReportSupportedSupportsOrNamespaceRules() {
        Document document = new HtmlParser().parse(
                "<html><body><p>ok</p></body></html>", "https://example.test/");
        StyleSheetRegistry styles = new StyleSheetRegistry();
        styles.register(0, """
                @supports (display: flex) { .flexed { color: red } }
                @namespace "http://www.w3.org/1999/xhtml";
                @keyframes spin { from { opacity: 0 } to { opacity: 1 } }
                """);

        Map<String, Object> report = CompatibilityReport.build(
                document, styles, JsExecutionResult.EMPTY);

        String issues = report.get("issues").toString();
        assertTrue(!issues.contains("at-rule:@supports"));
        assertTrue(!issues.contains("at-rule:@namespace"));
        assertTrue(issues.contains("at-rule:@keyframes"));
    }
}

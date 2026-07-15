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
                <html><head><style>.card { box-shadow: 0 1px black; display: grid; color: red }</style></head>
                <body><div id='app' style='box-shadow: 0 2px black'><canvas></canvas><my-widget></my-widget></div></body></html>
                """, "https://example.test/");
        StyleSheetRegistry styles = new StyleSheetRegistry();
        styles.register(0, document.getElementsByTagName("style").getFirst(),
                ".card { box-shadow: 0 1px black; display: grid; color: red }");
        JsExecutionResult javascript = new JsExecutionResult(List.of(), List.of(
                "ReferenceError: ResizeObserver is not defined (app.js:3:2)"));

        Map<String, Object> report = CompatibilityReport.build(document, styles, javascript);

        assertEquals(5, report.get("unsupportedFeatures"));
        assertEquals(6, report.get("occurrences"));
        String issues = report.get("issues").toString();
        assertTrue(issues.contains("property:box-shadow"));
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
}

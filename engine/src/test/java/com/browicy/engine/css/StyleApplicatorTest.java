package com.browicy.engine.css;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.html.HtmlParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class StyleApplicatorTest {

    @Test
    public void laterRuleWinsAtEqualSpecificity() {
        Element heading = parseHeading("""
                h1 { color: red; font-size: 18px; }
                h1 { color: blue; }
                """, "");

        assertEquals("blue", heading.getComputedStyles().get("color"));
        assertEquals("18px", heading.getComputedStyles().get("font-size"));
    }

    @Test
    public void laterStyleBlockWinsAtEqualSpecificity() {
        Document document = new HtmlParser().parse("""
                <html><head>
                  <style>h1 { color: red; }</style>
                  <style>h1 { color: green; }</style>
                </head><body><h1>Titel</h1></body></html>
                """);

        assertEquals("green", document.getBody().findFirst("h1")
                .getComputedStyles().get("color"));
    }

    @Test
    public void inlineStyleOverridesStylesheetAndIgnoresUnsupportedDeclarations() {
        Element heading = parseHeading(
                "h1 { color: red; font-size: 18px; }",
                "color: blue; border: 1px solid; font-size: 2em");

        assertEquals("blue", heading.getComputedStyles().get("color"));
        assertEquals("2em", heading.getComputedStyles().get("font-size"));
        assertFalse(heading.getComputedStyles().containsKey("border"));
    }

    @Test
    public void appliesInlineStyleWithoutHeadElement() {
        Document document = new HtmlParser().parse(
                "<h1 style=\"color: red\">Titel</h1>");

        assertEquals("red", document.getDocumentElement().getComputedStyles().get("color"));
    }

    @Test
    public void applyingAgainRecomputesStyles() {
        Document document = new HtmlParser().parse("""
                <html><head><style>h1 { color: red; }</style></head>
                <body><h1 style="font-size: 20px">Titel</h1></body></html>
                """);
        Element heading = document.getBody().findFirst("h1");
        heading.setAttribute("style", "color: blue");

        new StyleApplicator().apply(document);

        assertEquals("blue", heading.getComputedStyles().get("color"));
        assertFalse(heading.getComputedStyles().containsKey("font-size"));
    }

    private static Element parseHeading(String css, String inlineStyle) {
        Document document = new HtmlParser().parse("""
                <html><head><style>%s</style></head>
                <body><h1 style="%s">Titel</h1></body></html>
                """.formatted(css, inlineStyle));
        return document.getBody().findFirst("h1");
    }
}

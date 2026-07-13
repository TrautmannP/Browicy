package com.browicy.engine.css;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.html.HtmlParser;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
    public void classSelectorMatchesWhitespaceSeparatedClassTokens() {
        Document document = parse("""
                .notice { color: red; }
                .card.active { background-color: yellow; }
                """, """
                <p class="message notice highlighted">Hinweis</p>
                <div class="card active">Aktiv</div>
                <div class="card">Inaktiv</div>
                """);
        List<Element> bodyChildren = document.getBody().getChildElements();

        Element notice = bodyChildren.get(0);
        assertEquals(List.of("message", "notice", "highlighted"), notice.getClassNames());
        assertTrue(notice.hasClass("notice"));
        assertFalse(notice.hasClass("not"));
        assertEquals("red", notice.getComputedStyles().get("color"));
        assertEquals("yellow", bodyChildren.get(1).getComputedStyles().get("background-color"));
        assertNull(bodyChildren.get(2).getComputedStyles().get("background-color"));
    }

    @Test
    public void idSelectorMatchesElementId() {
        Document document = parse("#warning { color: red; }",
                "<p id=\"warning\">Warnung</p>");
        Element warning = document.getBody().findFirst("p");

        assertEquals("warning", warning.getId());
        assertEquals("red", warning.getComputedStyles().get("color"));
    }

    @Test
    public void combinedSelectorRequiresTagIdAndEveryClass() {
        Document document = parse("p.notice.critical#message { font-weight: bold; }", """
                <p id="message" class="notice critical">Treffer</p>
                <p id="other" class="notice critical">Falsche ID</p>
                <div id="message" class="notice critical">Falscher Tag</div>
                <p id="message" class="notice">Fehlende Klasse</p>
                """);
        List<Element> elements = document.getBody().getChildElements();

        assertEquals("bold", elements.get(0).getComputedStyles().get("font-weight"));
        assertNull(elements.get(1).getComputedStyles().get("font-weight"));
        assertNull(elements.get(2).getComputedStyles().get("font-weight"));
        assertNull(elements.get(3).getComputedStyles().get("font-weight"));
    }

    @Test
    public void higherSpecificityWinsRegardlessOfRuleOrder() {
        Document document = parse("""
                #message { color: red; }
                .notice { color: orange; }
                p { color: black; }
                """, "<p id=\"message\" class=\"notice\">Text</p>");

        assertEquals("red", document.getBody().findFirst("p")
                .getComputedStyles().get("color"));
    }

    @Test
    public void cascadeIsResolvedIndependentlyForEveryProperty() {
        Document document = parse("""
                .card { color: red; padding: 4px; }
                #main { color: blue; }
                """, "<div id=\"main\" class=\"card\">Text</div>");
        Element card = document.getBody().findFirst("div");

        assertEquals("blue", card.getComputedStyles().get("color"));
        assertEquals("4px", card.getComputedStyles().get("padding-top"));
        assertEquals("4px", card.getComputedStyles().get("padding-right"));
        assertEquals("4px", card.getComputedStyles().get("padding-bottom"));
        assertEquals("4px", card.getComputedStyles().get("padding-left"));
    }

    @Test
    public void universalSelectorMatchesEveryElementButHasZeroSpecificity() {
        Document document = parse("""
                p { color: blue; }
                * { color: red; background-color: yellow; }
                """, "<p>Absatz</p><div>Box</div>");
        List<Element> elements = document.getBody().getChildElements();

        assertEquals("blue", elements.get(0).getComputedStyles().get("color"));
        assertEquals("yellow", elements.get(0).getComputedStyles().get("background-color"));
        assertEquals("red", elements.get(1).getComputedStyles().get("color"));
    }

    @Test
    public void inlineStyleOverridesStylesheetAndExpandsBorder() {
        Element heading = parseHeading(
                "#title { color: red; font-size: 18px; }",
                "color: blue; position: absolute; border: 1px solid green; font-size: 2em",
                "id=\"title\"");

        assertEquals("blue", heading.getComputedStyles().get("color"));
        assertEquals("2em", heading.getComputedStyles().get("font-size"));
        assertEquals("1px", heading.getComputedStyles().get("border-left-width"));
        assertEquals("solid", heading.getComputedStyles().get("border-top-style"));
        assertEquals("green", heading.getComputedStyles().get("border-right-color"));
        assertFalse(heading.getComputedStyles().containsKey("position"));
    }

    @Test
    public void appliesInlineStyleWithoutHeadElement() {
        Document document = new HtmlParser().parse(
                "<h1 style=\"color: red\">Titel</h1>");

        assertEquals("red", document.getDocumentElement().getComputedStyles().get("color"));
    }

    @Test
    public void unsupportedSelectorDoesNotDamageFollowingRule() {
        Document document = parse("""
                p:hover { color: red; }
                .notice { color: blue; }
                """, "<p class=\"notice\">Text</p>");

        assertEquals("blue", document.getBody().findFirst("p")
                .getComputedStyles().get("color"));
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

    private static Document parse(String css, String body) {
        return new HtmlParser().parse("""
                <html><head><style>%s</style></head><body>%s</body></html>
                """.formatted(css, body));
    }

    private static Element parseHeading(String css, String inlineStyle) {
        return parseHeading(css, inlineStyle, "");
    }

    private static Element parseHeading(String css, String inlineStyle, String attributes) {
        Document document = new HtmlParser().parse("""
                <html><head><style>%s</style></head>
                <body><h1 %s style="%s">Titel</h1></body></html>
                """.formatted(css, attributes, inlineStyle));
        return document.getBody().findFirst("h1");
    }
}

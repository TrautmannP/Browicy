package com.browicy.engine.css;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.html.HtmlParser;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CssParserTest {

    @Test
    public void parsesSupportedDeclarations() {
        List<CssRule> rules = new CssParser().parse(
                "h1 { color: #ff0000; font-size: 24px; }");

        assertEquals(1, rules.size());
        assertEquals("h1", rules.get(0).selector());
        assertEquals("#ff0000", rules.get(0).declarations().get("color"));
        assertEquals("24px", rules.get(0).declarations().get("font-size"));
    }

    @Test
    public void appliesRulesFromHeadStyleElements() {
        Document document = new HtmlParser().parse("""
                <html><head><style>
                  h1 { color: #ff0000; font-size: 1.5em; }
                  p { color: blue; }
                </style></head><body><h1>Titel</h1><p>Text</p></body></html>
                """);

        Element body = document.getBody();
        assertEquals("#ff0000", body.findFirst("h1").getComputedStyles().get("color"));
        assertEquals("1.5em", body.findFirst("h1").getComputedStyles().get("font-size"));
        assertEquals("blue", body.findFirst("p").getComputedStyles().get("color"));
    }

    @Test
    public void ignoresUnknownPropertiesInvalidValuesAndMalformedRules() {
        List<CssRule> rules = new CssParser().parse("""
                h1 { border: 1px solid; color: definitely-not-a-color; font-size: huge; }
                p { color: red; }
                broken rule
                """);

        assertEquals(1, rules.size());
        assertEquals("p", rules.get(0).selector());
        assertFalse(rules.get(0).declarations().containsKey("border"));
    }

    @Test
    public void supportsCommentsSelectorGroupsAndMissingFinalSemicolon() {
        List<CssRule> rules = new CssParser().parse("""
                /* gemeinsame Überschrift */
                H1, h2 { COLOR: red; font-size: 20PX }
                """);

        assertEquals(2, rules.size());
        assertEquals("h1", rules.get(0).selector());
        assertEquals("h2", rules.get(1).selector());
        assertEquals("red", rules.get(0).declarations().get("color"));
        assertEquals("20px", rules.get(1).declarations().get("font-size"));
    }

    @Test
    public void recoversAfterMalformedInput() {
        List<CssRule> rules = new CssParser().parse("""
                h1 { color: red;
                this is not css
                p { color: blue; }
                div { font-size: 12px; }
                """);

        assertEquals(2, rules.size());
        assertEquals("p", rules.get(0).selector());
        assertEquals("div", rules.get(1).selector());
    }
}

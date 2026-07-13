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
    public void parsesSupportedDeclarationsAndExpandsBoxShorthands() {
        List<CssRule> rules = new CssParser().parse("""
                h1 {
                  color: #ff0000;
                  background-color: white;
                  font-size: 24px;
                  margin: 1px 2px 3px 4px;
                  padding: 5px 6px;
                  border: 2px solid blue;
                }
                """);

        assertEquals(1, rules.size());
        assertEquals("h1", rules.getFirst().selector());
        var declarations = rules.getFirst().declarations();
        assertEquals("#ff0000", declarations.get("color"));
        assertEquals("white", declarations.get("background-color"));
        assertEquals("24px", declarations.get("font-size"));
        assertEquals("1px", declarations.get("margin-top"));
        assertEquals("2px", declarations.get("margin-right"));
        assertEquals("3px", declarations.get("margin-bottom"));
        assertEquals("4px", declarations.get("margin-left"));
        assertEquals("5px", declarations.get("padding-top"));
        assertEquals("6px", declarations.get("padding-right"));
        assertEquals("2px", declarations.get("border-left-width"));
        assertEquals("solid", declarations.get("border-bottom-style"));
        assertEquals("blue", declarations.get("border-top-color"));
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
                h1 { position: absolute; color: definitely-not-a-color; font-size: huge; }
                p { color: red; }
                broken rule
                """);

        assertEquals(1, rules.size());
        assertEquals("p", rules.getFirst().selector());
        assertFalse(rules.getFirst().declarations().containsKey("position"));
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

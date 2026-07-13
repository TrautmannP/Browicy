package com.browicy.engine.css;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        assertEquals("h1", rules.getFirst().selector().toString());
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
    public void parsesClassIdCombinedAndUniversalSelectors() {
        List<CssRule> rules = new CssParser().parse("""
                .notice { color: red; }
                #warning { color: red; }
                div.card.highlighted#main { color: red; }
                * { color: red; }
                """);

        assertEquals(4, rules.size());
        assertEquals(".notice", rules.get(0).selector().toString());
        assertEquals("#warning", rules.get(1).selector().toString());
        assertEquals("div.card.highlighted#main", rules.get(2).selector().toString());
        assertEquals("*", rules.get(3).selector().toString());

        assertTrue(rules.get(2).selector() instanceof SimpleSelector);
        SimpleSelector combined = (SimpleSelector) rules.get(2).selector();
        assertEquals("div", combined.tagName());
        assertEquals("main", combined.id());
        assertEquals(List.of("card", "highlighted"), combined.classes());
        assertEquals(new Specificity(1, 2, 1), combined.specificity());
    }

    @Test
    public void assignsIncreasingSourceOrderToExpandedSelectorRules() {
        List<CssRule> rules = new CssParser().parse("""
                h1, h2 { color: red; }
                .notice { color: blue; }
                """);

        assertEquals(0, rules.get(0).sourceOrder());
        assertEquals(0, rules.get(1).sourceOrder());
        assertEquals(1, rules.get(2).sourceOrder());
    }

    @Test
    public void ignoresUnknownPropertiesInvalidValuesAndMalformedRules() {
        List<CssRule> rules = new CssParser().parse("""
                h1 { position: absolute; color: definitely-not-a-color; font-size: huge; }
                p { color: red; }
                broken rule
                """);

        assertEquals(1, rules.size());
        assertEquals("p", rules.getFirst().selector().toString());
        assertFalse(rules.getFirst().declarations().containsKey("position"));
    }

    @Test
    public void supportsCommentsSelectorGroupsAndMissingFinalSemicolon() {
        List<CssRule> rules = new CssParser().parse("""
                /* gemeinsame Überschrift */
                H1, h2 { COLOR: red; font-size: 20PX }
                """);

        assertEquals(2, rules.size());
        assertEquals("h1", rules.get(0).selector().toString());
        assertEquals("h2", rules.get(1).selector().toString());
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
        assertEquals("p", rules.get(0).selector().toString());
        assertEquals("div", rules.get(1).selector().toString());
    }

    @Test
    public void skipsUnsupportedSelectorsWithoutLosingFollowingRules() {
        List<CssRule> rules = new CssParser().parse("""
                p:hover { color: red; }
                div > p { color: green; }
                .notice { color: blue; }
                """);

        assertEquals(1, rules.size());
        assertEquals(".notice", rules.getFirst().selector().toString());
    }
}

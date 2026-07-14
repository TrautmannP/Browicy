package com.browicy.engine.css;

import com.browicy.engine.selectors.ComplexSelector;
import com.browicy.engine.selectors.CompoundSelector;
import com.browicy.engine.selectors.Specificity;
import java.util.List;
import java.util.Map;
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
                  box-sizing: border-box;
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
        assertEquals("border-box", declarations.get("box-sizing"));
    }

    @Test
    public void acceptsOnlySupportedBoxSizingValues() {
        CssParser parser = new CssParser();

        assertTrue(parser.supportsProperty("box-sizing"));
        assertTrue(parser.supports("box-sizing", "content-box"));
        assertTrue(parser.supports("box-sizing", "border-box"));
        assertFalse(parser.supports("box-sizing", "padding-box"));
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

        assertTrue(rules.get(2).selector() instanceof ComplexSelector);
        ComplexSelector combined = (ComplexSelector) rules.get(2).selector();
        CompoundSelector compound = combined.steps().getFirst().selector();
        assertEquals("div", compound.typeName());
        assertEquals("main", compound.id());
        assertEquals(List.of("card", "highlighted"), compound.classes());
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
                h1 { position: fixed; color: definitely-not-a-color; font-size: huge; }
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
    public void exposesTheSameCapabilityChecksUsedByTheJavaScriptCssApi() {
        CssParser parser = new CssParser();

        assertTrue(parser.supportsProperty("background-color"));
        assertTrue(parser.supports("display", "block"));
        assertFalse(parser.supports("display", "grid"));
        assertTrue(parser.supportsProperty("position"));
        assertTrue(parser.supports("position", "absolute"));
        assertFalse(parser.supports("position", "fixed"));
    }

    @Test
    public void parsesDimensionsAutoMarginsAlignmentAndInlineBlock() {
        var declarations = new CssParser().parseDeclarations("""
                width: 12em; height: 50%; margin: 1px auto 2px;
                text-align: center; display: inline-block
                """);

        assertEquals("12em", declarations.get("width"));
        assertEquals("50%", declarations.get("height"));
        assertEquals("auto", declarations.get("margin-left"));
        assertEquals("auto", declarations.get("margin-right"));
        assertEquals("center", declarations.get("text-align"));
        assertEquals("inline-block", declarations.get("display"));
    }

    @Test
    public void parsesPositionAndSignedOffsets() {
        var declarations = new CssParser().parseDeclarations("""
                position: absolute; top: -5px; right: 10%; bottom: auto; left: 2em
                """);

        assertEquals("absolute", declarations.get("position"));
        assertEquals("-5px", declarations.get("top"));
        assertEquals("10%", declarations.get("right"));
        assertEquals("auto", declarations.get("bottom"));
        assertEquals("2em", declarations.get("left"));
    }

    @Test
    public void acceptsSupportedDimensionsAndRejectsInvalidValues() {
        CssParser parser = new CssParser();

        assertTrue(parser.supports("width", "120px"));
        assertTrue(parser.supports("height", "auto"));
        assertTrue(parser.supports("text-align", "right"));
        assertFalse(parser.supports("width", "-1px"));
        assertTrue(parser.supports("height", "10vh"));
        assertTrue(parser.supports("width", "25vw"));
        assertTrue(parser.supports("font-size", "1.5rem"));
        assertFalse(parser.supports("text-align", "justify"));
    }

    @Test
    public void parsesSizeConstraintsOverflowAndVerticalAlignment() {
        var declarations = new CssParser().parseDeclarations("""
                min-width: 20px; max-width: 75%; min-height: auto; max-height: none;
                overflow: hidden; vertical-align: middle
                """);

        assertEquals("20px", declarations.get("min-width"));
        assertEquals("75%", declarations.get("max-width"));
        assertEquals("auto", declarations.get("min-height"));
        assertEquals("none", declarations.get("max-height"));
        assertEquals("hidden", declarations.get("overflow"));
        assertEquals("middle", declarations.get("vertical-align"));
    }


    @Test
    public void invalidSelectorListDiscardsTheWholeCssRule() {
        List<CssRule> rules = new CssParser().parse("""
                .notice, p:focus { color: red; }
                .notice { color: blue; }
                """);

        assertEquals(1, rules.size());
        assertEquals(".notice", rules.getFirst().selector().toString());
        assertEquals("blue", rules.getFirst().declarations().get("color"));
    }

    @Test
    public void supportsCombinatorsAndSkipsUnsupportedSelectors() {
        List<CssRule> rules = new CssParser().parse("""
                p:focus { color: red; }
                div > p { color: green; }
                main .notice { color: blue; }
                """);

        assertEquals(2, rules.size());
        assertEquals("div > p", rules.get(0).selector().toString());
        assertEquals("main .notice", rules.get(1).selector().toString());
    }

    @Test
    public void parsesTableDisplayRolesAndCollapsedBorders() {
        CssParser parser = new CssParser();
        Map<String, String> declarations = parser.parseDeclarations("""
                display: table-cell;
                border-collapse: collapse;
                """);

        assertEquals("table-cell", declarations.get("display"));
        assertEquals("collapse", declarations.get("border-collapse"));
        assertTrue(parser.supports("display", "table-row"));
        assertTrue(parser.supports("border-collapse", "separate"));
    }

    @Test
    public void parsesFloatAndClear() {
        Map<String, String> declarations = new CssParser().parseDeclarations(
                "float:right;clear:both");

        assertEquals("right", declarations.get("float"));
        assertEquals("both", declarations.get("clear"));
    }

    @Test
    public void expandsFontShorthandAndParsesLineHeight() {
        Map<String, String> declarations = new CssParser().parseDeclarations(
                "font:italic bold 20px/1.5 monospace");

        assertEquals("italic", declarations.get("font-style"));
        assertEquals("bold", declarations.get("font-weight"));
        assertEquals("20px", declarations.get("font-size"));
        assertEquals("1.5", declarations.get("line-height"));
        assertEquals("monospace", declarations.get("font-family"));
    }

    @Test
    public void parsesBackgroundImageRepeatAndPosition() {
        Map<String, String> declarations = new CssParser().parseDeclarations("""
                background-image:url("https://example.test/CaseSensitive.png");
                background-repeat:no-repeat;
                background-position:center right;
                """);

        assertEquals("url(\"https://example.test/CaseSensitive.png\")",
                declarations.get("background-image"));
        assertEquals("no-repeat", declarations.get("background-repeat"));
        assertEquals("right", declarations.get("background-position-x"));
        assertEquals("center", declarations.get("background-position-y"));
    }
}

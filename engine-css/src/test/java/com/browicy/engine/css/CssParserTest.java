package com.browicy.engine.css;

import com.browicy.engine.selectors.ComplexSelector;
import com.browicy.engine.selectors.CompoundSelector;
import com.browicy.engine.selectors.Specificity;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
                h1 { font-size: huge; color: definitely-not-a-color; }
                p { color: red; }
                broken rule
                """);

        assertEquals(1, rules.size());
        assertEquals("p", rules.getFirst().selector().toString());
        assertFalse(rules.getFirst().declarations().containsKey("font-size"));
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
        assertTrue(parser.supports("display", "grid"));
        assertFalse(parser.supports("display", "masonry"));
        assertTrue(parser.supportsProperty("position"));
        assertTrue(parser.supports("position", "absolute"));
        assertTrue(parser.supports("position", "fixed"));
        assertFalse(parser.supports("position", "stickyish"));
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
        assertTrue(parser.supports("font-size", "87.5%"));
        assertFalse(parser.supports("text-align", "justify"));
    }

    @Test
    public void parsesTextDecorationLineValues() {
        CssParser parser = new CssParser();
        List<CssRule> rules = parser.parse("""
                a { text-decoration: line-through red; }
                s { text-decoration-line: overline; }
                u { text-decoration: underline; }
                """);

        var struck = rules.get(0).declarations();
        assertEquals("line-through", struck.get("text-decoration-line"));
        assertEquals("red", struck.get("text-decoration-color"));
        assertEquals("overline", rules.get(1).declarations().get("text-decoration-line"));
        assertEquals("underline", rules.get(2).declarations().get("text-decoration-line"));
        assertTrue(parser.supports("text-decoration", "line-through"));
        assertTrue(parser.supports("text-decoration-line", "overline"));
        assertFalse(parser.supports("text-decoration", "wavy"));
    }

    @Test
    public void parsesImportantDeclarationsAndPreservesTheirExpandedProperties() {
        CssRule rule = new CssParser().parse(
                "p { color:red!important; margin:1px 2px !important; color:blue }")
                .getFirst();

        assertEquals("red", rule.declarations().get("color"));
        assertEquals("2px", rule.declarations().get("margin-left"));
        assertTrue(rule.importantProperties().contains("color"));
        assertTrue(rule.importantProperties().contains("margin-left"));
    }

    @Test
    public void parsesTextTransformValues() {
        CssParser parser = new CssParser();

        assertEquals("uppercase",
                parser.parseDeclarations("text-transform:uppercase").get("text-transform"));
        assertTrue(parser.supportsProperty("text-transform"));
        assertFalse(parser.supports("text-transform", "full-width"));
    }

    @Test
    public void parsesFlexWrapping() {
        CssParser parser = new CssParser();

        assertEquals("wrap", parser.parseDeclarations("flex-wrap:wrap").get("flex-wrap"));
        assertTrue(parser.supportsProperty("flex-wrap"));
        assertFalse(parser.supports("flex-wrap", "balance"));
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
    public void acceptsSimplePercentageLengthCalculations() {
        var declarations = new CssParser().parseDeclarations(
                "height:calc(100% - 200px);width:calc(50% + 12px)");

        assertEquals("calc(100% - 200px)", declarations.get("height"));
        assertEquals("calc(50% + 12px)", declarations.get("width"));
    }


    @Test
    public void invalidSelectorListDiscardsTheWholeCssRule() {
        List<CssRule> rules = new CssParser().parse("""
                .notice, p:unknown-pseudo { color: red; }
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

        assertEquals(3, rules.size());
        assertEquals("p:focus", rules.get(0).selector().toString());
        assertEquals("div > p", rules.get(1).selector().toString());
        assertEquals("main .notice", rules.get(2).selector().toString());
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

    @Test
    public void parsesAspectRatioObjectFitAndFlexGaps() {
        CssParser parser = new CssParser();
        Map<String, String> declarations = parser.parseDeclarations(
                "aspect-ratio:16 / 9;object-fit:cover;gap:8px 1.5rem");

        assertEquals("16 / 9", declarations.get("aspect-ratio"));
        assertEquals("cover", declarations.get("object-fit"));
        assertEquals("8px", declarations.get("row-gap"));
        assertEquals("1.5rem", declarations.get("column-gap"));
        assertTrue(parser.supportsProperty("aspect-ratio"));
        assertTrue(parser.supportsProperty("object-fit"));
        assertTrue(parser.supportsProperty("gap"));
    }

    @Test
    public void extractsFontFaceSourcesWithoutTreatingThemAsStyleRules() {
        CssParser parser = new CssParser();
        List<CssFontFace> faces = parser.fontFaces("""
                @font-face {
                  font-family: 'Special Elite';
                  src: url(font.woff) format('woff'), url(font.ttf) format('truetype');
                  font-weight: bold;
                }
                """);

        assertEquals(1, faces.size());
        assertEquals("Special Elite", faces.getFirst().family());
        assertEquals(700, faces.getFirst().weight());
        assertEquals(List.of(
                new CssFontFace.Source("font.woff", "woff"),
                new CssFontFace.Source("font.ttf", "truetype")), faces.getFirst().sources());
    }

    @Test
    public void parsesRoundedBordersAndOutlineShorthand() {
        CssParser parser = new CssParser();
        Map<String, String> declarations = parser.parseDeclarations(
                "border-radius:6px;outline:black 2px solid");

        assertEquals("6px", declarations.get("border-top-left-radius"));
        assertEquals("6px", declarations.get("border-top-right-radius"));
        assertEquals("2px", declarations.get("outline-width"));
        assertEquals("solid", declarations.get("outline-style"));
        assertEquals("black", declarations.get("outline-color"));
        assertTrue(parser.supportsProperty("border-radius"));
        assertTrue(parser.supportsProperty("border-top-left-radius"));
        assertTrue(parser.supportsProperty("outline"));
    }

    @Test
    public void parsesListStyleAndTextDecoration() {
        CssParser parser = new CssParser();
        Map<String, String> declarations = parser.parseDeclarations(
                "list-style:square inside;text-decoration:underline blue");

        assertEquals("square", declarations.get("list-style-type"));
        assertEquals("underline", declarations.get("text-decoration-line"));
        assertEquals("blue", declarations.get("text-decoration-color"));
        assertTrue(parser.supportsProperty("list-style"));
        assertTrue(parser.supportsProperty("text-decoration"));
    }

    @Test
    public void parsesZIndexAndCursor() {
        CssParser parser = new CssParser();
        Map<String, String> declarations = parser.parseDeclarations(
                "z-index:-2;cursor:pointer");

        assertEquals("-2", declarations.get("z-index"));
        assertEquals("pointer", declarations.get("cursor"));
        assertTrue(parser.supportsProperty("z-index"));
        assertTrue(parser.supportsProperty("cursor"));
    }

    @Test
    public void parsesFlexDisplayAndCoreFlexProperties() {
        CssParser parser = new CssParser();
        Map<String, String> declarations = parser.parseDeclarations("""
                display:inline-flex;flex-direction:column-reverse;
                justify-content:space-between;align-items:center;flex-grow:1.5
                """);

        assertEquals("inline-flex", declarations.get("display"));
        assertEquals("column-reverse", declarations.get("flex-direction"));
        assertEquals("space-between", declarations.get("justify-content"));
        assertEquals("center", declarations.get("align-items"));
        assertEquals("1.5", declarations.get("flex-grow"));
        assertTrue(parser.supports("display", "flex"));
        assertTrue(parser.supportsProperty("flex-grow"));
        assertFalse(parser.supports("flex-grow", "-1"));
        assertTrue(parser.supports("align-items", "baseline"));
        assertEquals("\"New Item\"", parser.parseDeclarations(
                "content:\"New Item\"").get("content"));
    }

    @Test
    public void parsesRgbaOpacityFlexShorthandAndSvgFill() {
        CssParser parser = new CssParser();
        Map<String, String> declarations = parser.parseDeclarations("""
                color:rgba(60, 64, 67, .3);opacity:.75;flex:1 0 auto;fill:currentColor
                """);

        assertEquals("rgba(60, 64, 67, .3)", declarations.get("color"));
        assertEquals(".75", declarations.get("opacity"));
        assertEquals("1", declarations.get("flex-grow"));
        assertEquals("0", declarations.get("flex-shrink"));
        assertEquals("auto", declarations.get("flex-basis"));
        assertEquals("currentcolor", declarations.get("fill"));
        assertTrue(parser.supportsProperty("flex"));
        assertTrue(parser.supportsProperty("opacity"));
        assertTrue(parser.supportsProperty("fill"));
    }

    @Test
    public void acceptsHslAndHslaAcrossColorProperties() {
        CssParser parser = new CssParser();
        Map<String, String> declarations = parser.parseDeclarations("""
                color:hsl(0,0%,0%);background-color:hsla(0,100%,50%,.5);
                border-color:hsl(120,100%,50%);text-decoration-color:hsl(240,100%,50%)
                """);

        assertEquals("hsl(0,0%,0%)", declarations.get("color"));
        assertEquals("hsla(0,100%,50%,.5)", declarations.get("background-color"));
        assertEquals("hsl(120,100%,50%)", declarations.get("border-top-color"));
        assertEquals("hsl(240,100%,50%)", declarations.get("text-decoration-color"));
        assertTrue(parser.supports("color", "hsl(0,0%,0%)"));
        assertTrue(parser.supports("background-color", "hsl(0,0%,0%)"));
        assertTrue(parser.supports("border-color", "hsla(0,0%,0%,.5)"));
        assertFalse(parser.supports("color", "hsl(0,0%)"));
        assertFalse(parser.supports("color", "hsl(0,0%,101%)"));
    }

    @Test
    public void acceptsCurrentColorForEveryColorProperty() {
        CssParser parser = new CssParser();
        Map<String, String> declarations = parser.parseDeclarations("""
                color:currentColor;background-color:currentColor;border-color:currentColor;
                outline-color:currentColor;text-decoration-color:currentColor;
                border:1px solid currentColor;outline:2px solid currentColor;
                background:currentColor url('none.png')
                """);

        assertEquals("currentcolor", declarations.get("color"));
        assertEquals("currentcolor", declarations.get("background-color"));
        assertEquals("currentcolor", declarations.get("border-top-color"));
        assertEquals("currentcolor", declarations.get("outline-color"));
        assertEquals("currentcolor", declarations.get("text-decoration-color"));
        assertTrue(parser.supports("color", "currentColor"));
        assertTrue(parser.supports("background-color", "currentColor"));
        assertTrue(parser.supports("border-color", "currentColor"));
        assertTrue(parser.supports("text-decoration-color", "currentColor"));
    }

    @Test
    public void clampsOpacityOutsideTheUnitInterval() {
        CssParser parser = new CssParser();
        assertEquals("0", parser.parseDeclarations("opacity:-5").get("opacity"));
        assertEquals("1", parser.parseDeclarations("opacity:2").get("opacity"));
        assertEquals(".5", parser.parseDeclarations("opacity:.5").get("opacity"));

        assertTrue(parser.supports("opacity", "-5"));
        assertTrue(parser.supports("opacity", "2"));
        assertTrue(parser.supports("opacity", ".5"));
        assertFalse(parser.supports("opacity", "abc"));
    }

    @Test
    public void retainsCustomPropertiesAndDeferredVarValues() {
        Map<String, String> declarations = new CssParser().parseDeclarations(
                "--BrandColor: #4285F4;color:var(--BrandColor, blue)");

        assertEquals("#4285F4", declarations.get("--BrandColor"));
        assertEquals("var(--BrandColor, blue)", declarations.get("color"));
    }

    @Test
    public void retainsNamespaceStatementsAsRulesAndKeepsParsingFollowingRules() {
        CssParser parser = new CssParser();

        assertEquals(List.of("@namespace \"http://www.w3.org/1999/xhtml\";"),
                parser.ruleSources("@namespace \"http://www.w3.org/1999/xhtml\";"));
        assertEquals(List.of("@namespace svg \"http://www.w3.org/2000/svg\";",
                        "p { color: red }"),
                parser.ruleSources("""
                        @namespace svg "http://www.w3.org/2000/svg";
                        p { color: red }
                        """));

        List<CssRule> rules = parser.parse("""
                @namespace "http://www.w3.org/1999/xhtml";
                p { color: red }
                div { color: blue }
                """);
        assertEquals(2, rules.size());
        assertEquals("p", rules.get(0).selector().toString());
        assertEquals("red", rules.get(0).declarations().get("color"));
        assertEquals("div", rules.get(1).selector().toString());
    }

    @Test
    public void acceptsPrefixAndSuffixAttributeSelectors() {
        CssParser parser = new CssParser();
        List<CssRule> rules = parser.parse("""
                a[href^="https://"] { color: green }
                a[href$=".pdf"] { color: red }
                """);

        assertEquals(2, rules.size());
        assertEquals("a[href^=\"https://\"]", rules.get(0).selector().toString());
        assertEquals("a[href$=\".pdf\"]", rules.get(1).selector().toString());
        assertTrue(parser.supports("color", "hsl(120,100%,50%)"));
    }

    @Test
    public void parsesRulesInsideMediaQueriesWithTheirCondition() {
        List<CssRule> rules = new CssParser().parse("""
                .base { color:black }
                @media (min-width: 569px) and (min-height: 500px) {
                  .wide { color:green }
                }
                """);

        assertEquals(2, rules.size());
        assertTrue(rules.get(1).mediaCondition().matches(800, 600));
        assertFalse(rules.get(1).mediaCondition().matches(500, 600));
    }

    @Test
    public void expandsBorderRadiusShorthandIntoPerCornerLonghands() {
        CssParser parser = new CssParser();
        Map<String, String> single = parser.parseDeclarations("border-radius:6px");
        assertEquals("6px", single.get("border-top-left-radius"));
        assertEquals("6px", single.get("border-top-right-radius"));
        assertEquals("6px", single.get("border-bottom-right-radius"));
        assertEquals("6px", single.get("border-bottom-left-radius"));

        Map<String, String> mixed = parser.parseDeclarations(
                "border-radius:8px 50% 4px");
        assertEquals("8px", mixed.get("border-top-left-radius"));
        assertEquals("50%", mixed.get("border-top-right-radius"));
        assertEquals("4px", mixed.get("border-bottom-right-radius"));
        assertEquals("50%", mixed.get("border-bottom-left-radius"));

        Map<String, String> four = parser.parseDeclarations(
                "border-radius:1px 2px 3px 4px");
        assertEquals("1px", four.get("border-top-left-radius"));
        assertEquals("2px", four.get("border-top-right-radius"));
        assertEquals("3px", four.get("border-bottom-right-radius"));
        assertEquals("4px", four.get("border-bottom-left-radius"));
    }

    @Test
    public void parsesBorderRadiusLonghandsAndRejectsInvalidValues() {
        CssParser parser = new CssParser();
        Map<String, String> declarations = parser.parseDeclarations("""
                border-top-left-radius:50%;border-top-right-radius:12px;
                border-bottom-left-radius:0
                """);
        assertEquals("50%", declarations.get("border-top-left-radius"));
        assertEquals("12px", declarations.get("border-top-right-radius"));
        assertEquals("0", declarations.get("border-bottom-left-radius"));
        assertNull(declarations.get("border-bottom-right-radius"));
        assertTrue(parser.supportsProperty("border-top-left-radius"));
        assertTrue(parser.supports("border-radius", "50%"));
        assertFalse(parser.supports("border-radius", "solid"));
        assertTrue(parser.parseDeclarations("border-radius:5px/50%")
                .containsKey("border-top-left-radius"));
    }

    @Test
    public void expandsBorderSideShorthandsAndAcceptsVarColors() {
        CssParser parser = new CssParser();
        Map<String, String> declarations = parser.parseDeclarations(
                "border-top:2px solid #abc;border-bottom:1px solid var(--muted);"
                        + "border-left:solid red");
        assertEquals("2px", declarations.get("border-top-width"));
        assertEquals("solid", declarations.get("border-top-style"));
        assertEquals("#abc", declarations.get("border-top-color"));
        assertEquals("1px solid var(--muted)", declarations.get("border-bottom"));
        assertEquals("solid", declarations.get("border-left-style"));
        assertEquals("red", declarations.get("border-left-color"));
        assertTrue(parser.supportsProperty("border-top"));
        assertTrue(parser.supportsProperty("border-bottom"));
        assertTrue(parser.supports("border-top", "1px solid black"));
        assertFalse(parser.supports("border-top", "1px 2px"));
    }

    @Test
    public void parsesWhiteSpaceValues() {
        CssParser parser = new CssParser();
        assertEquals("nowrap", parser.parseDeclarations("white-space:nowrap")
                .get("white-space"));
        assertEquals("pre-wrap", parser.parseDeclarations("white-space:pre-wrap")
                .get("white-space"));
        assertEquals("pre-line", parser.parseDeclarations("white-space:pre-line")
                .get("white-space"));
        assertTrue(parser.parseDeclarations("white-space:break-spaces")
                .containsKey("white-space"));
        assertTrue(parser.supportsProperty("white-space"));
        assertTrue(parser.supports("white-space", "pre"));
        assertFalse(parser.supports("white-space", "wrap"));
    }

    @Test
    public void acceptsBoxShadowValuesAndRejectsInvalidOnes() {
        CssParser parser = new CssParser();
        assertEquals("0 1px 3px rgba(0,0,0,.2)",
                parser.parseDeclarations("box-shadow:0 1px 3px rgba(0,0,0,.2)")
                        .get("box-shadow"));
        assertEquals("inset 0 2px 4px black",
                parser.parseDeclarations("box-shadow:inset 0 2px 4px black")
                        .get("box-shadow"));
        assertTrue(parser.parseDeclarations("box-shadow:0 1px 2px black,0 8px 24px gray")
                .containsKey("box-shadow"));
        assertTrue(parser.supportsProperty("box-shadow"));
        assertTrue(parser.supports("box-shadow", "none"));
        assertTrue(parser.supports("box-shadow", "0 0 0 1px #ccc inset"));
        assertFalse(parser.supports("box-shadow", "1px"));
        assertFalse(parser.supports("box-shadow", "red blue"));
        assertFalse(parser.supports("box-shadow", "0 1px solid"));
    }

    @Test
    public void parsesAnimationAndTransitionShorthands() {
        CssParser parser = new CssParser();
        Map<String, String> animation = parser.parseDeclarations("""
                animation:1s linear infinite rotate-keyframes
                """);
        assertEquals("rotate-keyframes", animation.get("animation-name"));
        assertEquals("1s", animation.get("animation-duration"));
        assertEquals("linear", animation.get("animation-timing-function"));
        assertEquals("infinite", animation.get("animation-iteration-count"));
        assertEquals("0s", animation.get("animation-delay"));

        Map<String, String> animationShort = parser.parseDeclarations(
                "animation:.2s ease-out AppFrame-a11yLink-focus");
        assertEquals("AppFrame-a11yLink-focus", animationShort.get("animation-name"));
        assertEquals(".2s", animationShort.get("animation-duration"));

        Map<String, String> transition = parser.parseDeclarations("""
                transition:color 80ms cubic-bezier(.33,1,.68,1),background-color 80ms ease-out
                """);
        assertEquals("color,background-color", transition.get("transition-property"));
        assertEquals("80ms,80ms", transition.get("transition-duration"));
        assertEquals("cubic-bezier(.33,1,.68,1),ease-out",
                transition.get("transition-timing-function"));

        assertEquals("none", parser.parseDeclarations("transition:none")
                .get("transition-property"));
        assertFalse(parser.parseDeclarations("animation:1s 2s 3s").containsKey("animation-name"));
        assertTrue(parser.supportsProperty("animation"));
        assertTrue(parser.supports("animation", "2s linear spin"));
        assertTrue(parser.supports("transition", "all 100ms ease-in"));
        assertTrue(parser.supports("clip-path", "polygon(0 0, 100% 0, 50% 100%)"));
    }

    @Test
    public void parsesAnimationFillModeAndSmallProperties() {
        CssParser parser = new CssParser();
        Map<String, String> animation = parser.parseDeclarations(
                "animation:.12s cubic-bezier(0,.1,.1,1) backwards SelectMenu-modal-animation");
        assertEquals("SelectMenu-modal-animation", animation.get("animation-name"));
        assertEquals("backwards", animation.get("animation-fill-mode"));
        assertEquals("cubic-bezier(0,.1,.1,1)", animation.get("animation-timing-function"));

        Map<String, String> batch = parser.parseDeclarations("""
                text-wrap:balance;tab-size:4;direction:rtl;
                list-style-type:lower-alpha;overflow:hidden auto;
                background-clip:padding-box;object-position:left top;
                padding:unset
                """);
        assertEquals("balance", batch.get("text-wrap"));
        assertEquals("4", batch.get("tab-size"));
        assertEquals("rtl", batch.get("direction"));
        assertEquals("lower-alpha", batch.get("list-style-type"));
        assertEquals("hidden auto", batch.get("overflow"));
        assertEquals("padding-box", batch.get("background-clip"));
        assertEquals("left top", batch.get("object-position"));
        assertEquals("0", batch.get("padding"));

        assertFalse(parser.parseDeclarations("text-wrap:sideways").containsKey("text-wrap"));
        assertFalse(parser.parseDeclarations("overflow:hidden auto scroll")
                .containsKey("overflow"));
        assertTrue(parser.supportsProperty("text-wrap"));
        assertTrue(parser.supports("overflow", "hidden auto"));
        assertTrue(parser.supports("animation", ".2s linear infinite rotate"));
    }

    @Test
    public void parsesKeyframesIntoRegistry() {
        StyleSheetRegistry registry = new StyleSheetRegistry();
        CssStyleSheet sheet = registry.register(0, """
                @keyframes spin {
                    from { transform: rotate(0deg); }
                    50% { opacity: .5; }
                    to { transform: rotate(360deg); }
                }
                p { color: red; }
                """);

        assertEquals(1, sheet.keyframes().size());
        CssKeyframes keyframes = sheet.keyframes().getFirst();
        assertEquals("spin", keyframes.name());
        assertEquals(3, keyframes.blocks().size());
        assertEquals("from", keyframes.blocks().getFirst().selector());
        assertEquals("rotate(0deg)", keyframes.blocks().getFirst()
                .declarations().get("transform"));
        // Die normalen Regeln bleiben erhalten.
        assertEquals(1, sheet.parsedRules().size());
        assertEquals("p", sheet.parsedRules().getFirst().selector().toString());
    }

    @Test
    public void parsesLayerAndContainerBlocks() {
        List<CssRule> rules = new CssParser().parse("""
                @layer primer {
                    .box { color: red; }
                }
                @container (min-width: 400px) {
                    .card { display: block; }
                }
                """);
        assertEquals(2, rules.size());
        assertEquals(".box", rules.get(0).selector().toString());
        assertEquals(".card", rules.get(1).selector().toString());
    }

    @Test
    public void parsesLinearGradientBackgroundsAndDataUris() {
        CssParser parser = new CssParser();
        Map<String, String> shorthand = parser.parseDeclarations("""
                background:linear-gradient(#ffffff26,#fff0)
                """);
        assertEquals("linear-gradient(#ffffff26,#fff0)", shorthand.get("background-image"));

        Map<String, String> withColor = parser.parseDeclarations(
                "background:linear-gradient(#34b75926,#2ea44f00),#2ea44f");
        assertEquals("linear-gradient(#34b75926,#2ea44f00)", withColor.get("background-image"));
        assertEquals("#2ea44f", withColor.get("background-color"));

        Map<String, String> image = parser.parseDeclarations(
                "background-image:linear-gradient(45deg, red 0%, blue 100%)");
        assertEquals("linear-gradient(45deg, red 0%, blue 100%)",
                image.get("background-image"));

        Map<String, String> dataUri = parser.parseDeclarations(
                "background-image:url(\"data:image/svg+xml,%3csvg%3e\")");
        assertTrue(dataUri.containsKey("background-image"));

        Map<String, String> svgData = parser.parseDeclarations(
                "background-image:url(\"data:image/svg+xml;charset=utf-8,"
                        + "%3Csvg xmlns='http://www.w3.org/2000/svg' width='16'%3E%3C/svg%3E\")");
        assertTrue("Data-URI mit Semikolon und Spaces muss akzeptiert werden",
                svgData.containsKey("background-image"));

        Map<String, String> linkText = parser.parseDeclarations("background:linktext");
        assertEquals("#0000ee", linkText.get("background-color"));
        assertTrue(parser.parseDeclarations("background:inherit")
                .containsKey("background-color"));

        Map<String, String> cover = parser.parseDeclarations(
                "background:url(/images/promo.png) bottom/cover no-repeat");
        assertEquals("url(/images/promo.png)", cover.get("background-image"));
        assertEquals("cover", cover.get("background-size-x"));
        assertEquals("no-repeat", cover.get("background-repeat"));

        assertFalse(parser.parseDeclarations(
                "background-image:linear-gradient()").containsKey("background-image"));
        assertFalse(parser.parseDeclarations(
                "background-image:linear-gradient(red, notacolor)")
                .containsKey("background-image"));
        assertTrue(parser.supports("background-image", "linear-gradient(#fff, #000)"));
    }

    @Test
    public void parsesWordBreakAppearanceFontSizeInheritAndRadiusInherit() {
        CssParser parser = new CssParser();
        Map<String, String> batch = parser.parseDeclarations("""
                word-break:break-all;appearance:none;font-size:inherit;
                border-radius:inherit
                """);
        assertEquals("break-all", batch.get("word-break"));
        assertEquals("none", batch.get("appearance"));
        assertEquals("inherit", batch.get("font-size"));
        assertEquals("inherit", batch.get("border-radius"));

        assertFalse(parser.parseDeclarations("word-break:break-everything")
                .containsKey("word-break"));
        assertTrue(parser.supports("word-break", "break-word"));
        assertTrue(parser.supports("border-radius", "inherit"));
        assertTrue(parser.supports("appearance", "none"));
    }

    @Test
    public void parsesUnsetFitContentInitialStrokeWidthAndScrollbarWidth() {
        CssParser parser = new CssParser();
        Map<String, String> batch = parser.parseDeclarations("""
                width:unset;height:fit-content;background-color:initial;
                stroke-width:2px;scrollbar-width:thin
                """);
        assertEquals("unset", batch.get("width"));
        assertEquals("fit-content", batch.get("height"));
        assertEquals("transparent", batch.get("background-color"));
        assertEquals("2px", batch.get("stroke-width"));
        assertEquals("thin", batch.get("scrollbar-width"));

        assertFalse(parser.parseDeclarations("scrollbar-width:wide").containsKey("scrollbar-width"));
        assertTrue(parser.supports("width", "unset"));
        assertTrue(parser.supports("height", "fit-content"));
        assertTrue(parser.supports("background-color", "initial"));
    }

    @Test
    public void parsesUserSelectFlexFlowStrokeAndMathFunctions() {
        CssParser parser = new CssParser();
        Map<String, String> batch = parser.parseDeclarations("""
                user-select:none;flex-flow:column wrap;stroke:#ee8;
                width:min(256px,100vw - 2rem)
                """);
        assertEquals("none", batch.get("user-select"));
        assertEquals("column", batch.get("flex-direction"));
        assertEquals("wrap", batch.get("flex-wrap"));
        assertEquals("#ee8", batch.get("stroke"));
        assertEquals("min(256px,100vw - 2rem)", batch.get("width"));

        assertTrue(parser.parseDeclarations("flex-flow:row").containsKey("flex-direction"));
        assertFalse(parser.parseDeclarations("flex-flow:bogus").containsKey("flex-direction"));
        assertFalse(parser.parseDeclarations("user-select:nonsense").containsKey("user-select"));
        assertFalse(parser.parseDeclarations("width:min(256px, 2rem, 1em")
                .containsKey("width"));
        assertTrue(parser.supportsProperty("user-select"));
        assertTrue(parser.supports("width", "max(100px, 50vw)"));
        assertTrue(parser.supports("flex-flow", "column"));
    }

    @Test
    public void parsesLetterSpacingTextOverflowOverflowWrapAndMaxContent() {
        CssParser parser = new CssParser();
        Map<String, String> text = parser.parseDeclarations("""
                letter-spacing:-.5px;text-overflow:ellipsis;overflow-wrap:break-word;
                width:max-content
                """);
        assertEquals("-.5px", text.get("letter-spacing"));
        assertEquals("ellipsis", text.get("text-overflow"));
        assertEquals("break-word", text.get("overflow-wrap"));
        assertEquals("max-content", text.get("width"));

        assertEquals("normal", parser.parseDeclarations("letter-spacing:normal")
                .get("letter-spacing"));
        assertFalse(parser.parseDeclarations("letter-spacing:2px solid")
                .containsKey("letter-spacing"));
        assertFalse(parser.parseDeclarations("text-overflow:fade")
                .containsKey("text-overflow"));
        assertFalse(parser.parseDeclarations("width:bogus").containsKey("width"));
        assertTrue(parser.supportsProperty("letter-spacing"));
        assertTrue(parser.supportsProperty("text-overflow"));
        assertTrue(parser.supports("overflow-wrap", "break-word"));
        assertTrue(parser.supports("width", "max-content"));
    }

    @Test
    public void parsesAlignSelfAlignContentAndOrder() {
        CssParser parser = new CssParser();
        Map<String, String> alignment = parser.parseDeclarations("""
                align-self:flex-start;align-content:space-between;order:-1
                """);
        assertEquals("flex-start", alignment.get("align-self"));
        assertEquals("space-between", alignment.get("align-content"));
        assertEquals("-1", alignment.get("order"));

        assertEquals("0", parser.parseDeclarations("order:inherit").get("order"));
        assertFalse(parser.parseDeclarations("align-self:bogus").containsKey("align-self"));
        assertFalse(parser.parseDeclarations("order:1.5").containsKey("order"));
        assertTrue(parser.supportsProperty("align-self"));
        assertTrue(parser.supportsProperty("align-content"));
        assertTrue(parser.supports("order", "0"));
        assertTrue(parser.supports("align-self", "center"));
    }

    @Test
    public void parsesTransformAndTransformOriginValues() {
        CssParser parser = new CssParser();
        Map<String, String> transforms = parser.parseDeclarations("""
                transform:translate(-50%) translateY(-50%);
                transform-origin:100% 100%
                """);
        assertEquals("translate(-50%) translatey(-50%)", transforms.get("transform"));
        assertEquals("100% 100%", transforms.get("transform-origin"));

        assertEquals("none", parser.parseDeclarations("transform:none").get("transform"));
        assertTrue(parser.parseDeclarations("transform-origin:top")
                .containsKey("transform-origin"));
        assertTrue(parser.parseDeclarations("transform:rotate(180deg) scale(2)")
                .containsKey("transform"));
        assertFalse(parser.parseDeclarations("transform:skew(10deg)").containsKey("transform"));
        assertFalse(parser.parseDeclarations("transform:translate(50%,)")
                .containsKey("transform"));
        assertFalse(parser.parseDeclarations("transform-origin:3")
                .containsKey("transform-origin"));
        assertTrue(parser.supportsProperty("transform"));
        assertTrue(parser.supports("transform", "translate(10px) rotate(45deg)"));
        assertFalse(parser.supports("transform", "skew(10deg)"));
    }

    @Test
    public void acceptsWebkitAndMozPrefixedAliases() {
        CssParser parser = new CssParser();
        Map<String, String> decorations = parser.parseDeclarations("""
                -webkit-text-decoration:inherit;-webkit-text-decoration-color:red;
                -webkit-text-fill-color:blue;-webkit-tap-highlight-color:rgba(0,0,0,0)
                """);
        assertEquals("inherit", decorations.get("text-decoration-line"));
        assertEquals("red", decorations.get("text-decoration-color"));
        assertEquals("blue", decorations.get("-webkit-text-fill-color"));

        Map<String, String> behavior = parser.parseDeclarations("""
                -webkit-user-select:none;-webkit-font-smoothing:antialiased;
                -webkit-line-clamp:2;-webkit-box-orient:vertical;
                -webkit-appearance:none;-moz-osx-font-smoothing:grayscale;
                -webkit-backdrop-filter:none
                """);
        assertEquals("none", behavior.get("-webkit-user-select"));
        assertEquals("2", behavior.get("-webkit-line-clamp"));
        assertTrue(parser.supportsProperty("-webkit-user-select"));
        assertTrue(parser.supports("-webkit-text-decoration", "inherit"));
        assertFalse(parser.supports("-webkit-bogus-property", "x"));
    }

    @Test
    public void mapsLogicalPropertiesToPhysicalSides() {
        CssParser parser = new CssParser();
        Map<String, String> padding = parser.parseDeclarations(
                "padding-inline:8px;padding-block:4px 2px");
        assertEquals("8px", padding.get("padding-left"));
        assertEquals("8px", padding.get("padding-right"));
        assertEquals("4px", padding.get("padding-top"));
        assertEquals("2px", padding.get("padding-bottom"));

        Map<String, String> paddingSides = parser.parseDeclarations(
                "padding-inline-start:5px;padding-inline-end:7px;"
                        + "padding-block-start:1px;padding-block-end:3px");
        assertEquals("5px", paddingSides.get("padding-left"));
        assertEquals("7px", paddingSides.get("padding-right"));
        assertEquals("1px", paddingSides.get("padding-top"));
        assertEquals("3px", paddingSides.get("padding-bottom"));

        Map<String, String> margins = parser.parseDeclarations(
                "margin-inline:auto 10px;margin-block:5px");
        assertEquals("auto", margins.get("margin-left"));
        assertEquals("10px", margins.get("margin-right"));
        assertEquals("5px", margins.get("margin-top"));
        assertEquals("5px", margins.get("margin-bottom"));

        Map<String, String> inset = parser.parseDeclarations("inset:1px 2px 3px 4px");
        assertEquals("1px", inset.get("top"));
        assertEquals("2px", inset.get("right"));
        assertEquals("3px", inset.get("bottom"));
        assertEquals("4px", inset.get("left"));

        Map<String, String> insetInline = parser.parseDeclarations("inset-inline:6px");
        assertEquals("6px", insetInline.get("right"));
        assertEquals("6px", insetInline.get("left"));

        Map<String, String> insetStart = parser.parseDeclarations(
                "inset-block-start:9px");
        assertEquals("9px", insetStart.get("top"));

        Map<String, String> sizes = parser.parseDeclarations(
                "inline-size:50%;block-size:12em;min-inline-size:10px;max-block-size:200px");
        assertEquals("50%", sizes.get("width"));
        assertEquals("12em", sizes.get("height"));
        assertEquals("10px", sizes.get("min-width"));
        assertEquals("200px", sizes.get("max-height"));

        Map<String, String> borders = parser.parseDeclarations(
                "border-inline:1px solid red;border-block-start:2px solid blue");
        assertEquals("1px", borders.get("border-left-width"));
        assertEquals("solid", borders.get("border-right-style"));
        assertEquals("red", borders.get("border-left-color"));
        assertEquals("2px", borders.get("border-top-width"));
        assertEquals("blue", borders.get("border-top-color"));

        assertTrue(parser.supportsProperty("padding-inline"));
        assertTrue(parser.supportsProperty("inset"));
        assertFalse(parser.supports("padding-inline", "bogus"));
    }

    @Test
    public void parsesVisibilityPointerEventsAndOutlineOffset() {
        CssParser parser = new CssParser();
        assertEquals("hidden", parser.parseDeclarations("visibility:hidden")
                .get("visibility"));
        assertEquals("collapse", parser.parseDeclarations("visibility:collapse")
                .get("visibility"));
        assertEquals("none", parser.parseDeclarations("pointer-events:none")
                .get("pointer-events"));
        assertEquals("3px", parser.parseDeclarations("outline-offset:3px")
                .get("outline-offset"));
        assertEquals("-2px", parser.parseDeclarations("outline-offset:-2px")
                .get("outline-offset"));
        assertTrue(parser.supports("visibility", "hidden"));
        assertTrue(parser.supports("pointer-events", "none"));
        assertTrue(parser.supportsProperty("outline-offset"));
        assertFalse(parser.supports("visibility", "invisible"));
    }

    @Test
    public void parsesPercentageMarginsStickyFixedOverflowLonghandsAndCursors() {
        CssParser parser = new CssParser();
        assertEquals("8.33333%", parser.parseDeclarations("margin-left:8.33333%")
                .get("margin-left"));
        assertEquals("25%", parser.parseDeclarations("margin:25% 0")
                .get("margin-top"));
        assertEquals("sticky", parser.parseDeclarations("position:sticky")
                .get("position"));
        assertEquals("fixed", parser.parseDeclarations("position:fixed")
                .get("position"));
        assertEquals("hidden", parser.parseDeclarations("overflow-y:hidden")
                .get("overflow-y"));
        assertEquals("clip", parser.parseDeclarations("overflow-x:clip")
                .get("overflow-x"));
        assertEquals("grab", parser.parseDeclarations("cursor:grab").get("cursor"));
        assertTrue(parser.supports("position", "sticky"));
        assertTrue(parser.supports("overflow-y", "auto"));
        assertFalse(parser.supports("position", "stickyish"));
    }

    @Test
    public void parsesRulesInsideTrueSupportsConditionsAndSkipsFalseOnes() {
        List<CssRule> rules = new CssParser().parse("""
                .base { color: black }
                @supports (display: flex) {
                  .flexed { color: green }
                }
                @supports (display: masonry) {
                  .gridded { color: red }
                }
                """);

        assertEquals(2, rules.size());
        assertEquals(".base", rules.get(0).selector().toString());
        assertEquals(".flexed", rules.get(1).selector().toString());
    }

    @Test
    public void evaluatesSupportsNotAndOrConditions() {
        List<CssRule> rules = new CssParser().parse("""
                @supports not (display: masonry) {
                  .noGrid { color: blue }
                }
                @supports (color: red) or (display: masonry) {
                  .orMatch { color: green }
                }
                @supports (display: masonry) and (color: red) {
                  .andMiss { color: red }
                }
                @supports (color: red) and (display: flex) {
                  .andHit { color: green }
                }
                """);

        assertEquals(List.of(".noGrid", ".orMatch", ".andHit"),
                rules.stream().map(rule -> rule.selector().toString()).toList());
    }

    @Test
    public void evaluatesSupportsSelectorConditionsAndNestedMedia() {
        List<CssRule> rules = new CssParser().parse("""
                @supports selector(:has(.a)) {
                  .hasStyle { color: green }
                }
                @supports selector(:nope) {
                  .bad { color: red }
                }
                @supports (display: flex) {
                  @media (min-width: 500px) {
                    .nested { color: green }
                  }
                }
                """);

        assertEquals(List.of(".hasStyle", ".nested"),
                rules.stream().map(rule -> rule.selector().toString()).toList());
        assertTrue(rules.getLast().mediaCondition().matches(800, 600));
        assertFalse(rules.getLast().mediaCondition().matches(400, 600));
    }

    @Test
    public void expandsBackgroundShorthandWithEdgeOffsetAndSize() {
        CssParser parser = new CssParser();
        Map<String, String> declarations = parser.parseDeclarations(
                "background:repeat-y top 64px center/100% dimgray url('/blur.jpg')");

        assertEquals("dimgray", declarations.get("background-color"));
        assertEquals("url('/blur.jpg')", declarations.get("background-image"));
        assertEquals("repeat-y", declarations.get("background-repeat"));
        assertEquals("center", declarations.get("background-position-x"));
        assertEquals("top", declarations.get("background-position-y"));
        assertEquals("64px", declarations.get("background-position-y-offset"));
        assertEquals("100%", declarations.get("background-size-x"));
        assertEquals("auto", declarations.get("background-size-y"));
        assertTrue(parser.supportsProperty("background-size"));
        assertEquals("50%", parser.parseDeclarations(
                "background-position:0 50%").get("background-position-y-offset"));
        assertEquals("right", parser.parseDeclarations(
                "background-position:center right").get("background-position-x"));
    }

    @Test
    public void parsesGridDisplayTemplatesAndPlacement() {
        CssParser parser = new CssParser();
        Map<String, String> declarations = parser.parseDeclarations("""
                display:grid;grid-template-columns:repeat(2,minmax(0,1fr));
                grid-template-rows:auto min-content;
                grid-template-areas:"content pane" "footer footer";
                grid-auto-flow:row dense;
                grid-area:content;
                grid-column:1 / span 2;grid-row:2 / 4;
                grid-gap:8px 16px
                """);

        assertEquals("grid", declarations.get("display"));
        assertEquals("repeat(2,minmax(0,1fr))", declarations.get("grid-template-columns"));
        assertEquals("auto min-content", declarations.get("grid-template-rows"));
        assertEquals("\"content pane\" \"footer footer\"", declarations.get("grid-template-areas"));
        assertEquals("row dense", declarations.get("grid-auto-flow"));
        assertEquals("content", declarations.get("grid-area"));
        assertEquals("1", declarations.get("grid-column-start"));
        assertEquals("span 2", declarations.get("grid-column-end"));
        assertEquals("2", declarations.get("grid-row-start"));
        assertEquals("4", declarations.get("grid-row-end"));
        assertEquals("8px", declarations.get("row-gap"));
        assertEquals("16px", declarations.get("column-gap"));

        assertTrue(parser.supports("display", "grid"));
        assertTrue(parser.supportsProperty("grid-template-columns"));
        assertTrue(parser.supports("grid-template-areas", "\"a\" \"b\""));
        assertTrue(parser.supportsProperty("grid-area"));
        assertFalse(parser.parseDeclarations("grid-template-columns:bogus 1fr")
                .containsKey("grid-template-columns"));
        assertFalse(parser.parseDeclarations("grid-row:1/2/3").containsKey("grid-row-start"));
        assertFalse(parser.parseDeclarations("grid-area:content 1").containsKey("grid-area"));

        Map<String, String> shorthand = parser.parseDeclarations(
                "grid-template:\"header header\" 56px / 1fr 2fr");
        assertEquals("\"header header\"", shorthand.get("grid-template-areas"));
        assertEquals("56px", shorthand.get("grid-template-rows"));
        assertEquals("1fr 2fr", shorthand.get("grid-template-columns"));
    }

    @Test
    public void acceptsStartEndValuesInheritShadowAndViewportUnits() {
        CssParser parser = new CssParser();
        Map<String, String> declarations = parser.parseDeclarations("""
                place-content:center;justify-content:start;align-items:end;
                line-height:inherit;padding:inherit;
                text-shadow:0 -1px #0000004d;shape-rendering:crispedges;
                flex:0 auto;max-height:100dvh
                """);

        assertEquals("start", declarations.get("justify-content"));
        assertEquals("end", declarations.get("align-items"));
        assertEquals("center", declarations.get("align-content"));
        assertEquals("start", declarations.get("justify-content"));
        assertEquals("inherit", declarations.get("line-height"));
        assertEquals("inherit", declarations.get("padding"));
        assertEquals("0 -1px #0000004d", declarations.get("text-shadow"));
        assertEquals("crispedges", declarations.get("shape-rendering"));
        assertEquals("0", declarations.get("flex-grow"));
        assertEquals("1", declarations.get("flex-shrink"));
        assertEquals("auto", declarations.get("flex-basis"));
        assertEquals("100dvh", declarations.get("max-height"));

        assertEquals("100%", parser.parseDeclarations("flex:100%").get("flex-basis"));
        assertEquals("none", parser.parseDeclarations("text-shadow:none").get("text-shadow"));
        assertEquals("linear-gradient(#fff0 -8.14%,#ffffff1a 62.09%)",
                parser.parseDeclarations("background-image:linear-gradient(#fff0 -8.14%,#ffffff1a 62.09%)")
                        .get("background-image"));
        assertEquals("radial-gradient(141.53% 114.68% at 87.46% 55.27%,#9a7cff 36.75%,#0e0aa200 100%)",
                parser.parseDeclarations(
                        "background:radial-gradient(141.53% 114.68% at 87.46% 55.27%,#9a7cff 36.75%,#0e0aa200 100%)")
                        .get("background-image"));

        assertTrue(parser.supportsProperty("text-shadow"));
        assertTrue(parser.supports("max-height", "100dvh"));
        assertFalse(parser.parseDeclarations("flex:1 2 3 4").containsKey("flex-grow"));
        assertFalse(parser.parseDeclarations("shape-rendering:bogus")
                .containsKey("shape-rendering"));
    }

    @Test
    public void acceptsFiltersCalcCustomMediaAndSmallProperties() {
        CssParser parser = new CssParser();
        Map<String, String> declarations = parser.parseDeclarations("""
                filter:brightness(.85);vertical-align:-.075em;color-scheme:light dark;
                isolation:isolate;mix-blend-mode:plus-lighter;resize:vertical;
                scroll-margin-top:64px;table-layout:fixed;cursor:grabbing;
                display:inherit;max-height:calc(100vh - 2rem);
                text-overflow:inherit
                """);

        assertEquals("brightness(.85)", declarations.get("filter"));
        assertEquals("-.075em", declarations.get("vertical-align"));
        assertEquals("light dark", declarations.get("color-scheme"));
        assertEquals("isolate", declarations.get("isolation"));
        assertEquals("plus-lighter", declarations.get("mix-blend-mode"));
        assertEquals("vertical", declarations.get("resize"));
        assertEquals("64px", declarations.get("scroll-margin-top"));
        assertEquals("fixed", declarations.get("table-layout"));
        assertEquals("grabbing", declarations.get("cursor"));
        assertEquals("inherit", declarations.get("display"));
        assertEquals("calc(100vh - 2rem)", declarations.get("max-height"));
        assertEquals("inherit", declarations.get("text-overflow"));

        assertTrue(parser.supportsProperty("filter"));
        assertTrue(parser.supports("filter", "grayscale()"));
        assertTrue(parser.supports("display", "-webkit-box"));
        assertFalse(parser.parseDeclarations("filter:drop-shadow(0 0 2px red)")
                .containsKey("filter"));
        assertFalse(parser.parseDeclarations("cursor:bogus").containsKey("cursor"));

        List<CssRule> rules = parser.parse("""
                @custom-media --narrow (max-width: 600px);
                .kept { color: green }
                """);
        assertEquals(1, rules.size());
        assertEquals(".kept", rules.get(0).selector().toString());
    }

    @Test
    public void acceptsSvgFiltersSystemColorsAndMaskValues() {
        CssParser parser = new CssParser();
        Map<String, String> declarations = parser.parseDeclarations("""
                backdrop-filter:blur(5px);fill-opacity:1;stop-color:#fff;stop-opacity:.5;
                mask-image:url("data:image/svg+xml;base64,AAAA");
                text-indent:-3000px;contain:content;font-stretch:100%;
                touch-action:manipulation;background-color:canvastext;
                background-repeat:repeat-x,repeat-x;letter-spacing:inherit;
                grid-area:1/1/auto/-1;top:calc(50% - 12px)
                """);

        assertEquals("blur(5px)", declarations.get("backdrop-filter"));
        assertEquals("1", declarations.get("fill-opacity"));
        assertEquals("#fff", declarations.get("stop-color"));
        assertEquals(".5", declarations.get("stop-opacity"));
        assertEquals("-3000px", declarations.get("text-indent"));
        assertEquals("content", declarations.get("contain"));
        assertEquals("100%", declarations.get("font-stretch"));
        assertEquals("manipulation", declarations.get("touch-action"));
        assertEquals("canvastext", declarations.get("background-color"));
        assertEquals("repeat-x", declarations.get("background-repeat"));
        assertEquals("inherit", declarations.get("letter-spacing"));
        assertEquals("1", declarations.get("grid-row-start"));
        assertEquals("auto", declarations.get("grid-row-end"));
        assertEquals("-1", declarations.get("grid-column-end"));
        assertEquals("calc(50% - 12px)", declarations.get("top"));

        assertEquals("radial-gradient(circle at 0 100%,#e6b7fe 10%,#5049c2 20%,#574eff00 60%)",
                parser.parseDeclarations(
                        "background:radial-gradient(circle at 0 100%,#e6b7fe 10%,#5049c2 20%,#574eff00 60%)")
                        .get("background-image"));

        assertTrue(parser.supportsProperty("backdrop-filter"));
        assertTrue(parser.supports("grid-area", "1/2"));
        assertFalse(parser.parseDeclarations("mask-image:garbage").containsKey("mask-image"));
    }

    @Test
    public void acceptsChLhUnitsPlaceSelfClipAndInheritLonghands() {
        CssParser parser = new CssParser();
        Map<String, String> declarations = parser.parseDeclarations("""
                padding:30% 0;min-width:1ch;max-width:65ch;line-height:2lh;
                clip:rect(1px,1px,1px,1px);mask-size:75%;place-self:center end;
                align-self:start;border-color:canvastext;box-sizing:initial;
                justify-content:stretch;margin-bottom:inherit;padding-left:inherit;
                font-weight:inherit;grid-template-columns:0 0 0 1fr;
                flex:0 1 max-content
                """);

        assertEquals("30%", declarations.get("padding-top"));
        assertEquals("0", declarations.get("padding-right"));
        assertEquals("inherit", declarations.get("padding-left"));
        assertEquals("1ch", declarations.get("min-width"));
        assertEquals("65ch", declarations.get("max-width"));
        assertEquals("2lh", declarations.get("line-height"));
        assertEquals("rect(1px,1px,1px,1px)", declarations.get("clip"));
        assertEquals("75%", declarations.get("mask-size"));
        assertEquals("start", declarations.get("align-self"));
        assertEquals("end", declarations.get("justify-self"));
        assertEquals("start", parser.parseDeclarations(
                "align-self:start").get("align-self"));
        assertEquals("canvastext", declarations.get("border-color"));
        assertEquals("initial", declarations.get("box-sizing"));
        assertEquals("stretch", declarations.get("justify-content"));
        assertEquals("inherit", declarations.get("margin-bottom"));
        assertEquals("inherit", declarations.get("padding-left"));
        assertEquals("inherit", declarations.get("font-weight"));
        assertEquals("0 0 0 1fr", declarations.get("grid-template-columns"));
        assertEquals("max-content", declarations.get("flex-basis"));

        assertTrue(parser.supportsProperty("clip"));
        assertTrue(parser.supports("place-self", "start"));
        assertTrue(parser.supports("grid-template-columns", "repeat(auto-fit,160px)"));
        assertFalse(parser.parseDeclarations("clip:rect()").containsKey("clip"));
        assertFalse(parser.parseDeclarations("mask-size:garbage").containsKey("mask-size"));
    }

    @Test
    public void acceptsPlayStateContentsMultiStopGradientsAndRepeatWithSpaces() {
        CssParser parser = new CssParser();
        Map<String, String> declarations = parser.parseDeclarations("""
                animation-play-state:paused;justify-items:center;mask-position:50%;
                mask-repeat:no-repeat;stroke-dasharray:3 3;text-rendering:optimizelegibility;
                text-underline-offset:2px;outline:2px solid highlighttext;
                text-decoration:inherit;white-space:unset;aspect-ratio:unset;
                display:contents;max-width:inherit;
                grid-template-columns:repeat(auto-fit,160px 160px)
                """);

        assertEquals("paused", declarations.get("animation-play-state"));
        assertEquals("center", declarations.get("justify-items"));
        assertEquals("50%", declarations.get("mask-position"));
        assertEquals("no-repeat", declarations.get("mask-repeat"));
        assertEquals("3 3", declarations.get("stroke-dasharray"));
        assertEquals("optimizelegibility", declarations.get("text-rendering"));
        assertEquals("2px", declarations.get("text-underline-offset"));
        assertEquals("highlighttext", declarations.get("outline-color"));
        assertEquals("inherit", declarations.get("text-decoration-line"));
        assertEquals("unset", declarations.get("white-space"));
        assertEquals("unset", declarations.get("aspect-ratio"));
        assertEquals("contents", declarations.get("display"));
        assertEquals("inherit", declarations.get("max-width"));
        assertEquals("repeat(auto-fit,160px 160px)", declarations.get("grid-template-columns"));

        assertEquals("radial-gradient(#ffc58b 10%,#e1a6ff 20% 30%,#352cee 60%)",
                parser.parseDeclarations(
                        "background:radial-gradient(#ffc58b 10%,#e1a6ff 20% 30%,#352cee 60%)")
                        .get("background-image"));
        assertEquals("linear-gradient(90deg,#e9edec 160px,#0000 200px)",
                parser.parseDeclarations(
                        "background:linear-gradient(90deg,#e9edec 160px,#0000 200px)")
                        .get("background-image"));

        assertTrue(parser.supportsProperty("animation-play-state"));
        assertTrue(parser.supports("display", "contents"));
        assertFalse(parser.parseDeclarations("stroke-dasharray:abc").containsKey("stroke-dasharray"));
    }
}

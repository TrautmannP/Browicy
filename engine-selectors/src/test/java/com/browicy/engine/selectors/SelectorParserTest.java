package com.browicy.engine.selectors;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SelectorParserTest {

    @Test
    public void parsesUnquotedIdentifierAttributeValues() {
        Selector selector = new SelectorParser().parse("link[blocking=render]")
                .selectors().getFirst();

        assertEquals("link[blocking=\"render\"]", selector.toString());
    }

    @Test
    public void parsesRootAndSiblingStructuralPseudoClasses() {
        assertEquals(":root", new SelectorParser().parse(":root")
                .selectors().getFirst().toString());
        assertEquals("div:only-child", new SelectorParser().parse("div:only-child")
                .selectors().getFirst().toString());
        assertEquals("a:first-of-type", new SelectorParser().parse("a:first-of-type")
                .selectors().getFirst().toString());
    }

    @Test
    public void parsesSubstringAttributeSelectors() {
        assertEquals("[jsaction*=\"trigger.\"]", new SelectorParser()
                .parse("[jsaction*=\"trigger.\"]").selectors().getFirst().toString());
    }

    @Test
    public void parsesBackslashEscapesInUnquotedAttributeValues() {
        assertEquals("[data-target=\"qbsearch-input.inputButtonText\"]",
                parser.parse("[data-target=qbsearch-input\\.inputButtonText]")
                        .selectors().getFirst().toString());
        assertEquals("[type=\"A s\"]", parser.parse("[type=A\\ s]")
                .selectors().getFirst().toString());
        assertEquals("[class=\"octicon octicon-x\"]", parser.parse(
                "[class=octicon\\ octicon-x]").selectors().getFirst().toString());
        assertEquals("[href^=\"a.pdf\"]", parser.parse("[href^=a\\.pdf]")
                .selectors().getFirst().toString());

        TestNode link = new TestNode("a", null, Set.of(), null,
                Map.of("data-target", "qbsearch-input.inputButtonText",
                        "type", "A s", "class", "octicon octicon-x"));
        TestAdapter adapter = new TestAdapter(link);
        assertTrue(parser.parse("[data-target=qbsearch-input\\.inputButtonText]")
                .matchesAny(link, adapter));
        assertTrue(parser.parse("[type=A\\ s]").matchesAny(link, adapter));
        assertTrue(parser.parse("[class=octicon\\ octicon-x]").matchesAny(link, adapter));
        assertFalse(parser.parse("[data-target=other\\.value]").matchesAny(link, adapter));
    }

    @Test
    public void parsesAndMatchesPrefixAndSuffixAttributeSelectors() {
        TestNode link = new TestNode("a", null, Set.of(), null,
                Map.of("href", "https://example.com/page.pdf", "lang", "de-DE"));
        TestNode other = new TestNode("a", null, Set.of(), null,
                Map.of("href", "mailto:user@example.com"));
        TestAdapter adapter = new TestAdapter(link, other);

        assertEquals("[href^=\"https\"]", parser.parse("[href^='https']")
                .selectors().getFirst().toString());
        assertEquals("a[href$=\".pdf\"]", parser.parse("a[href$=\".pdf\"]")
                .selectors().getFirst().toString());
        assertTrue(parser.parse("[href^=\"https://\"]").matchesAny(link, adapter));
        assertTrue(parser.parse("a[href$=\".pdf\"]").matchesAny(link, adapter));
        assertTrue(parser.parse("[lang^=\"de\"]").matchesAny(link, adapter));
        assertTrue(parser.parse("[lang$=\"DE\"]").matchesAny(link, adapter));
        assertFalse(parser.parse("[href^=\"mailto\"]").matchesAny(link, adapter));
        assertFalse(parser.parse("[href$=\".pdf\"]").matchesAny(other, adapter));
        assertFalse(parser.parse("[href^=\"\"]").matchesAny(link, adapter));
        assertEquals(new Specificity(0, 1, 1), parser.parse("a[href$=\".pdf\"]")
                .selectors().getFirst().specificity());
    }

    private final SelectorParser parser = new SelectorParser();

    @Test
    public void parsesListsCompoundSelectorsAndCombinators() {
        SelectorList list = parser.parse("main > section.card p.note.highlight#message, *");

        assertEquals(2, list.selectors().size());
        ComplexSelector selector = list.selectors().getFirst();
        assertEquals(3, selector.steps().size());
        assertEquals("main", selector.steps().get(0).selector().typeName());
        assertEquals(Combinator.CHILD, selector.steps().get(1).relationToPrevious());
        assertEquals(List.of("card"), selector.steps().get(1).selector().classes());
        assertEquals(Combinator.DESCENDANT, selector.steps().get(2).relationToPrevious());
        assertEquals("message", selector.steps().get(2).selector().id());
        assertEquals("main > section.card p.note.highlight#message, *", list.toString());
    }

    @Test
    public void calculatesSpecificityAcrossTheWholeSelector() {
        ComplexSelector selector = parser.parse("main > section.card p.note#message")
                .selectors().getFirst();

        assertEquals(new Specificity(1, 2, 3), selector.specificity());
        assertEquals(Specificity.ZERO, parser.parse("*").selectors().getFirst().specificity());
        assertTrue(new Specificity(1, 0, 0)
                .compareTo(new Specificity(0, 100, 100)) > 0);
    }

    @Test
    public void matcherUsesOnlyTheProvidedNodeAdapter() {
        TestNode main = new TestNode("main", null, Set.of(), null);
        TestNode section = new TestNode("section", null, Set.of("card"), main);
        TestNode wrapper = new TestNode("div", null, Set.of(), section);
        TestNode target = new TestNode("p", "message", Set.of("note", "highlight"), wrapper);
        TestAdapter adapter = new TestAdapter(main, section, wrapper, target);

        Selector selector = parser.parse("main > section.card p.note.highlight#message")
                .selectors().getFirst();

        assertTrue(selector.matches(target, adapter));
        assertFalse(selector.matches(wrapper, adapter));
        assertSame(section, adapter.parentElement(wrapper));
    }

    @Test
    public void parsesAndMatchesAttributeSelectors() {
        TestNode input = new TestNode("input", null, Set.of(), null,
                Map.of("type", "text", "data-tags", "primary wide"));
        TestAdapter adapter = new TestAdapter(input);

        ComplexSelector selector = parser.parse(
                "input[type=\"text\"][data-tags~='primary'][disabled], [data-tags]")
                .selectors().getFirst();

        assertEquals(3, selector.specificity().classes());
        assertFalse(selector.matches(input, adapter));
        assertTrue(parser.parse("input[type='text'][data-tags~=\"wide\"]")
                .matchesAny(input, adapter));
        assertTrue(parser.parse("[data-tags]").matchesAny(input, adapter));
        assertFalse(parser.parse("[data-tags~=\"mary\"]").matchesAny(input, adapter));
    }

    @Test
    public void matchesSiblingCombinatorsAndStructuralPseudoClasses() {
        TestNode parent = new TestNode("div", null, Set.of(), null);
        TestNode first = new TestNode("p", null, Set.of("note"), parent);
        TestNode second = new TestNode("span", null, Set.of(), parent);
        TestNode third = new TestNode("p", null, Set.of("note"), parent);
        TestAdapter adapter = new TestAdapter(parent, first, second, third);

        assertTrue(parser.parse("p + span").matchesAny(second, adapter));
        assertTrue(parser.parse("p ~ p").matchesAny(third, adapter));
        assertFalse(parser.parse("p + p").matchesAny(third, adapter));
        assertTrue(parser.parse("p:first-child").matchesAny(first, adapter));
        assertTrue(parser.parse("p:last-child").matchesAny(third, adapter));
        assertTrue(parser.parse("span:nth-child(2)").matchesAny(second, adapter));
        assertTrue(parser.parse("p:nth-child(odd)").matchesAny(third, adapter));
        assertTrue(parser.parse("span:nth-child(2n)").matchesAny(second, adapter));
        assertTrue(parser.parse("p:nth-child(-n + 2)").matchesAny(first, adapter));
        assertFalse(parser.parse("p:nth-child(-n + 2)").matchesAny(third, adapter));
    }

    @Test
    public void parsesAndMatchesNegationPseudoClass() {
        TestNode article = new TestNode("article", null, Set.of("published"), null,
                Map.of("lang", "de"));
        TestAdapter adapter = new TestAdapter(article);

        ComplexSelector selector = parser.parse("article:not(.draft):not([lang='en'])")
                .selectors().getFirst();

        assertTrue(selector.matches(article, adapter));
        assertFalse(parser.parse("article:not(.published)").matchesAny(article, adapter));
        assertFalse(parser.parse(":not(article)").matchesAny(article, adapter));
        assertEquals(new Specificity(0, 2, 1), selector.specificity());
        assertEquals(new Specificity(1, 0, 1), parser.parse("article:not(#featured)")
                .selectors().getFirst().specificity());
        assertEquals(Specificity.ZERO, parser.parse(":not(*)")
                .selectors().getFirst().specificity());
        assertEquals("article:not(.draft):not([lang=\"en\"])", selector.toString());
    }

    @Test
    public void matchesNthAndLastOfTypeAmongMixedSiblings() {
        TestNode parent = new TestNode("div", null, Set.of(), null);
        TestNode firstParagraph = new TestNode("p", null, Set.of(), parent);
        TestNode span = new TestNode("span", null, Set.of(), parent);
        TestNode secondParagraph = new TestNode("p", null, Set.of(), parent);
        TestNode secondSpan = new TestNode("span", null, Set.of(), parent);
        TestAdapter adapter = new TestAdapter(parent, firstParagraph, span,
                secondParagraph, secondSpan);

        assertTrue(parser.parse("p:nth-of-type(2)").matchesAny(secondParagraph, adapter));
        assertFalse(parser.parse("p:nth-of-type(2)").matchesAny(firstParagraph, adapter));
        assertTrue(parser.parse("p:nth-of-type(even)").matchesAny(secondParagraph, adapter));
        assertTrue(parser.parse("p:last-of-type").matchesAny(secondParagraph, adapter));
        assertFalse(parser.parse("span:last-of-type").matchesAny(span, adapter));
        assertTrue(parser.parse("span:last-of-type").matchesAny(secondSpan, adapter));
    }

    @Test
    public void matchesNthLastChildWithTheMirroredFormulaMatrix() {
        TestNode parent = new TestNode("div", null, Set.of(), null);
        List<TestNode> children = new java.util.ArrayList<>();
        for (int index = 0; index < 6; index++) {
            children.add(new TestNode("p", null, Set.of(), parent));
        }
        TestAdapter adapter = new TestAdapter(
                java.util.stream.Stream.concat(java.util.stream.Stream.of(parent),
                        children.stream()).toArray(TestNode[]::new));

        assertTrue(parser.parse("p:nth-last-child(1)").matchesAny(children.get(5), adapter));
        assertFalse(parser.parse("p:nth-last-child(1)").matchesAny(children.get(4), adapter));
        assertTrue(parser.parse("p:nth-last-child(even)").matchesAny(children.get(4), adapter));
        assertTrue(parser.parse("p:nth-last-child(odd)").matchesAny(children.get(5), adapter));
        assertTrue(parser.parse("p:nth-last-child(n)").matchesAny(children.get(0), adapter));
        assertTrue(parser.parse("p:nth-last-child(n+1)").matchesAny(children.get(0), adapter));
        assertTrue(parser.parse("p:nth-last-child(-n+1)").matchesAny(children.get(5), adapter));
        assertFalse(parser.parse("p:nth-last-child(-n+1)").matchesAny(children.get(4), adapter));
        assertFalse(parser.parse("p:nth-last-child(-n)").matchesAny(children.get(0), adapter));
        assertFalse(parser.parse("p:nth-last-child(0n)").matchesAny(children.get(0), adapter));
        assertFalse(parser.parse("p:nth-last-child(0)").matchesAny(children.get(0), adapter));
        assertTrue(parser.parse("p:nth-last-child(3n+1)").matchesAny(children.get(5), adapter));
        assertTrue(parser.parse("p:nth-last-child(3n + 1)").matchesAny(children.get(2), adapter));
        assertTrue(parser.parse("p:nth-last-child(3n-1)").matchesAny(children.get(4), adapter));
        assertFalse(parser.parse("p:nth-last-child(-1)").matchesAny(children.get(0), adapter));
        assertEquals("p:nth-last-child(3n+1)", parser.parse("p:nth-last-child(3n+1)")
                .selectors().getFirst().toString());
        assertEquals(new Specificity(0, 1, 1), parser.parse("p:nth-last-child(3n+1)")
                .selectors().getFirst().specificity());
    }

    @Test
    public void matchesNthLastOfTypeAmongMixedSiblings() {
        TestNode parent = new TestNode("div", null, Set.of(), null);
        TestNode firstParagraph = new TestNode("p", null, Set.of(), parent);
        TestNode span = new TestNode("span", null, Set.of(), parent);
        TestNode secondParagraph = new TestNode("p", null, Set.of(), parent);
        TestNode secondSpan = new TestNode("span", null, Set.of(), parent);
        TestAdapter adapter = new TestAdapter(parent, firstParagraph, span,
                secondParagraph, secondSpan);

        assertTrue(parser.parse("p:nth-last-of-type(1)").matchesAny(secondParagraph, adapter));
        assertFalse(parser.parse("p:nth-last-of-type(1)").matchesAny(firstParagraph, adapter));
        assertTrue(parser.parse("p:nth-last-of-type(even)").matchesAny(firstParagraph, adapter));
        assertTrue(parser.parse("span:nth-last-of-type(odd)").matchesAny(secondSpan, adapter));
        assertTrue(parser.parse("span:nth-last-of-type(-n+1)").matchesAny(secondSpan, adapter));
        assertFalse(parser.parse("span:nth-last-of-type(-n+1)").matchesAny(span, adapter));
        assertTrue(parser.parse("p:nth-last-of-type(3n-1)").matchesAny(firstParagraph, adapter));
        assertFalse(parser.parse("p:nth-last-of-type(3n-1)").matchesAny(secondParagraph, adapter));
        assertFalse(parser.parse("p:nth-last-of-type(2n)").matchesAny(secondParagraph, adapter));
        assertEquals("p:nth-last-of-type(2n)", parser.parse("p:nth-last-of-type(even)")
                .selectors().getFirst().toString());
    }

    @Test
    public void matchesOnlyOfTypeAndEmpty() {
        TestNode parent = new TestNode("div", null, Set.of(), null);
        TestNode lone = new TestNode("em", null, Set.of(), parent);
        TestNode first = new TestNode("p", null, Set.of(), parent);
        TestNode second = new TestNode("p", null, Set.of(), parent);
        TestNode blank = new TestNode("span", null, Set.of(), parent);
        TestNode textChild = new TestNode("i", null, Set.of(), null);
        TestNode populated = new TestNode("span", null, Set.of(), parent, Map.of(),
                List.of(textChild));
        TestNode whitespace = new TestNode("u", null, Set.of(), null);
        TestNode withWhitespace = new TestNode("b", null, Set.of(), parent, Map.of(),
                List.of(whitespace));
        TestAdapter adapter = new TestAdapter(parent, lone, first, second, blank,
                populated, textChild, withWhitespace, whitespace);

        assertTrue(parser.parse("em:only-of-type").matchesAny(lone, adapter));
        assertFalse(parser.parse("p:only-of-type").matchesAny(first, adapter));
        assertFalse(parser.parse("p:only-of-type").matchesAny(second, adapter));
        assertTrue(parser.parse("span:empty").matchesAny(blank, adapter));
        assertFalse(parser.parse("span:empty").matchesAny(populated, adapter));
        assertFalse(parser.parse("b:empty").matchesAny(withWhitespace, adapter));
        assertEquals(new Specificity(0, 1, 1), parser.parse("em:only-of-type")
                .selectors().getFirst().specificity());
        assertEquals(new Specificity(0, 1, 0), parser.parse(":empty")
                .selectors().getFirst().specificity());
    }

    @Test
    public void parsesAndMatchesInteractiveStatePseudoClasses() {
        TestNode hovered = new TestNode("a", null, Set.of("hovered", "focused", "active"), null);
        TestNode checked = new TestNode("input", null, Set.of(), null,
                Map.of("checked", "checked"));
        TestAdapter adapter = new TestAdapter(hovered, checked);

        assertTrue(parser.parse("a:hover").matchesAny(hovered, adapter));
        assertTrue(parser.parse("input:checked").matchesAny(checked, adapter));
        assertTrue(parser.parse("a:focus:active").matchesAny(hovered, adapter));
        assertFalse(parser.parse("a:checked").matchesAny(hovered, adapter));
        assertEquals(new Specificity(0, 1, 1),
                parser.parse("a:hover").selectors().getFirst().specificity());
    }

    @Test
    public void parsesAndMatchesDisabledAndEnabledPseudoClasses() {
        TestNode disabled = new TestNode("button", null, Set.of(), null,
                Map.of("disabled", ""));
        TestNode enabled = new TestNode("button", null, Set.of(), null);
        TestNode plain = new TestNode("div", null, Set.of(), null,
                Map.of("disabled", ""));
        TestAdapter adapter = new TestAdapter(disabled, enabled, plain);

        assertEquals("button:disabled", parser.parse("button:disabled")
                .selectors().getFirst().toString());
        assertEquals("input:enabled", parser.parse("input:enabled")
                .selectors().getFirst().toString());
        assertEquals(new Specificity(0, 1, 1), parser.parse("button:disabled")
                .selectors().getFirst().specificity());
        assertTrue(parser.parse("button:disabled").matchesAny(disabled, adapter));
        assertTrue(parser.parse("button:enabled").matchesAny(enabled, adapter));
        assertFalse(parser.parse("button:enabled").matchesAny(disabled, adapter));
        assertFalse(parser.parse("button:disabled").matchesAny(enabled, adapter));
        assertFalse(parser.parse("div:disabled").matchesAny(plain, adapter));
        assertFalse(parser.parse("div:enabled").matchesAny(plain, adapter));
    }

    @Test
    public void matchesLinkAndVisitedAndNestedNotChains() {
        TestNode unvisited = new TestNode("a", null, Set.of("nav"), null,
                Map.of("href", "https://example.com"));
        TestNode plainLink = new TestNode("a", null, Set.of(), null);
        TestNode unrelated = new TestNode("p", null, Set.of("nav"), null,
                Map.of("attr", "x"));
        TestAdapter adapter = new TestAdapter(unvisited, plainLink, unrelated);

        assertTrue(parser.parse("a:link").matchesAny(unvisited, adapter));
        assertFalse(parser.parse("a:link").matchesAny(plainLink, adapter));
        assertFalse(parser.parse("a:visited").matchesAny(unvisited, adapter));
        assertFalse(parser.parse("p:link").matchesAny(unrelated, adapter));
        assertEquals(new Specificity(0, 1, 1), parser.parse("a:link")
                .selectors().getFirst().specificity());

        ComplexSelector chained = parser.parse(
                ":not(.class):not(#id):not([attr]):not(:link)").selectors().getFirst();
        assertTrue(chained.matches(plainLink, adapter));
        assertFalse(chained.matches(unvisited, adapter));
        assertFalse(chained.matches(unrelated, adapter));
        assertTrue(parser.parse(":not(.nav):not(:link)").matchesAny(plainLink, adapter));
        assertFalse(parser.parse(":not(.nav):not(:link)").matchesAny(unvisited, adapter));
    }

    @Test
    public void parsesFocusVisibleFocusWithinAndPlaceholderShownStates() {
        assertEquals("input:focus-visible", parser.parse("input:focus-visible")
                .selectors().getFirst().toString());
        assertEquals("input:focus-within", parser.parse("input:focus-within")
                .selectors().getFirst().toString());
        assertEquals("input:placeholder-shown", parser.parse("input:placeholder-shown")
                .selectors().getFirst().toString());
        assertEquals("div:focus-within", parser.parse("div:focus-within")
                .selectors().getFirst().toString());
        assertEquals(new Specificity(0, 1, 1), parser.parse("input:focus-visible")
                .selectors().getFirst().specificity());
        assertEquals(new Specificity(0, 1, 1), parser.parse("input:focus-within")
                .selectors().getFirst().specificity());
        assertEquals(new Specificity(0, 1, 1), parser.parse("input:placeholder-shown")
                .selectors().getFirst().specificity());
    }

    @Test
    public void parsesAndMatchesIsWhereAndSelectorListNot() {
        TestNode card = new TestNode("div", null, Set.of("card", "selected"), null,
                Map.of("data-active", "true"));
        TestNode plain = new TestNode("div", null, Set.of("card"), null);
        TestNode button = new TestNode("button", null, Set.of(), null,
                Map.of("disabled", ""));
        TestAdapter adapter = new TestAdapter(card, plain, button);

        assertTrue(parser.parse(":is(.selected, [data-active])").matchesAny(card, adapter));
        assertFalse(parser.parse(":is(.selected, [data-active])").matchesAny(plain, adapter));
        assertTrue(parser.parse("div:is(.card, .other)").matchesAny(plain, adapter));
        assertFalse(parser.parse("span:is(.card, .other)").matchesAny(plain, adapter));
        assertTrue(parser.parse(":where(.card, .selected)").matchesAny(plain, adapter));
        assertTrue(parser.parse(":not(.selected, [data-active])").matchesAny(plain, adapter));
        assertFalse(parser.parse(":not(.selected, [data-active])").matchesAny(card, adapter));
        assertFalse(parser.parse(":not(:disabled, .selected)").matchesAny(button, adapter));
        assertTrue(parser.parse(":not(:disabled, .selected)").matchesAny(plain, adapter));
        assertTrue(parser.parse("div:is(.card):not(.selected)").matchesAny(plain, adapter));
        assertFalse(parser.parse("div:is(.card):not(.selected)").matchesAny(card, adapter));
        assertTrue(parser.parse(":is(div.card, button):not([disabled])")
                .matchesAny(plain, adapter));
        assertFalse(parser.parse(":is(div.card, button):not([disabled])")
                .matchesAny(button, adapter));
    }

    @Test
    public void parsesNestedIsWhereAndNotWithCombinators() {
        TestNode main = new TestNode("main", null, Set.of(), null);
        TestNode section = new TestNode("section", null, Set.of("card"), main);
        TestNode target = new TestNode("p", null, Set.of("note"), section,
                Map.of("lang", "de"));
        TestNode wrapper = new TestNode("div", null, Set.of(), main);
        TestNode orphan = new TestNode("p", null, Set.of(), wrapper);
        TestNode deepCard = new TestNode("section", null, Set.of("card"), null);
        TestNode deep = new TestNode("p", null, Set.of(), deepCard);
        TestAdapter adapter = new TestAdapter(main, section, wrapper, deepCard,
                target, orphan, deep);

        ComplexSelector selector = parser.parse(
                "main :is(section > p.note, aside p)").selectors().getFirst();
        assertTrue(selector.matches(target, adapter));
        assertFalse(selector.matches(orphan, adapter));

        ComplexSelector nested = parser.parse(
                ":not(:is(.card, .other)) p").selectors().getFirst();
        assertTrue(nested.matches(target, adapter));
        assertTrue(nested.matches(orphan, adapter));
        assertFalse(nested.matches(deep, adapter));

        ComplexSelector combined = parser.parse(
                ":is(:is(.card, .note), :where([lang]))").selectors().getFirst();
        assertTrue(combined.matches(target, adapter));
        assertTrue(combined.matches(section, adapter));
    }

    @Test
    public void calculatesIsWhereAndNotListSpecificity() {
        assertEquals(new Specificity(1, 0, 0), parser.parse(":is(#id, .a)")
                .selectors().getFirst().specificity());
        assertEquals(new Specificity(1, 1, 0), parser.parse(":is(.a, #id) > .b")
                .selectors().getFirst().specificity());
        assertEquals(new Specificity(1, 0, 1), parser.parse("div:is(.a, #id)")
                .selectors().getFirst().specificity());
        assertEquals(Specificity.ZERO, parser.parse(":where(.a, #id)")
                .selectors().getFirst().specificity());
        assertEquals(new Specificity(0, 1, 0), parser.parse(":where(.a, #id) .b")
                .selectors().getFirst().specificity());
        assertEquals(new Specificity(1, 0, 0), parser.parse(":not(.a, #id)")
                .selectors().getFirst().specificity());
        assertEquals(Specificity.ZERO, parser.parse(":not(*)")
                .selectors().getFirst().specificity());
    }

    @Test
    public void roundTripsIsWhereAndNotListInToString() {
        assertEquals(":is(.a, .b)", parser.parse(":is(.a,.b)")
                .selectors().getFirst().toString());
        assertEquals(":where(.a, #b)", parser.parse(":where(.a, #b)")
                .selectors().getFirst().toString());
        assertEquals("div:is(.a, .b):not(.c, .d)", parser.parse("div:is(.a,.b):not(.c,.d)")
                .selectors().getFirst().toString());
    }

    @Test
    public void rejectsPseudoElementsInsideIsWhereAndNot() {
        for (String source : List.of(":is(.a::before)", ":where(.a::after)",
                ":not(.a::before)", ":is(div::first-line)")) {
            assertThrows(SelectorParseException.class, () -> parser.parse(source));
        }
    }

    @Test
    public void parsesGeneratedPseudoElementsAndCountsTheirSpecificity() {
        ComplexSelector selector = parser.parse(".badge:hover::before").selectors().getFirst();

        assertEquals("before", selector.pseudoElement());
        assertEquals(new Specificity(0, 2, 1), selector.specificity());
        assertEquals(".badge:hover::before", selector.toString());
    }

    @Test
    public void acceptsLegacySingleColonGeneratedPseudoElements() {
        ComplexSelector before = parser.parse(".badge:before").selectors().getFirst();
        ComplexSelector after = parser.parse("li:last-child:after").selectors().getFirst();

        assertEquals("before", before.pseudoElement());
        assertEquals("after", after.pseudoElement());
        assertEquals(new Specificity(0, 1, 1), before.specificity());
        assertEquals(".badge::before", before.toString());
        assertEquals("li:last-child::after", after.toString());
    }

    @Test
    public void parsesFirstLetterAndFirstLinePseudoElements() {
        ComplexSelector letter = parser.parse("p::first-letter").selectors().getFirst();
        ComplexSelector line = parser.parse("p::first-line").selectors().getFirst();
        ComplexSelector legacyLetter = parser.parse("p:first-letter").selectors().getFirst();

        assertEquals("first-letter", letter.pseudoElement());
        assertEquals("first-line", line.pseudoElement());
        assertEquals("first-letter", legacyLetter.pseudoElement());
        assertEquals("p::first-letter", letter.toString());
        assertEquals(new Specificity(0, 0, 2), letter.specificity());

        TestNode paragraph = new TestNode("p", null, Set.of(), null);
        TestAdapter adapter = new TestAdapter(paragraph);
        assertTrue(parser.parse("p::first-letter").matchesAny(paragraph, adapter));
        assertTrue(parser.parse("p::first-line").matchesAny(paragraph, adapter));
    }

    @Test
    public void rejectsInvalidAndUnsupportedSelectorsWithPositions() {
        for (String source : List.of("", "div,",
                ":nth-child(2n+)", ":nth-of-type()", ":not()", ":is()", ":where()",
                ":is(.a,)", "div > > p")) {
            SelectorParseException exception = assertThrows(
                    SelectorParseException.class, () -> parser.parse(source));
            assertEquals(source, exception.getSelector());
            assertTrue(exception.getPosition() >= 0);
        }
        for (String source : List.of("div::marker", "div::before span")) {
            assertThrows(SelectorParseException.class, () -> parser.parse(source));
        }
    }

    @Test
    public void parsesAndMatchesNamespacedTypeAndAttributeSelectors() {
        assertEquals("*|html", parser.parse("*|html").selectors().getFirst().toString());
        assertEquals("|html", parser.parse("|html").selectors().getFirst().toString());
        assertEquals("svg|rect", parser.parse("svg|rect").selectors().getFirst().toString());
        assertEquals("*|*", parser.parse("*|*").selectors().getFirst().toString());
        assertEquals("[*|attr]", parser.parse("[*|attr]").selectors().getFirst().toString());
        assertEquals("[*|attr=\"val\"]", parser.parse("[*|attr=val]")
                .selectors().getFirst().toString());
        assertEquals("[|attr]", parser.parse("[|attr]").selectors().getFirst().toString());
        assertEquals("[svg|attr=\"val\"]", parser.parse("[svg|attr=\"val\"]")
                .selectors().getFirst().toString());
        assertEquals("*|html[*|attr]", parser.parse("*|html[*|attr]")
                .selectors().getFirst().toString());
        assertEquals(new Specificity(0, 0, 1), parser.parse("*|html")
                .selectors().getFirst().specificity());

        TestNode html = new TestNode("html", null, Set.of(), null, Map.of("attr", "val"));
        TestNode plainDiv = new TestNode("div", null, Set.of(), null);
        TestAdapter adapter = new TestAdapter(html, plainDiv);
        assertTrue(parser.parse("*|html").matchesAny(html, adapter));
        assertTrue(parser.parse("|html").matchesAny(html, adapter));
        assertFalse(parser.parse("svg|rect").matchesAny(html, adapter));
        assertTrue(parser.parse("*|div").matchesAny(plainDiv, adapter));
        assertTrue(parser.parse("|div").matchesAny(plainDiv, adapter));
        assertTrue(parser.parse("*|html[*|attr]").matchesAny(html, adapter));
    }

    @Test
    public void rejectsInvalidNamespacesWithPositions() {
        for (String source : List.of("|", "svg|", "*|", "[*]", "[|]", "svg||rect")) {
            SelectorParseException exception = assertThrows(
                    SelectorParseException.class, () -> parser.parse(source));
            assertTrue(exception.getPosition() >= 0);
        }
    }

    private record TestNode(String tagName, String id, Set<String> classes, TestNode parent,
                            Map<String, String> attributes, List<TestNode> children) {
        private TestNode(String tagName, String id, Set<String> classes, TestNode parent) {
            this(tagName, id, classes, parent, Map.of(), List.of());
        }
        private TestNode(String tagName, String id, Set<String> classes, TestNode parent,
                         Map<String, String> attributes) {
            this(tagName, id, classes, parent, attributes, List.of());
        }
        private TestNode {
            classes = new LinkedHashSet<>(classes);
            attributes = Map.copyOf(attributes);
            children = List.copyOf(children);
        }
    }

    private static final class TestAdapter implements SelectorNodeAdapter<TestNode> {
        private final List<TestNode> nodes;

        private TestAdapter(TestNode... nodes) {
            this.nodes = List.of(nodes);
        }

        @Override
        public TestNode parentElement(TestNode element) {
            return element.parent();
        }

        @Override
        public TestNode previousElementSibling(TestNode element) {
            return sibling(element, -1);
        }

        @Override
        public TestNode nextElementSibling(TestNode element) {
            return sibling(element, 1);
        }

        @Override
        public String tagName(TestNode element) {
            return element.tagName();
        }

        @Override
        public boolean hasChildren(TestNode element) {
            return !element.children().isEmpty();
        }

        private TestNode sibling(TestNode element, int offset) {
            List<TestNode> siblings = nodes.stream()
                    .filter(node -> node.parent() == element.parent())
                    .toList();
            int current = -1;
            for (int index = 0; index < siblings.size(); index++) {
                if (siblings.get(index) == element) {
                    current = index;
                    break;
                }
            }
            int index = current + offset;
            return index >= 0 && index < siblings.size() ? siblings.get(index) : null;
        }

        @Override
        public boolean matchesType(TestNode element, String typeName) {
            return typeName.equalsIgnoreCase(element.tagName());
        }

        @Override
        public String id(TestNode element) {
            return element.id();
        }

        @Override
        public boolean hasClass(TestNode element, String className) {
            return element.classes().contains(className);
        }

        @Override
        public boolean hasAttribute(TestNode element, String name) {
            return element.attributes().containsKey(name);
        }

        @Override
        public String attributeValue(TestNode element, String name) {
            return element.attributes().get(name);
        }

        @Override
        public boolean matchesState(TestNode element, String state) {
            return switch (state) {
                case "hover" -> element.classes().contains("hovered");
                case "checked" -> element.attributes().containsKey("checked");
                case "focus" -> element.classes().contains("focused");
                case "active" -> element.classes().contains("active");
                case "link" -> switch (element.tagName()) {
                    case "a", "area", "link" -> element.attributes().containsKey("href");
                    default -> false;
                };
                case "visited" -> false;
                case "disabled", "enabled" -> {
                    boolean formControl = switch (element.tagName()) {
                        case "button", "input", "select", "textarea",
                             "option", "optgroup", "fieldset" -> true;
                        default -> false;
                    };
                    if (!formControl) yield false;
                    yield state.equals("disabled")
                            == element.attributes().containsKey("disabled");
                }
                default -> false;
            };
        }
    }
}

package com.browicy.engine.selectors;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SelectorParserTest {

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
        TestAdapter adapter = new TestAdapter();

        Selector selector = parser.parse("main > section.card p.note.highlight#message")
                .selectors().getFirst();

        assertTrue(selector.matches(target, adapter));
        assertFalse(selector.matches(wrapper, adapter));
        assertSame(section, adapter.parentElement(wrapper));
    }

    @Test
    public void rejectsInvalidAndUnsupportedSelectorsWithPositions() {
        for (String source : List.of("", "div,", "div + p", "[data-id]", "div > > p")) {
            SelectorParseException exception = assertThrows(
                    SelectorParseException.class, () -> parser.parse(source));
            assertEquals(source, exception.getSelector());
            assertTrue(exception.getPosition() >= 0);
        }
    }

    private record TestNode(String tagName, String id, Set<String> classes, TestNode parent) {
        private TestNode {
            classes = new LinkedHashSet<>(classes);
        }
    }

    private static final class TestAdapter implements SelectorNodeAdapter<TestNode> {
        @Override
        public TestNode parentElement(TestNode element) {
            return element.parent();
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
    }
}

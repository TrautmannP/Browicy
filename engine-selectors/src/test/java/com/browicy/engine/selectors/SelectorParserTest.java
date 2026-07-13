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
    public void rejectsInvalidAndUnsupportedSelectorsWithPositions() {
        for (String source : List.of("", "div,", "[attr=value]", ":hover",
                ":nth-child(2n+)", "div > > p")) {
            SelectorParseException exception = assertThrows(
                    SelectorParseException.class, () -> parser.parse(source));
            assertEquals(source, exception.getSelector());
            assertTrue(exception.getPosition() >= 0);
        }
    }

    private record TestNode(String tagName, String id, Set<String> classes, TestNode parent,
                            Map<String, String> attributes) {
        private TestNode(String tagName, String id, Set<String> classes, TestNode parent) {
            this(tagName, id, classes, parent, Map.of());
        }

        private TestNode {
            classes = new LinkedHashSet<>(classes);
            attributes = Map.copyOf(attributes);
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
    }
}

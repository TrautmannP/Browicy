package com.browicy.engine.dom;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class ParentNodeTest {

    @Test
    public void documentQueriesSimpleAndCombinedSelectorsInTreeOrder() {
        Fixture fixture = fixture();

        assertSame(fixture.section, fixture.document.querySelector("#main"));
        assertSame(fixture.firstNote, fixture.document.querySelector("p.note.highlight"));
        assertEquals(List.of(fixture.firstNote, fixture.secondNote),
                fixture.document.querySelectorAll(".note"));
        assertEquals(List.of(fixture.section, fixture.firstNote, fixture.secondNote),
                fixture.document.querySelectorAll("section, .note"));
        assertEquals(6, fixture.document.querySelectorAll("*").size());
    }

    @Test
    public void supportsDescendantAndChildCombinators() {
        Fixture fixture = fixture();

        assertSame(fixture.firstNote,
                fixture.document.querySelector("body #main > p.note"));
        assertEquals(List.of(fixture.firstNote),
                fixture.document.querySelectorAll("section > p.note"));
        assertEquals(List.of(fixture.firstNote, fixture.secondNote),
                fixture.document.querySelectorAll("#main .note"));
        assertNull(fixture.document.querySelector("body > p.note"));
    }

    @Test
    public void supportsAttributesSiblingsAndStructuralPseudoClasses() {
        Document document = new Document("about:test");
        Element root = document.createElement("div");
        Element first = document.createElement("input");
        first.setAttribute("type", "text");
        first.setAttribute("data-tags", "primary wide");
        Element second = document.createElement("span");
        Element third = document.createElement("input");
        third.setAttribute("type", "checkbox");

        document.appendChild(root);
        root.appendChild(first);
        root.appendChild(document.createTextNode("ignored by element selectors"));
        root.appendChild(second);
        root.appendChild(third);

        assertSame(first, document.querySelector("input[type=\"text\"][data-tags~=\"wide\"]"));
        assertEquals(List.of(first, third), document.querySelectorAll("[type]"));
        assertSame(second, document.querySelector("input + span"));
        assertSame(third, document.querySelector("input ~ input"));
        assertSame(first, document.querySelector("input:first-child"));
        assertSame(third, document.querySelector("input:last-child"));
        assertSame(second, document.querySelector("span:nth-child(2)"));
        assertEquals(List.of(first, third), document.querySelectorAll(":nth-child(odd)"));
        assertEquals(List.of(first, third), document.querySelectorAll("input:not([disabled])"));
        assertSame(third, document.querySelector("input:nth-of-type(2)"));
        assertSame(third, document.querySelector("input:last-of-type"));
        assertSame(second, document.querySelector("span:last-of-type"));
    }

    @Test
    public void elementQueriesOnlyDescendantsAndReturnsStaticSnapshots() {
        Fixture fixture = fixture();

        assertNull(fixture.section.querySelector("#main"));
        assertSame(fixture.firstNote, fixture.section.querySelector(".note"));

        List<Element> snapshot = fixture.section.querySelectorAll(".note");
        Element later = fixture.document.createElement("div");
        later.setAttribute("class", "note");
        fixture.section.appendChild(later);

        assertEquals(List.of(fixture.firstNote, fixture.secondNote), snapshot);
        assertEquals(List.of(fixture.firstNote, fixture.secondNote, later),
                fixture.section.querySelectorAll(".note"));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.add(fixture.section));
    }

    @Test
    public void documentFragmentImplementsParentNodeQueries() {
        Document document = new Document("about:test");
        DocumentFragment fragment = document.createDocumentFragment();
        Element wrapper = document.createElement("div");
        Element target = document.createElement("span");
        target.setAttribute("id", "target");
        wrapper.appendChild(target);
        fragment.appendChild(wrapper);

        assertSame(target, fragment.querySelector("div > #target"));
        assertEquals(List.of(target), fragment.querySelectorAll("span, #target"));
    }

    @Test
    public void invalidOrUnsupportedSelectorsThrowSyntaxError() {
        Fixture fixture = fixture();

        for (String selector : List.of("", "div,", "[attr=value]", ":hover",
                ":nth-child(nope)", "div > > p")) {
            DomException exception = assertThrows(DomException.class,
                    () -> fixture.document.querySelector(selector));
            assertEquals("SyntaxError", exception.getDomName());
            assertEquals(DomException.SYNTAX_ERR, exception.getCode());
        }
    }

    private static Fixture fixture() {
        Document document = new Document("about:test");
        Element html = document.createElement("html");
        Element body = document.createElement("body");
        Element section = document.createElement("section");
        section.setAttribute("id", "main");
        section.setAttribute("class", "card");

        Element firstNote = document.createElement("p");
        firstNote.setAttribute("class", "note highlight");
        Element wrapper = document.createElement("div");
        Element secondNote = document.createElement("span");
        secondNote.setAttribute("class", "note");

        document.appendChild(html);
        html.appendChild(body);
        body.appendChild(section);
        section.appendChild(firstNote);
        section.appendChild(wrapper);
        wrapper.appendChild(secondNote);
        return new Fixture(document, section, firstNote, secondNote);
    }

    private record Fixture(Document document, Element section,
                           Element firstNote, Element secondNote) {
    }
}

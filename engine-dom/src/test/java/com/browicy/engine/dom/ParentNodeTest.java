package com.browicy.engine.dom;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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

        for (String selector : List.of("", "div,",
                ":nth-child(nope)", "div > > p")) {
            DomException exception = assertThrows(DomException.class,
                    () -> fixture.document.querySelector(selector));
            assertEquals("SyntaxError", exception.getDomName());
            assertEquals(DomException.SYNTAX_ERR, exception.getCode());
        }
    }

    @Test
    public void queriesFocusAndActiveStateTrackedByTheDocument() {
        Fixture fixture = fixture();

        fixture.document.setFocusedElement(fixture.firstNote);
        fixture.document.setActiveElement(fixture.secondNote);

        assertSame(fixture.firstNote, fixture.document.querySelector(":focus"));
        assertSame(fixture.secondNote, fixture.document.querySelector(":active"));
        fixture.document.setFocusedElement(null);
        fixture.document.setActiveElement(null);
        assertNull(fixture.document.querySelector(":focus, :active"));
        assertSame(fixture.firstNote, fixture.document.querySelector("p::before"));
    }

    @Test
    public void documentTagLookupSupportsWildcard() {
        Fixture fixture = fixture();

        assertEquals(fixture.document.querySelectorAll("*").size(),
                fixture.document.getElementsByTagName("*").size());
        assertTrue(fixture.document.getElementsByTagName("*").contains(fixture.secondNote));
    }

    @Test
    public void matchesNthLastEmptyOnlyOfTypeAndPseudoElements() {
        Document document = new Document("about:test");
        Element root = document.createElement("div");
        Element blank = document.createElement("span");
        Element withText = document.createElement("span");
        Element onlyEm = document.createElement("em");
        Element first = document.createElement("p");
        Element second = document.createElement("p");
        Element third = document.createElement("p");

        document.appendChild(root);
        root.appendChild(blank);
        root.appendChild(document.createTextNode("not an element"));
        root.appendChild(withText);
        withText.appendChild(document.createTextNode("text"));
        root.appendChild(onlyEm);
        root.appendChild(first);
        root.appendChild(second);
        root.appendChild(third);

        assertSame(third, document.querySelector("p:nth-last-child(1)"));
        assertSame(first, document.querySelector("p:nth-last-child(3)"));
        assertSame(first, document.querySelector("p:nth-last-of-type(3n)"));
        assertSame(third, document.querySelector("p:nth-last-child(-n+1)"));
        assertSame(blank, document.querySelector("span:empty"));
        assertSame(blank, document.querySelector(":empty"));
        assertSame(withText, document.querySelector("span:empty + span"));
        assertSame(onlyEm, document.querySelector("em:only-of-type"));
        assertEquals(List.of(first, second),
                document.querySelectorAll("p:not(:nth-last-child(1)):not(:first-child)"));

        assertSame(first, document.querySelector("p::first-letter"));
        assertEquals(List.of(first, second, third),
                document.querySelectorAll("p::first-line"));
    }

    @Test
    public void matchesLinkVisitedAndTargetStates() {
        Document document = new Document("https://example.com/page#note");
        Element root = document.createElement("div");
        Element link = document.createElement("a");
        link.setAttribute("href", "https://example.com/other");
        Element plainAnchor = document.createElement("a");
        Element targeted = document.createElement("p");
        targeted.setAttribute("id", "note");
        Element unrelated = document.createElement("p");
        unrelated.setAttribute("id", "other");

        document.appendChild(root);
        root.appendChild(link);
        root.appendChild(plainAnchor);
        root.appendChild(targeted);
        root.appendChild(unrelated);

        assertSame(link, document.querySelector("a:link"));
        assertSame(plainAnchor, document.querySelector("a:not(:link)"));
        assertSame(null, document.querySelector("a:visited"));
        assertSame(targeted, document.querySelector(":target"));
        assertSame(null, document.querySelector("#other:target"));

        Document withoutFragment = new Document("https://example.com/page");
        Element target = withoutFragment.createElement("div");
        target.setAttribute("id", "note");
        withoutFragment.appendChild(target);
        assertSame(null, withoutFragment.querySelector("#note:target"));
    }

    @Test
    public void matchesIndeterminateCheckboxesAndRadioGroups() {
        Document document = new Document("about:test");
        Element root = document.createElement("div");
        Element checkbox = document.createElement("input");
        checkbox.setAttribute("type", "checkbox");
        Element radioA = document.createElement("input");
        radioA.setAttribute("type", "radio");
        radioA.setAttribute("name", "group");
        Element radioB = document.createElement("input");
        radioB.setAttribute("type", "radio");
        radioB.setAttribute("name", "group");
        Element checkedRadio = document.createElement("input");
        checkedRadio.setAttribute("type", "radio");
        checkedRadio.setAttribute("name", "chosen");
        checkedRadio.setCheckedState(true);
        Element uncheckedRadio = document.createElement("input");
        uncheckedRadio.setAttribute("type", "radio");
        uncheckedRadio.setAttribute("name", "chosen");

        document.appendChild(root);
        root.appendChild(checkbox);
        root.appendChild(radioA);
        root.appendChild(radioB);
        root.appendChild(checkedRadio);
        root.appendChild(uncheckedRadio);

        assertSame(radioA, document.querySelector("input:indeterminate"));
        assertEquals(List.of(radioA, radioB), document.querySelectorAll(":indeterminate"));
        checkbox.setIndeterminate(true);
        assertSame(checkbox, document.querySelector("input:indeterminate"));
        assertEquals(List.of(checkbox, radioA, radioB),
                document.querySelectorAll(":indeterminate"));
        assertSame(null, document.querySelector("input[name=chosen]:indeterminate"));
    }

    @Test
    public void matchesPrefixAndSuffixAttributeSelectors() {
        Document document = new Document("about:test");
        Element root = document.createElement("div");
        Element pdf = document.createElement("a");
        pdf.setAttribute("href", "https://example.com/report.pdf");
        pdf.setAttribute("lang", "de-DE");
        Element mail = document.createElement("a");
        mail.setAttribute("href", "mailto:user@example.com");

        document.appendChild(root);
        root.appendChild(pdf);
        root.appendChild(mail);

        assertSame(pdf, document.querySelector("a[href^=\"https://\"]"));
        assertSame(pdf, document.querySelector("a[href$=\".pdf\"]"));
        assertSame(pdf, document.querySelector("[lang^=\"de\"]"));
        assertSame(mail, document.querySelector("a[href^=\"mailto\"]"));
        assertSame(mail, document.querySelector("a[href$=\".pdf\"] + a"));
        assertSame(null, document.querySelector("a[href^=\"mailto\"] + a"));
    }

    @Test
    public void matchesNamespacePrefixedTypeSelectors() {
        Document document = new Document("about:test");
        Element html = document.createElement("html");
        Element svg = document.createElementNS("http://www.w3.org/2000/svg", "svg:svg");
        Element rect = document.createElementNS("http://www.w3.org/2000/svg", "svg:rect");
        Element plainDiv = document.createElement("div");

        document.appendChild(html);
        html.appendChild(svg);
        svg.appendChild(rect);
        html.appendChild(plainDiv);

        assertSame(html, document.querySelector("*|html"));
        assertSame(html, document.querySelector("|html"));
        assertSame(null, document.querySelector("svg|html"));
        assertSame(rect, document.querySelector("*|rect"));
        assertSame(rect, document.querySelector("svg|rect"));
        assertSame(null, document.querySelector("|rect"));
        assertSame(plainDiv, document.querySelector("*|div"));
        assertSame(null, document.querySelector("svg|div"));
    }

    @Test
    public void matchesNamespacePrefixedAttributeSelectors() {
        Document document = new Document("about:test");
        Element root = document.createElement("div");
        Element target = document.createElement("p");
        target.setAttribute("attr", "val");

        document.appendChild(root);
        root.appendChild(target);

        assertSame(target, document.querySelector("[*|attr]"));
        assertSame(target, document.querySelector("[*|attr=val]"));
        assertSame(target, document.querySelector("[|attr]"));
        assertSame(null, document.querySelector("[svg|attr]"));
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

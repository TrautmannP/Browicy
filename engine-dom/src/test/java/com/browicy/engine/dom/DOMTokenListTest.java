package com.browicy.engine.dom;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DOMTokenListTest {

    @Test
    public void classListIsStableAndReadsCurrentClassAttribute() {
        Element element = new Element("div");
        element.setAttribute("class", "  card\tactive  card\n");

        DOMTokenList classList = element.getClassList();

        assertSame(classList, element.getClassList());
        assertEquals(2, classList.getLength());
        assertEquals("card", classList.item(0));
        assertEquals("active", classList.item(1));
        assertNull(classList.item(2));
        assertTrue(classList.contains("card"));
        assertEquals(List.of("card", "active"), element.getClassNames());

        element.setAttribute("class", "external");
        assertEquals("external", classList.getValue());
        assertTrue(classList.contains("external"));
        assertFalse(classList.contains("card"));
    }

    @Test
    public void addAndRemoveSynchronizeClassAttributeWithoutDuplicates() {
        Element element = new Element("div");
        element.setAttribute("class", "card active");
        DOMTokenList classList = element.getClassList();

        classList.add("active", "wide", "card");
        assertEquals("card active wide", element.getAttribute("class"));

        classList.remove("active", "missing");
        assertEquals("card wide", element.getAttribute("class"));

        classList.remove("card", "wide");
        assertTrue(element.hasAttribute("class"));
        assertEquals("", element.getAttribute("class"));
    }

    @Test
    public void toggleReturnsNewMembershipAndSupportsForce() {
        Element element = new Element("div");
        DOMTokenList classList = element.getClassList();

        assertTrue(classList.toggle("open"));
        assertEquals("open", element.getAttribute("class"));
        assertFalse(classList.toggle("open"));
        assertEquals("", element.getAttribute("class"));

        assertFalse(classList.toggle("open", false));
        assertFalse(element.getClassList().contains("open"));
        assertTrue(classList.toggle("open", true));
        assertTrue(element.getClassList().contains("open"));
        assertTrue(classList.toggle("open", true));
        assertEquals("open", element.getAttribute("class"));
    }

    @Test
    public void mutationsValidateAllTokensBeforeChangingAttribute() {
        Element element = new Element("div");
        element.setAttribute("class", "initial");

        DomException whitespace = assertThrows(DomException.class,
                () -> element.getClassList().add("valid", "not valid"));
        assertEquals("InvalidCharacterError", whitespace.getDomName());
        assertEquals("initial", element.getAttribute("class"));

        DomException empty = assertThrows(DomException.class,
                () -> element.getClassList().remove(""));
        assertEquals("SyntaxError", empty.getDomName());
        assertEquals("initial", element.getAttribute("class"));
    }

    @Test
    public void removingFromMissingAttributeDoesNotCreateIt() {
        Element element = new Element("div");

        element.getClassList().remove("missing");

        assertFalse(element.hasAttribute("class"));
    }
}

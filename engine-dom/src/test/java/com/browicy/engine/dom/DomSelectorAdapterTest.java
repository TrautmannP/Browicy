package com.browicy.engine.dom;

import com.browicy.engine.selectors.SelectorParser;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DomSelectorAdapterTest {

    private final Document document = new Document("about:test");

    @Test
    public void focusVisibleMatchesOnlyTheFocusedElement() {
        Element root = document.createElement("div");
        Element first = document.createElement("input");
        Element second = document.createElement("input");
        document.appendChild(root);
        root.appendChild(first);
        root.appendChild(second);

        document.setFocusedElement(first);

        assertTrue(document.querySelector(":focus-visible") == first);
        assertTrue(document.querySelector("input:focus-visible") == first);
        assertFalse(document.querySelectorAll("input:focus-visible").contains(second));
        assertFalse(matches("input:not(:focus-visible)", first));
        assertTrue(matches("input:not(:focus-visible)", second));
    }

    @Test
    public void focusWithinMatchesAncestorsOfTheFocusedElement() {
        Element root = document.createElement("div");
        Element section = document.createElement("section");
        Element input = document.createElement("input");
        Element sibling = document.createElement("p");
        document.appendChild(root);
        root.appendChild(section);
        section.appendChild(input);
        section.appendChild(sibling);

        document.setFocusedElement(input);

        assertTrue(matches(":focus-within", input));
        assertTrue(matches(":focus-within", section));
        assertTrue(matches(":focus-within", root));
        assertTrue(matches("section:focus-within", section));
        assertFalse(matches(":focus-within", sibling));
    }

    @Test
    public void placeholderShownMatchesEmptyFormControlsWithPlaceholder() {
        Element root = document.createElement("div");
        Element emptyInput = document.createElement("input");
        emptyInput.setAttribute("placeholder", "Suche...");
        Element filledInput = document.createElement("input");
        filledInput.setAttribute("placeholder", "Suche...");
        filledInput.setAttribute("value", "text");
        Element noPlaceholder = document.createElement("input");
        Element emptyTextarea = document.createElement("textarea");
        emptyTextarea.setAttribute("placeholder", "Kommentar");
        Element plainDiv = document.createElement("div");
        plainDiv.setAttribute("placeholder", "x");
        document.appendChild(root);
        root.appendChild(emptyInput);
        root.appendChild(filledInput);
        root.appendChild(noPlaceholder);
        root.appendChild(emptyTextarea);
        root.appendChild(plainDiv);

        assertTrue(matches(":placeholder-shown", emptyInput));
        assertTrue(matches("input:placeholder-shown", emptyInput));
        assertFalse(matches(":placeholder-shown", filledInput));
        assertFalse(matches(":placeholder-shown", noPlaceholder));
        assertTrue(matches(":placeholder-shown", emptyTextarea));
        assertFalse(matches(":placeholder-shown", plainDiv));
        assertTrue(matches("input:not(:placeholder-shown)", filledInput));
        assertFalse(matches("input:not(:placeholder-shown)", emptyInput));
    }

    private boolean matches(String selector, Element element) {
        return new SelectorParser().parse(selector).selectors().getFirst()
                .matches(element, DomSelectorAdapter.INSTANCE);
    }
}

package com.browicy.engine.dom;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RangeTest {

    @Test
    public void newRangeStartsCollapsedAtDocumentAndCanSelectNodes() {
        Document document = new Document("about:test");
        Element root = document.createElement("root");
        Element first = document.createElement("first");
        Element second = document.createElement("second");
        document.appendChild(root);
        root.appendChild(first);
        root.appendChild(second);

        Range range = document.createRange();
        assertTrue(range.isCollapsed());
        assertSame(document, range.getCommonAncestorContainer());
        assertSame(document, range.getStartContainer());
        assertEquals(0, range.getStartOffset());

        range.selectNode(second);
        assertFalse(range.isCollapsed());
        assertSame(root, range.getStartContainer());
        assertEquals(1, range.getStartOffset());
        assertEquals(2, range.getEndOffset());

        range.selectNodeContents(root);
        assertEquals(0, range.getStartOffset());
        assertEquals(2, range.getEndOffset());
        range.collapse(false);
        assertTrue(range.isCollapsed());
        assertEquals(2, range.getStartOffset());
    }

    @Test
    public void extractContentsPreservesFullySelectedNodeIdentityAndClonesPartialAncestors() {
        Document document = new Document("about:test");
        Element body = document.createElement("body");
        Element heading = document.createElement("h1");
        TextNode hello = document.createTextNode("Hello ");
        Element emphasis = document.createElement("em");
        TextNode wonderful = document.createTextNode("Wonderful");
        TextNode kitty = document.createTextNode(" Kitty");
        Element paragraph = document.createElement("p");
        TextNode question = document.createTextNode("How are you?");

        document.appendChild(body);
        body.appendChild(heading);
        heading.appendChild(hello);
        heading.appendChild(emphasis);
        emphasis.appendChild(wonderful);
        heading.appendChild(kitty);
        body.appendChild(paragraph);
        paragraph.appendChild(question);

        Range range = document.createRange();
        range.setStart(wonderful, 6);
        range.setEnd(paragraph, 0);
        assertEquals("ful Kitty", range.toString());

        DocumentFragment fragment = range.extractContents();

        assertEquals(2, fragment.getChildren().size());
        Element clonedHeading = (Element) fragment.getChildren().get(0);
        Element clonedEmphasis = (Element) clonedHeading.getChildren().get(0);
        assertEquals("ful", clonedEmphasis.getTextContent());
        assertSame(kitty, clonedHeading.getChildren().get(1));
        assertEquals("Wonder", wonderful.getData());
        assertEquals("Hello Wonder", heading.getTextContent());
        assertEquals("How are you?", paragraph.getTextContent());
        assertTrue(range.isCollapsed());
    }

    @Test
    public void insertionIntoTextSplitsNodeAndKeepsInsertedContentInsideRange() {
        Document document = new Document("about:test");
        Element paragraph = document.createElement("p");
        TextNode digits = document.createTextNode("12345");
        TextNode letters = document.createTextNode("ABCDE");
        document.appendChild(paragraph);
        paragraph.appendChild(digits);
        paragraph.appendChild(letters);

        Range range = document.createRange();
        range.setStart(digits, 2);
        range.setEnd(digits, 3);
        range.insertNode(letters);

        assertEquals(3, paragraph.getChildren().size());
        assertSame(digits, paragraph.getChildren().get(0));
        assertSame(letters, paragraph.getChildren().get(1));
        assertEquals("12", digits.getData());
        assertEquals("ABCDE", letters.getData());
        assertEquals("345", ((TextNode) paragraph.getChildren().get(2)).getData());
        assertTrue(range.toString().startsWith("ABCDE"));
    }

    @Test
    public void liveBoundariesFollowInsertionsAndRemovedSubtrees() {
        Document document = new Document("about:test");
        Element body = document.createElement("body");
        Element paragraph = document.createElement("p");
        TextNode text = document.createTextNode("12345");
        document.appendChild(body);
        body.appendChild(paragraph);
        paragraph.appendChild(text);

        Range range = document.createRange();
        range.setStart(text, 2);
        range.setEnd(body, 1);

        body.insertBefore(document.createElement("before"), paragraph);
        assertEquals(2, range.getEndOffset());

        body.removeChild(paragraph);
        assertTrue(range.isCollapsed());
        assertSame(body, range.getStartContainer());
        assertEquals(1, range.getStartOffset());
        assertSame(body, range.getEndContainer());
        assertEquals(1, range.getEndOffset());
    }

    @Test
    public void boundaryComparisonUsesTheRequestedEndpoints() {
        Document document = new Document("about:test");
        Element root = document.createElement("root");
        document.appendChild(root);

        Range first = document.createRange();
        first.setStart(root, 0);
        first.setEnd(root, 0);
        Range second = document.createRange();
        second.setStart(root, 0);
        root.appendChild(document.createElement("child"));
        second.setEnd(root, 1);

        assertEquals(-1, first.compareBoundaryPoints(Range.START_TO_END, second));
        assertEquals(0, first.compareBoundaryPoints(Range.END_TO_START, second));
        assertEquals(1, second.compareBoundaryPoints(Range.END_TO_START, first));
    }

    @Test
    public void stringificationOmitsCommentsAndDetachIsANoOp() {
        Document document = new Document("about:test");
        Element root = document.createElement("root");
        root.appendChild(document.createTextNode("before"));
        root.appendChild(document.createComment("not text"));
        root.appendChild(document.createTextNode("after"));
        document.appendChild(root);

        Range range = document.createRange();
        range.selectNodeContents(root);
        assertEquals("beforeafter", range.toString());

        range.detach();
        range.collapse(true);
        root.insertBefore(document.createElement("inserted"), root.getFirstChild());
        assertEquals(0, range.getStartOffset());
    }
}

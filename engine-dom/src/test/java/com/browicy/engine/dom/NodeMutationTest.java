package com.browicy.engine.dom;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NodeMutationTest {

    @Test
    public void appendInsertsNodesTextsAndResolvesDocumentFragments() {
        Document document = document();
        Element body = document.getBody();
        DocumentFragment fragment = document.createDocumentFragment();
        Element fragmentChild = document.createElement("span");
        fragment.appendChild(fragmentChild);
        List<DomMutation> mutations = new ArrayList<>();
        document.addMutationListener(mutations::add);

        body.append(document.createElement("div"), "Hello", null, fragment);

        List<Node> children = body.getChildren();
        assertEquals(3, children.size());
        assertEquals("div", ((Element) children.get(0)).getTagName());
        assertEquals("Hello", ((TextNode) children.get(1)).getData());
        assertSame(fragmentChild, children.get(2));
        assertFalse(fragment.hasChildNodes());

        assertEquals(1, mutations.size());
        DomMutation.ChildListChanged mutation = (DomMutation.ChildListChanged) mutations.get(0);
        assertSame(body, mutation.target());
        assertEquals(List.of(children.get(0), children.get(1), fragmentChild), mutation.addedNodes());
        assertTrue(mutation.removedNodes().isEmpty());
        assertNull(mutation.previousSibling());
        assertNull(mutation.nextSibling());
    }

    @Test
    public void prependInsertsNodesBeforeTheCurrentChildren() {
        Document document = document();
        Element body = document.getBody();
        Element existing = document.createElement("div");
        body.appendChild(existing);
        List<DomMutation> mutations = new ArrayList<>();
        document.addMutationListener(mutations::add);

        body.prepend("first", document.createElement("em"));

        List<Node> children = body.getChildren();
        assertEquals(3, children.size());
        assertEquals("first", ((TextNode) children.get(0)).getData());
        assertEquals("em", ((Element) children.get(1)).getTagName());
        assertSame(existing, children.get(2));

        DomMutation.ChildListChanged mutation = (DomMutation.ChildListChanged) mutations.get(0);
        assertEquals(List.of(children.get(0), children.get(1)), mutation.addedNodes());
        assertNull(mutation.previousSibling());
        assertSame(existing, mutation.nextSibling());
    }

    @Test
    public void replaceChildrenRemovesAllAndInsertsNewNodesInOneBatch() {
        Document document = document();
        Element body = document.getBody();
        Element first = document.createElement("span");
        Element second = document.createElement("em");
        body.appendChild(first);
        body.appendChild(second);
        List<DomMutation> mutations = new ArrayList<>();
        document.addMutationListener(mutations::add);

        Element div1 = document.createElement("div");
        Element div2 = document.createElement("div");
        body.replaceChildren(div1, "Text", div2);

        List<Node> children = body.getChildren();
        assertEquals(3, children.size());
        assertSame(div1, children.get(0));
        assertEquals("Text", ((TextNode) children.get(1)).getData());
        assertSame(div2, children.get(2));
        assertNull(first.getParent());
        assertNull(second.getParent());

        assertEquals(1, mutations.size());
        DomMutation.ChildListChanged mutation = (DomMutation.ChildListChanged) mutations.get(0);
        assertEquals(List.of(div1, children.get(1), div2), mutation.addedNodes());
        assertEquals(List.of(first, second), mutation.removedNodes());
        assertSame(second, mutation.previousSibling());
        assertNull(mutation.nextSibling());
    }

    @Test
    public void replaceChildrenWithFragmentMovesTheFragmentChildren() {
        Document document = document();
        Element body = document.getBody();
        body.appendChild(document.createElement("div"));
        DocumentFragment fragment = document.createDocumentFragment();
        Element content = document.createElement("p");
        fragment.appendChild(content);

        body.replaceChildren(fragment);

        List<Node> children = body.getChildren();
        assertEquals(1, children.size());
        assertSame(content, children.get(0));
        assertFalse(fragment.hasChildNodes());
    }

    @Test
    public void removeDetachesTheNodeAndFiresChildListMutation() {
        Document document = document();
        Element body = document.getBody();
        Element target = document.createElement("div");
        Element sibling = document.createElement("em");
        body.appendChild(target);
        body.appendChild(sibling);
        List<DomMutation> mutations = new ArrayList<>();
        document.addMutationListener(mutations::add);

        target.remove();

        assertNull(target.getParent());
        assertSame(sibling, body.getFirstChild());
        assertTrue(body.getChildren().size() == 1);

        DomMutation.ChildListChanged mutation = (DomMutation.ChildListChanged) mutations.get(0);
        assertEquals(List.of(target), mutation.removedNodes());
        assertTrue(mutation.addedNodes().isEmpty());
        assertNull(mutation.previousSibling());
        assertSame(sibling, mutation.nextSibling());

        target.remove();
        assertEquals(1, mutations.size());
    }

    @Test
    public void beforeAndAfterInsertNodesRelativeToTheNode() {
        Document document = document();
        Element body = document.getBody();
        Element anchor = document.createElement("div");
        Element tail = document.createElement("em");
        body.appendChild(anchor);
        body.appendChild(tail);
        List<DomMutation> mutations = new ArrayList<>();
        document.addMutationListener(mutations::add);

        anchor.before("head");
        anchor.after(document.createElement("p"), null);

        List<Node> children = body.getChildren();
        assertEquals(4, children.size());
        assertEquals("head", ((TextNode) children.get(0)).getData());
        assertSame(anchor, children.get(1));
        assertEquals("p", ((Element) children.get(2)).getTagName());
        assertSame(tail, children.get(3));

        DomMutation.ChildListChanged before = (DomMutation.ChildListChanged) mutations.get(0);
        assertEquals(List.of(children.get(0)), before.addedNodes());
        assertNull(before.previousSibling());
        assertSame(anchor, before.nextSibling());

        DomMutation.ChildListChanged after = (DomMutation.ChildListChanged) mutations.get(1);
        assertEquals(List.of(children.get(2)), after.addedNodes());
        assertSame(anchor, after.previousSibling());
        assertSame(tail, after.nextSibling());
    }

    @Test
    public void replaceWithReplacesTheNodeAndFiresOneBatchRecord() {
        Document document = document();
        Element body = document.getBody();
        Element old = document.createElement("div");
        body.appendChild(old);
        List<DomMutation> mutations = new ArrayList<>();
        document.addMutationListener(mutations::add);

        Element replacement = document.createElement("section");
        old.replaceWith(replacement, "text");

        List<Node> children = body.getChildren();
        assertEquals(2, children.size());
        assertSame(replacement, children.get(0));
        assertNull(old.getParent());

        DomMutation.ChildListChanged mutation = (DomMutation.ChildListChanged) mutations.get(0);
        assertEquals(List.of(replacement, children.get(1)), mutation.addedNodes());
        assertEquals(List.of(old), mutation.removedNodes());
        assertNull(mutation.previousSibling());
        assertNull(mutation.nextSibling());
    }

    @Test
    public void mutationsOnDetachedNodesAreNoOps() {
        Document document = document();
        Element detached = document.createElement("div");
        List<DomMutation> mutations = new ArrayList<>();
        document.addMutationListener(mutations::add);

        detached.remove();
        detached.before(document.createElement("span"));
        detached.after(document.createElement("span"));
        detached.replaceWith(document.createElement("span"));

        assertTrue(mutations.isEmpty());
        assertTrue(detached.getChildren().isEmpty());
    }

    @Test
    public void hierarchyErrorsAreRaisedForAncestorInsertions() {
        Document document = document();
        Element body = document.getBody();
        Element child = document.createElement("div");
        body.appendChild(child);

        assertThrows(DomException.class, () -> body.append(body));
        assertThrows(DomException.class, () -> child.append(body));
        assertThrows(DomException.class, () -> body.replaceChildren(body));
        assertThrows(DomException.class, () -> body.prepend(document));
    }

    @Test
    public void documentRejectsInvalidReplaceChildrenSequencesAtomically() {
        Document document = new Document("about:test");
        Element html = document.createElement("html");
        document.appendChild(html);
        Element first = document.createElement("div");
        Element second = document.createElement("div");

        assertThrows(DomException.class, () -> document.replaceChildren(first, second));

        assertSame(html, document.getDocumentElement());
        assertNull(first.getParent());
        assertNull(second.getParent());
    }

    @Test
    public void rangesStayConsistentDuringBatchInsertions() {
        Document document = document();
        Element body = document.getBody();
        Element first = document.createElement("div");
        Element second = document.createElement("div");
        Element third = document.createElement("div");
        body.appendChild(first);
        body.appendChild(second);
        body.appendChild(third);
        Range range = document.createRange();
        range.selectNode(third);

        body.prepend(document.createElement("x"), document.createElement("y"));

        assertEquals(5, body.getChildren().size());
        assertEquals(body, range.getStartContainer());
        assertEquals(4, range.getStartOffset());
        assertEquals(5, range.getEndOffset());

        body.replaceChildren();

        assertTrue(range.isCollapsed());
        assertEquals(body, range.getStartContainer());
        assertEquals(0, range.getStartOffset());
    }

    private static Document document() {
        Document document = new Document("about:test");
        Element html = document.createElement("html");
        Element body = document.createElement("body");
        document.appendChild(html);
        html.appendChild(body);
        return document;
    }
}

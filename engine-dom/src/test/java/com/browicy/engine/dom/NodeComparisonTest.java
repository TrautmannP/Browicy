package com.browicy.engine.dom;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NodeComparisonTest {

    @Test
    public void compareDocumentPositionHandlesAncestorsAndSiblingOrder() {
        Element root = new Element("root");
        Element first = new Element("first");
        Element second = new Element("second");
        TextNode nested = new TextNode("text");
        root.appendChild(first);
        root.appendChild(second);
        first.appendChild(nested);

        assertEquals(Node.DOCUMENT_POSITION_FOLLOWING,
                first.compareDocumentPosition(second));
        assertEquals(Node.DOCUMENT_POSITION_PRECEDING,
                second.compareDocumentPosition(first));
        assertEquals(Node.DOCUMENT_POSITION_FOLLOWING | Node.DOCUMENT_POSITION_CONTAINED_BY,
                root.compareDocumentPosition(nested));
        assertEquals(Node.DOCUMENT_POSITION_PRECEDING | Node.DOCUMENT_POSITION_CONTAINS,
                nested.compareDocumentPosition(root));
        assertEquals(0, first.compareDocumentPosition(first));
    }

    @Test
    public void disconnectedNodesReturnDisconnectedAndImplementationSpecificBits() {
        Element first = new Element("first");
        Element second = new Element("second");

        short result = first.compareDocumentPosition(second);

        assertTrue((result & Node.DOCUMENT_POSITION_DISCONNECTED) != 0);
        assertTrue((result & Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC) != 0);
        assertTrue((result & (Node.DOCUMENT_POSITION_PRECEDING
                | Node.DOCUMENT_POSITION_FOLLOWING)) != 0);
    }

    @Test
    public void isSameNodeUsesIdentityAndIsEqualNodeUsesDeepStructure() {
        Element first = new Element("div", Map.of("class", "box"));
        first.appendChild(new TextNode("value"));
        Element equal = new Element("div", Map.of("class", "box"));
        equal.appendChild(new TextNode("value"));
        Element different = new Element("div", Map.of("class", "other"));
        different.appendChild(new TextNode("value"));

        assertTrue(first.isSameNode(first));
        assertFalse(first.isSameNode(equal));
        assertTrue(first.isEqualNode(equal));
        assertFalse(first.isEqualNode(different));
    }
}

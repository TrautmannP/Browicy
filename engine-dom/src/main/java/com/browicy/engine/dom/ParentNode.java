package com.browicy.engine.dom;

import java.util.List;

public sealed interface ParentNode permits Document, DocumentFragment, Element {

    default Element querySelector(String selectors) {
        return SelectorQueries.querySelector((Node) this, selectors);
    }

    default List<Element> querySelectorAll(String selectors) {
        return SelectorQueries.querySelectorAll((Node) this, selectors);
    }

    default void append(Object... nodesOrStrings) {
        Node node = (Node) this;
        node.appendNodes(Node.convertNodesIntoNode(node, nodesOrStrings));
    }

    default void prepend(Object... nodesOrStrings) {
        Node node = (Node) this;
        node.prependNodes(Node.convertNodesIntoNode(node, nodesOrStrings));
    }

    default void replaceChildren(Object... nodesOrStrings) {
        Node node = (Node) this;
        node.replaceAllChildren(Node.convertNodesIntoNode(node, nodesOrStrings));
    }
}

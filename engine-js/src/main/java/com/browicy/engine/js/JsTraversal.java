package com.browicy.engine.js;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import com.browicy.engine.dom.Node;
import org.graalvm.polyglot.Value;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
abstract class JsTraversal {
    static final int FILTER_ACCEPT = 1;
    static final int FILTER_REJECT = 2;
    static final int FILTER_SKIP = 3;

    final JsDocument document;
    final Node root;
    final long whatToShow;
    final Value filter;

    final int accept(Node node) {
        long bit = 1L << (node.getNodeType() - 1);
        if ((whatToShow & bit) == 0) return FILTER_SKIP;
        if (filter == null) return FILTER_ACCEPT;
        Value result = filter.canExecute()
                ? filter.execute(document.wrap(node))
                : filter.invokeMember("acceptNode", document.wrap(node));
        int decision = result.asInt();
        return decision == FILTER_ACCEPT || decision == FILTER_REJECT || decision == FILTER_SKIP
                ? decision : FILTER_SKIP;
    }

    final boolean insideRoot(Node node) {
        return node != null && root.contains(node);
    }

    static Node nextInTree(Node node, Node root) {
        if (node.getFirstChild() != null) return node.getFirstChild();
        while (node != null && node != root) {
            if (node.getNextSibling() != null) return node.getNextSibling();
            node = node.getParent();
        }
        return null;
    }

    static Node previousInTree(Node node, Node root) {
        if (node == root) return null;
        Node previous = node.getPreviousSibling();
        if (previous == null) return node.getParent();
        while (previous.getLastChild() != null) previous = previous.getLastChild();
        return previous;
    }
}

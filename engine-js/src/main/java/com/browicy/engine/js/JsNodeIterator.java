package com.browicy.engine.js;

import com.browicy.engine.dom.Node;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.List;

final class JsNodeIterator extends JsTraversal implements ProxyObject {
    private static final List<String> MEMBERS = List.of("root", "whatToShow", "filter",
            "referenceNode", "pointerBeforeReferenceNode", "nextNode", "previousNode", "detach");
    private Node reference;
    private boolean before = true;

    JsNodeIterator(JsDocument document, Node root, long whatToShow, Value filter) {
        super(document, root, whatToShow, filter);
        this.reference = root;
    }

    @Override public Object getMember(String key) {
        return switch (key) {
            case "root" -> document.wrap(root);
            case "whatToShow" -> whatToShow;
            case "filter" -> filter;
            case "referenceNode" -> document.wrap(reference);
            case "pointerBeforeReferenceNode" -> before;
            case "nextNode" -> (ProxyExecutable) args -> nextNode();
            case "previousNode" -> (ProxyExecutable) args -> previousNode();
            case "detach" -> (ProxyExecutable) args -> null;
            default -> null;
        };
    }

    private Object nextNode() {
        Node candidate = before ? reference : nextInTree(reference, root);
        before = false;
        while (candidate != null) {
            Node next = nextInTree(candidate, root);
            reference = candidate;
            if (accept(candidate) == FILTER_ACCEPT) return document.wrap(candidate);
            candidate = insideRoot(candidate) ? next : next;
        }
        return null;
    }

    private Object previousNode() {
        Node candidate = before ? previousInTree(reference, root) : reference;
        before = true;
        while (candidate != null) {
            Node previous = previousInTree(candidate, root);
            reference = candidate;
            if (accept(candidate) == FILTER_ACCEPT) return document.wrap(candidate);
            candidate = previous;
        }
        return null;
    }

    @Override public Object getMemberKeys() { return MEMBERS.toArray(); }
    @Override public boolean hasMember(String key) { return MEMBERS.contains(key); }
    @Override public void putMember(String key, Value value) { throw new UnsupportedOperationException(key); }
}

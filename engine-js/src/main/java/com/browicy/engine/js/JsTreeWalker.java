package com.browicy.engine.js;

import com.browicy.engine.dom.Node;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.List;

final class JsTreeWalker extends JsTraversal implements ProxyObject {
    private static final List<String> MEMBERS = List.of("root", "whatToShow", "filter", "currentNode",
            "parentNode", "firstChild", "lastChild", "previousSibling", "nextSibling", "previousNode", "nextNode");
    private Node current;

    JsTreeWalker(JsDocument document, Node root, long whatToShow, Value filter) {
        super(document, root, whatToShow, filter);
        current = root;
    }

    @Override public Object getMember(String key) {
        return switch (key) {
            case "root" -> document.wrap(root);
            case "whatToShow" -> whatToShow;
            case "filter" -> filter;
            case "currentNode" -> document.wrap(current);
            case "parentNode" -> (ProxyExecutable) args -> moveParent();
            case "firstChild" -> (ProxyExecutable) args -> moveChild(false);
            case "lastChild" -> (ProxyExecutable) args -> moveChild(true);
            case "previousSibling" -> (ProxyExecutable) args -> moveSibling(false);
            case "nextSibling" -> (ProxyExecutable) args -> moveSibling(true);
            case "previousNode" -> (ProxyExecutable) args -> moveLinear(false);
            case "nextNode" -> (ProxyExecutable) args -> moveLinear(true);
            default -> null;
        };
    }

    private Object moveParent() {
        Node node = current.getParent();
        while (node != null && insideRoot(node)) {
            if (accept(node) == FILTER_ACCEPT) return set(node);
            if (node == root) break;
            node = node.getParent();
        }
        return null;
    }

    private Object moveChild(boolean last) {
        List<Node> visible = visibleNodes();
        int currentIndex = visible.indexOf(current);
        Node found = null;
        for (Node node : visible) {
            if (node == current) continue;
            Node parent = visibleParent(node, visible);
            if (parent == current && (last || found == null)) found = node;
        }
        return found == null || currentIndex < 0 ? null : set(found);
    }

    private Object moveSibling(boolean next) {
        List<Node> visible = visibleNodes();
        Node parent = visibleParent(current, visible);
        if (parent == null) return null;
        List<Node> siblings = visible.stream().filter(node -> visibleParent(node, visible) == parent).toList();
        int index = siblings.indexOf(current) + (next ? 1 : -1);
        return index >= 0 && index < siblings.size() ? set(siblings.get(index)) : null;
    }

    private Object moveLinear(boolean next) {
        List<Node> visible = visibleNodes();
        int index = visible.indexOf(current) + (next ? 1 : -1);
        return index >= 0 && index < visible.size() ? set(visible.get(index)) : null;
    }

    private List<Node> visibleNodes() {
        List<Node> result = new ArrayList<>();
        collect(root, result);
        return result;
    }

    private void collect(Node node, List<Node> result) {
        int decision = accept(node);
        if (decision == FILTER_ACCEPT) result.add(node);
        if (decision == FILTER_REJECT) return;
        for (Node child : List.copyOf(node.getChildren())) collect(child, result);
    }

    private Node visibleParent(Node node, List<Node> visible) {
        for (Node parent = node.getParent(); parent != null && insideRoot(parent); parent = parent.getParent()) {
            if (visible.contains(parent)) return parent;
        }
        return null;
    }

    private Object set(Node node) { current = node; return document.wrap(node); }

    @Override public void putMember(String key, Value value) {
        if (!"currentNode".equals(key)) throw new UnsupportedOperationException(key);
        if (!value.isProxyObject() || !(value.asProxyObject() instanceof JsNodeLike wrapped)
                || !insideRoot(wrapped.unwrapNode())) throw new IllegalArgumentException("currentNode liegt außerhalb der Wurzel");
        current = wrapped.unwrapNode();
    }
    @Override public Object getMemberKeys() { return MEMBERS.toArray(); }
    @Override public boolean hasMember(String key) { return MEMBERS.contains(key); }
}

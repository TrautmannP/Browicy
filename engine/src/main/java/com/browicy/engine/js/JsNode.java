package com.browicy.engine.js;

import com.browicy.engine.dom.Node;
import com.browicy.engine.dom.TextNode;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.List;

final class JsNode implements ProxyObject {
    private static final List<String> MEMBERS = List.of(
            "data", "nodeName", "nodeType", "parentNode", "firstChild", "lastChild",
            "previousSibling", "nextSibling", "textContent");

    private final Node node;
    private final JsDocument document;

    JsNode(Node node, JsDocument document) {
        this.node = node;
        this.document = document;
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "data", "textContent" -> node instanceof TextNode text ? text.getData() : node.getTextContent();
            case "nodeName" -> node instanceof TextNode ? "#text" : "#document";
            case "nodeType" -> node instanceof TextNode ? 3 : 9;
            case "parentNode" -> document.wrap(node.getParent());
            case "firstChild" -> childAt(0);
            case "lastChild" -> childAt(node.getChildren().size() - 1);
            case "previousSibling" -> sibling(-1);
            case "nextSibling" -> sibling(1);
            default -> null;
        };
    }

    @Override
    public void putMember(String key, Value value) {
        if (("data".equals(key) || "textContent".equals(key)) && node instanceof TextNode text) {
            text.setData(value.isString() ? value.asString() : value.toString());
            return;
        }
        throw new UnsupportedOperationException("Eigenschaft nicht unterstützt: " + key);
    }

    private Object childAt(int index) {
        return index >= 0 && index < node.getChildren().size()
                ? document.wrap(node.getChildren().get(index)) : null;
    }

    private Object sibling(int offset) {
        if (node.getParent() == null) {
            return null;
        }
        List<Node> siblings = node.getParent().getChildren();
        int index = siblings.indexOf(node) + offset;
        return index >= 0 && index < siblings.size() ? document.wrap(siblings.get(index)) : null;
    }

    @Override
    public Object getMemberKeys() {
        return MEMBERS.toArray();
    }

    @Override
    public boolean hasMember(String key) {
        return MEMBERS.contains(key);
    }
}

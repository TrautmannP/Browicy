package com.browicy.engine.js;

import com.browicy.engine.dom.Node;
import com.browicy.engine.dom.TextNode;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.List;

final class JsNode implements ProxyObject, JsNodeLike {
    private static final List<String> MEMBERS = List.of(
            "data", "nodeName", "nodeType", "nodeValue", "parentNode", "childNodes", "firstChild", "lastChild",
            "previousSibling", "nextSibling", "textContent", "appendChild", "insertBefore",
            "replaceChild", "removeChild", "hasChildNodes", "contains",
            "ELEMENT_NODE", "TEXT_NODE", "COMMENT_NODE", "DOCUMENT_NODE", "DOCUMENT_TYPE_NODE", "DOCUMENT_FRAGMENT_NODE");

    private final Node node;
    private final JsDocument document;

    JsNode(Node node, JsDocument document) {
        this.node = node;
        this.document = document;
    }

    @Override public Node unwrapNode() { return node; }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "data" -> node.getNodeValue();
            case "textContent" -> node.getTextContent();
            case "nodeName" -> node.getNodeName();
            case "nodeType" -> node.getNodeType();
            case "nodeValue" -> node.getNodeValue();
            case "parentNode" -> document.wrap(node.getParent());
            case "childNodes" -> org.graalvm.polyglot.proxy.ProxyArray.fromList(node.getChildren().stream()
                    .map(document::wrap).toList());
            case "firstChild" -> childAt(0);
            case "lastChild" -> childAt(node.getChildren().size() - 1);
            case "previousSibling" -> sibling(-1);
            case "nextSibling" -> sibling(1);
            case "appendChild" -> (org.graalvm.polyglot.proxy.ProxyExecutable) args -> {
                JsNodeLike child = JsElement.expectNode(args, 0, false);
                node.appendChild(child.unwrapNode());
                return child;
            };
            case "insertBefore" -> (org.graalvm.polyglot.proxy.ProxyExecutable) args -> {
                JsNodeLike child = JsElement.expectNode(args, 0, false);
                JsNodeLike reference = JsElement.expectNode(args, 1, true);
                node.insertBefore(child.unwrapNode(), reference == null ? null : reference.unwrapNode());
                return child;
            };
            case "replaceChild" -> (org.graalvm.polyglot.proxy.ProxyExecutable) args -> {
                JsNodeLike replacement = JsElement.expectNode(args, 0, false);
                JsNodeLike oldChild = JsElement.expectNode(args, 1, false);
                node.replaceChild(replacement.unwrapNode(), oldChild.unwrapNode());
                return oldChild;
            };
            case "removeChild" -> (org.graalvm.polyglot.proxy.ProxyExecutable) args -> {
                JsNodeLike child = JsElement.expectNode(args, 0, false);
                node.removeChild(child.unwrapNode());
                return child;
            };
            case "hasChildNodes" -> (org.graalvm.polyglot.proxy.ProxyExecutable) args -> node.hasChildNodes();
            case "contains" -> (org.graalvm.polyglot.proxy.ProxyExecutable) args -> {
                JsNodeLike other = JsElement.expectNode(args, 0, true);
                return other != null && node.contains(other.unwrapNode());
            };
            case "ELEMENT_NODE" -> Node.ELEMENT_NODE;
            case "TEXT_NODE" -> Node.TEXT_NODE;
            case "COMMENT_NODE" -> Node.COMMENT_NODE;
            case "DOCUMENT_NODE" -> Node.DOCUMENT_NODE;
            case "DOCUMENT_TYPE_NODE" -> Node.DOCUMENT_TYPE_NODE;
            case "DOCUMENT_FRAGMENT_NODE" -> Node.DOCUMENT_FRAGMENT_NODE;
            default -> null;
        };
    }

    @Override
    public void putMember(String key, Value value) {
        if ("data".equals(key) || "nodeValue".equals(key)) {
            node.setNodeValue(value.isNull() ? null : value.isString() ? value.asString() : value.toString());
            return;
        }
        if ("textContent".equals(key)) {
            node.setTextContent(value.isNull() ? "" : value.isString() ? value.asString() : value.toString());
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

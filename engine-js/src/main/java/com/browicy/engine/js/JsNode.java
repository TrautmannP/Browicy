package com.browicy.engine.js;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import com.browicy.engine.dom.Node;
import com.browicy.engine.dom.ParentNode;
import com.browicy.engine.dom.DocumentType;
import com.browicy.engine.dom.TextNode;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class JsNode implements ProxyObject, JsNodeLike {
    private static final List<String> MEMBERS = List.of(
            "data", "name", "publicId", "systemId", "nodeName", "nodeType", "nodeValue", "parentNode", "ownerDocument", "childNodes", "firstChild", "lastChild",
            "previousSibling", "nextSibling", "textContent", "appendChild", "insertBefore",
            "replaceChild", "removeChild", "hasChildNodes", "contains",
            "compareDocumentPosition", "isSameNode", "isEqualNode", "cloneNode",
            JsEventTarget.ADD_EVENT_LISTENER, JsEventTarget.REMOVE_EVENT_LISTENER, JsEventTarget.DISPATCH_EVENT,
            "ELEMENT_NODE", "TEXT_NODE", "COMMENT_NODE", "DOCUMENT_NODE", "DOCUMENT_TYPE_NODE", "DOCUMENT_FRAGMENT_NODE",
            "DOCUMENT_POSITION_DISCONNECTED", "DOCUMENT_POSITION_PRECEDING", "DOCUMENT_POSITION_FOLLOWING",
            "DOCUMENT_POSITION_CONTAINS", "DOCUMENT_POSITION_CONTAINED_BY", "DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC");

    private final Node node;
    private final JsDocument document;

    @Override public Node unwrapNode() { return node; }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "data" -> node.getNodeValue();
            case "name" -> node instanceof DocumentType type ? type.getName() : null;
            case "publicId" -> node instanceof DocumentType type ? type.getPublicId() : null;
            case "systemId" -> node instanceof DocumentType type ? type.getSystemId() : null;
            case "textContent" -> node.getTextContent();
            case "nodeName" -> node.getNodeName();
            case "nodeType" -> node.getNodeType();
            case "nodeValue" -> node.getNodeValue();
            case "parentNode" -> document.wrap(node.getParent());
            case "ownerDocument" -> document.wrapOwnerDocument(node);
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
            case "compareDocumentPosition" -> (org.graalvm.polyglot.proxy.ProxyExecutable) args ->
                    node.compareDocumentPosition(JsElement.expectNode(args, 0, false).unwrapNode());
            case "isSameNode" -> (org.graalvm.polyglot.proxy.ProxyExecutable) args -> {
                JsNodeLike other = JsElement.expectNode(args, 0, true);
                return other != null && node.isSameNode(other.unwrapNode());
            };
            case "isEqualNode" -> (org.graalvm.polyglot.proxy.ProxyExecutable) args -> {
                JsNodeLike other = JsElement.expectNode(args, 0, true);
                return other != null && node.isEqualNode(other.unwrapNode());
            };
            case "cloneNode" -> (org.graalvm.polyglot.proxy.ProxyExecutable) args -> document.wrap(
                    node.cloneNode(args.length > 0 && args[0].asBoolean()));
            case "querySelector" -> node instanceof ParentNode parent
                    ? document.domOperation((org.graalvm.polyglot.proxy.ProxyExecutable) args ->
                            document.wrap(parent.querySelector(text(args, 0))))
                    : null;
            case "querySelectorAll" -> node instanceof ParentNode parent
                    ? document.domOperation((org.graalvm.polyglot.proxy.ProxyExecutable) args ->
                            new JsNodeList(parent.querySelectorAll(text(args, 0)), document))
                    : null;
            case JsEventTarget.ADD_EVENT_LISTENER -> JsEventTarget.addEventListener(node, document);
            case JsEventTarget.REMOVE_EVENT_LISTENER -> JsEventTarget.removeEventListener(node, document);
            case JsEventTarget.DISPATCH_EVENT -> JsEventTarget.dispatchEvent(node);
            case "ELEMENT_NODE" -> Node.ELEMENT_NODE;
            case "TEXT_NODE" -> Node.TEXT_NODE;
            case "COMMENT_NODE" -> Node.COMMENT_NODE;
            case "DOCUMENT_NODE" -> Node.DOCUMENT_NODE;
            case "DOCUMENT_TYPE_NODE" -> Node.DOCUMENT_TYPE_NODE;
            case "DOCUMENT_FRAGMENT_NODE" -> Node.DOCUMENT_FRAGMENT_NODE;
            case "DOCUMENT_POSITION_DISCONNECTED" -> Node.DOCUMENT_POSITION_DISCONNECTED;
            case "DOCUMENT_POSITION_PRECEDING" -> Node.DOCUMENT_POSITION_PRECEDING;
            case "DOCUMENT_POSITION_FOLLOWING" -> Node.DOCUMENT_POSITION_FOLLOWING;
            case "DOCUMENT_POSITION_CONTAINS" -> Node.DOCUMENT_POSITION_CONTAINS;
            case "DOCUMENT_POSITION_CONTAINED_BY" -> Node.DOCUMENT_POSITION_CONTAINED_BY;
            case "DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC" -> Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC;
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
        if (!(node instanceof ParentNode)) {
            return MEMBERS.toArray();
        }
        List<String> keys = new java.util.ArrayList<>(MEMBERS);
        keys.add("querySelector");
        keys.add("querySelectorAll");
        return keys.toArray();
    }

    @Override
    public boolean hasMember(String key) {
        return MEMBERS.contains(key)
                || node instanceof ParentNode && ("querySelector".equals(key) || "querySelectorAll".equals(key));
    }

    private static String text(Value[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Argument " + index + " fehlt");
        }
        Value value = args[index];
        return value.isNull() ? "null" : value.isString() ? value.asString() : value.toString();
    }
}

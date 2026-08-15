package com.browicy.engine.js.handlers;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import com.browicy.engine.js.JsDocument;
import com.browicy.engine.js.JsElement;
import com.browicy.engine.js.JsNodeLike;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.util.List;
import java.util.stream.Collectors;

import static com.browicy.engine.js.handlers.JsMemberHandler.expectNode;

public final class JsNodeHandler implements JsMemberHandler {

    private static final List<String> KEYS = List.of(
            "tagName", "nodeName", "nodeType", "namespaceURI", "prefix", "localName",
            "parentNode", "ownerDocument", "isConnected",
            "firstChild", "lastChild", "previousSibling", "nextSibling",
            "childNodes", "children",
            "contains", "compareDocumentPosition", "isSameNode", "isEqualNode",
            "cloneNode", "hasChildNodes");

    @Override
    public List<String> keys() {
        return KEYS;
    }

    @Override
    public boolean canHandle(String key) {
        return KEYS.contains(key);
    }

    @Override
    public Object get(String key, JsElement element, JsDocument doc) {
        Element el = element.unwrap();
        return switch (key) {
            case "tagName", "nodeName" -> el.getNodeName();
            case "nodeType" -> el.getNodeType();
            case "namespaceURI" -> el.getNamespaceUri();
            case "prefix" -> el.getPrefix();
            case "localName" -> el.getLocalName();
            case "parentNode" -> doc.wrap(el.getParent());
            case "ownerDocument" -> doc.wrapOwnerDocument(el);
            case "isConnected" -> isConnected(el);
            case "firstChild" -> childAt(el, doc, 0);
            case "lastChild" -> childAt(el, doc, el.getChildren().size() - 1);
            case "previousSibling" -> sibling(el, doc, -1);
            case "nextSibling" -> sibling(el, doc, 1);
            case "childNodes" -> ProxyArray.fromList(el.getChildren().stream()
                    .map(doc::wrap).collect(Collectors.toList()));
            case "children" -> doc.htmlCollection(el::getChildElements);
            case "contains" -> (ProxyExecutable) args -> {
                JsNodeLike other = expectNode(args, 0, true);
                return other != null && el.contains(other.unwrapNode());
            };
            case "compareDocumentPosition" -> (ProxyExecutable) args ->
                    el.compareDocumentPosition(expectNode(args, 0, false).unwrapNode());
            case "isSameNode" -> (ProxyExecutable) args -> {
                JsNodeLike other = expectNode(args, 0, true);
                return other != null && el.isSameNode(other.unwrapNode());
            };
            case "isEqualNode" -> (ProxyExecutable) args -> {
                JsNodeLike other = expectNode(args, 0, true);
                return other != null && el.isEqualNode(other.unwrapNode());
            };
            case "cloneNode" -> (ProxyExecutable) args ->
                    doc.wrap(el.cloneNode(args.length > 0 && args[0].asBoolean()));
            case "hasChildNodes" -> (ProxyExecutable) args -> el.hasChildNodes();
            default -> null;
        };
    }

    @Override
    public boolean set(String key, Value value, JsElement element, JsDocument doc) {
        return false;
    }

    private static boolean isConnected(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof Document) {
                return true;
            }
        }
        return false;
    }

    private static Object childAt(Element element, JsDocument doc, int index) {
        return index >= 0 && index < element.getChildren().size()
                ? doc.wrap(element.getChildren().get(index)) : null;
    }

    private static Object sibling(Element element, JsDocument doc, int offset) {
        if (element.getParent() == null) {
            return null;
        }
        List<Node> siblings = element.getParent().getChildren();
        int index = siblings.indexOf(element) + offset;
        return index >= 0 && index < siblings.size() ? doc.wrap(siblings.get(index)) : null;
    }
}

package com.browicy.engine.js.handlers;

import com.browicy.engine.dom.Node;
import com.browicy.engine.js.JsDocument;
import com.browicy.engine.js.JsElement;
import com.browicy.engine.js.JsNodeLike;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.util.List;

import static com.browicy.engine.js.handlers.JsMemberHandler.expectNode;

public final class JsDocumentTraversalHandler implements JsMemberHandler {

    private static final List<String> KEYS = List.of(
            "childNodes", "firstChild", "lastChild", "hasChildNodes",
            "appendChild", "insertBefore", "replaceChild", "removeChild",
            "compareDocumentPosition", "isSameNode", "isEqualNode");

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
        Node document = doc.unwrapNode();
        return switch (key) {
            case "childNodes" -> ProxyArray.fromList(
                    document.getChildren().stream().map(doc::wrap).toList());
            case "firstChild" -> doc.wrap(document.getFirstChild());
            case "lastChild" -> doc.wrap(document.getLastChild());
            case "hasChildNodes" -> (ProxyExecutable) args -> document.hasChildNodes();
            case "appendChild" -> doc.domOperation((ProxyExecutable) args -> {
                JsNodeLike child = expectNode(args, 0, false);
                document.appendChild(child.unwrapNode());
                return child;
            });
            case "insertBefore" -> doc.domOperation((ProxyExecutable) args -> {
                JsNodeLike child = expectNode(args, 0, false);
                JsNodeLike reference = expectNode(args, 1, true);
                document.insertBefore(child.unwrapNode(),
                        reference == null ? null : reference.unwrapNode());
                return child;
            });
            case "replaceChild" -> doc.domOperation((ProxyExecutable) args -> {
                JsNodeLike replacement = expectNode(args, 0, false);
                JsNodeLike oldChild = expectNode(args, 1, false);
                document.replaceChild(replacement.unwrapNode(), oldChild.unwrapNode());
                return oldChild;
            });
            case "removeChild" -> doc.domOperation((ProxyExecutable) args -> {
                JsNodeLike child = expectNode(args, 0, false);
                document.removeChild(child.unwrapNode());
                return child;
            });
            case "compareDocumentPosition" -> (ProxyExecutable) args ->
                    document.compareDocumentPosition(expectNode(args, 0, false).unwrapNode());
            case "isSameNode" -> (ProxyExecutable) args -> {
                JsNodeLike other = expectNode(args, 0, true);
                return other != null && document.isSameNode(other.unwrapNode());
            };
            case "isEqualNode" -> (ProxyExecutable) args -> {
                JsNodeLike other = expectNode(args, 0, true);
                return other != null && document.isEqualNode(other.unwrapNode());
            };
            default -> null;
        };
    }

    @Override
    public boolean set(String key, Value value, JsElement element, JsDocument doc) {
        return false;
    }
}

package com.browicy.engine.js.handlers;

import com.browicy.engine.dom.Element;
import com.browicy.engine.js.JsDocument;
import com.browicy.engine.js.JsElement;
import com.browicy.engine.js.JsNodeLike;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.util.List;

import static com.browicy.engine.js.handlers.JsMemberHandler.expectNode;
import static com.browicy.engine.js.handlers.JsMemberHandler.nodesOrStrings;

public final class JsMutationHandler implements JsMemberHandler {

    private static final List<String> KEYS = List.of(
            "append", "prepend", "replaceChildren", "before", "after", "replaceWith",
            "appendChild", "insertBefore", "replaceChild", "removeChild");

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
            case "append" -> (ProxyExecutable) args -> {
                el.append(nodesOrStrings(args));
                element.styleContentMaybeChanged();
                return null;
            };
            case "prepend" -> (ProxyExecutable) args -> {
                el.prepend(nodesOrStrings(args));
                element.styleContentMaybeChanged();
                return null;
            };
            case "replaceChildren" -> (ProxyExecutable) args -> {
                el.replaceChildren(nodesOrStrings(args));
                element.styleContentMaybeChanged();
                return null;
            };
            case "before" -> (ProxyExecutable) args -> {
                el.before(nodesOrStrings(args));
                element.styleContentMaybeChanged();
                return null;
            };
            case "after" -> (ProxyExecutable) args -> {
                el.after(nodesOrStrings(args));
                element.styleContentMaybeChanged();
                return null;
            };
            case "replaceWith" -> (ProxyExecutable) args -> {
                el.replaceWith(nodesOrStrings(args));
                element.styleContentMaybeChanged();
                return null;
            };
            case "appendChild" -> (ProxyExecutable) args -> {
                JsNodeLike child = expectNode(args, 0, false);
                el.appendChild(child.unwrapNode());
                element.styleContentMaybeChanged();
                return child;
            };
            case "insertBefore" -> (ProxyExecutable) args -> {
                JsNodeLike child = expectNode(args, 0, false);
                JsNodeLike reference = expectNode(args, 1, true);
                el.insertBefore(child.unwrapNode(), reference == null ? null : reference.unwrapNode());
                element.styleContentMaybeChanged();
                return child;
            };
            case "replaceChild" -> (ProxyExecutable) args -> {
                JsNodeLike replacement = expectNode(args, 0, false);
                JsNodeLike oldChild = expectNode(args, 1, false);
                el.replaceChild(replacement.unwrapNode(), oldChild.unwrapNode());
                element.styleContentMaybeChanged();
                return oldChild;
            };
            case "removeChild" -> (ProxyExecutable) args -> {
                JsNodeLike child = expectNode(args, 0, false);
                el.removeChild(child.unwrapNode());
                element.styleContentMaybeChanged();
                return child;
            };
            default -> null;
        };
    }

    @Override
    public boolean set(String key, Value value, JsElement element, JsDocument doc) {
        return false;
    }
}

package com.browicy.engine.js.handlers;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import com.browicy.engine.js.JsDocument;
import com.browicy.engine.js.JsElement;
import com.browicy.engine.js.JsNodeList;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.util.List;

import static com.browicy.engine.js.handlers.JsMemberHandler.asString;

public final class JsQueryHandler implements JsMemberHandler {

    private static final List<String> KEYS = List.of(
            "querySelector", "querySelectorAll", "matches", "closest");

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
            case "querySelector" -> doc.domOperation((ProxyExecutable) args ->
                    doc.wrap(el.querySelector(asString(args, 0))));
            case "querySelectorAll" -> doc.domOperation((ProxyExecutable) args ->
                    doc.nodeList(el.querySelectorAll(asString(args, 0))));
            case "matches" -> doc.domOperation((ProxyExecutable) args ->
                    matchesSelector(el, asString(args, 0)));
            case "closest" -> doc.domOperation((ProxyExecutable) args -> {
                String selector = asString(args, 0);
                for (Node candidate = el; candidate instanceof Element ancestor;
                     candidate = candidate.getParent()) {
                    if (matchesSelector(ancestor, selector)) {
                        return doc.wrap(ancestor);
                    }
                }
                return null;
            });
            default -> null;
        };
    }

    @Override
    public boolean set(String key, Value value, JsElement element, JsDocument doc) {
        return false;
    }

    private static boolean matchesSelector(Element candidate, String selector) {
        Document owner = candidate.getOwnerDocument();
        return owner != null && owner.querySelectorAll(selector).contains(candidate);
    }
}

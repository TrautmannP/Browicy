package com.browicy.engine.js;

import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.List;

/** A same-origin window facade for an embedded document browsing context. */
final class JsWindow implements ProxyObject {

    private static final List<String> MEMBERS = List.of(
            "document", "window", "self", "getComputedStyle");

    private final JsDocument document;

    JsWindow(JsDocument document) {
        this.document = document;
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "document" -> document;
            case "window", "self" -> this;
            case "getComputedStyle" -> (ProxyExecutable) args -> {
                if (args.length == 0 || !args[0].isProxyObject()
                        || !(args[0].asProxyObject() instanceof JsElement element)) {
                    throw new IllegalArgumentException(
                            "getComputedStyle: argument 1 must be an Element");
                }
                return document.computedStyle(element);
            };
            default -> null;
        };
    }

    @Override public Object getMemberKeys() { return MEMBERS.toArray(); }
    @Override public boolean hasMember(String key) { return MEMBERS.contains(key); }
    @Override public void putMember(String key, org.graalvm.polyglot.Value value) { }
    @Override public String toString() { return "[object Window]"; }
}

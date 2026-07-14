package com.browicy.engine.js;

import java.util.List;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

final class JsCssRule implements ProxyObject {

    private static final List<String> MEMBERS = List.of(
            "cssText", "selectorText", "type", "parentRule", "parentStyleSheet");

    private final String cssText;
    private final JsCssStyleSheet parentStyleSheet;

    JsCssRule(String cssText, JsCssStyleSheet parentStyleSheet) {
        this.cssText = cssText;
        this.parentStyleSheet = parentStyleSheet;
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "cssText" -> cssText;
            case "selectorText" -> cssText.substring(0, cssText.indexOf('{')).strip();
            case "type" -> 1;
            case "parentRule" -> null;
            case "parentStyleSheet" -> parentStyleSheet;
            default -> null;
        };
    }

    @Override public Object getMemberKeys() { return MEMBERS.toArray(); }
    @Override public boolean hasMember(String key) { return MEMBERS.contains(key); }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("CSSRule ist schreibgeschuetzt");
    }

    @Override public String toString() { return "[object CSSStyleRule]"; }
}

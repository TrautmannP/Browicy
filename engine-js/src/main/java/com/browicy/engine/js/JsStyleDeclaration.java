package com.browicy.engine.js;

import com.browicy.engine.css.CssParser;
import com.browicy.engine.dom.Element;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Minimal live CSSStyleDeclaration backed by an element's style attribute. */
final class JsStyleDeclaration implements ProxyObject {

    private static final CssParser PARSER = new CssParser();
    private static final List<String> METHODS = List.of(
            "cssText", "length", "setProperty", "getPropertyValue", "removeProperty", "item");

    private final Element element;

    JsStyleDeclaration(Element element) {
        this.element = element;
    }

    void setCssText(String cssText) {
        write(PARSER.parseDeclarations(cssText));
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "cssText" -> cssText();
            case "length" -> declarations().size();
            case "setProperty" -> (ProxyExecutable) args -> {
                setProperty(text(args, 0), text(args, 1));
                return null;
            };
            case "getPropertyValue" -> (ProxyExecutable) args ->
                    declarations().getOrDefault(normalizeProperty(text(args, 0)), "");
            case "removeProperty" -> (ProxyExecutable) args -> removeProperty(text(args, 0));
            case "item" -> (ProxyExecutable) args -> {
                int index = args.length == 0 ? -1 : args[0].asInt();
                List<String> names = new ArrayList<>(declarations().keySet());
                return index >= 0 && index < names.size() ? names.get(index) : "";
            };
            default -> declarations().getOrDefault(normalizeProperty(key), "");
        };
    }

    @Override
    public void putMember(String key, Value value) {
        if ("cssText".equals(key)) {
            setCssText(value.isString() ? value.asString() : value.toString());
            return;
        }
        setProperty(normalizeProperty(key), value.isString() ? value.asString() : value.toString());
    }

    @Override
    public Object getMemberKeys() {
        List<String> keys = new ArrayList<>(METHODS);
        for (String property : declarations().keySet()) {
            keys.add(toCamelCase(property));
        }
        return keys.toArray();
    }

    @Override
    public boolean hasMember(String key) {
        return METHODS.contains(key) || PARSER.supportsProperty(normalizeProperty(key));
    }

    private void setProperty(String property, String value) {
        property = normalizeProperty(property);
        Map<String, String> accepted = PARSER.parseDeclarations(property + ":" + value);
        if (accepted.isEmpty()) {
            return;
        }
        Map<String, String> current = declarations();
        current.putAll(accepted);
        write(current);
    }

    private String removeProperty(String property) {
        Map<String, String> current = declarations();
        String old = current.remove(normalizeProperty(property));
        write(current);
        return old == null ? "" : old;
    }

    private Map<String, String> declarations() {
        return new LinkedHashMap<>(PARSER.parseDeclarations(element.getAttribute("style")));
    }

    private String cssText() {
        StringBuilder result = new StringBuilder();
        declarations().forEach((name, value) -> result.append(name).append(": ")
                .append(value).append("; "));
        return result.toString().stripTrailing();
    }

    private void write(Map<String, String> declarations) {
        if (declarations.isEmpty()) {
            element.removeAttribute("style");
            return;
        }
        StringBuilder css = new StringBuilder();
        declarations.forEach((name, value) -> css.append(name).append(':').append(value).append(';'));
        element.setAttribute("style", css.toString());
    }

    private static String normalizeProperty(String property) {
        if (property == null) return "";
        StringBuilder normalized = new StringBuilder();
        for (int index = 0; index < property.length(); index++) {
            char character = property.charAt(index);
            if (Character.isUpperCase(character)) {
                normalized.append('-').append(Character.toLowerCase(character));
            } else {
                normalized.append(character);
            }
        }
        return normalized.toString().strip().toLowerCase(Locale.ROOT);
    }

    private static String toCamelCase(String property) {
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (char character : property.toCharArray()) {
            if (character == '-') {
                upper = true;
            } else {
                result.append(upper ? Character.toUpperCase(character) : character);
                upper = false;
            }
        }
        return result.toString();
    }

    private static String text(Value[] args, int index) {
        if (index >= args.length) return "";
        return args[index].isString() ? args[index].asString() : args[index].toString();
    }
}

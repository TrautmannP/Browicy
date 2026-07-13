package com.browicy.engine.js;

import com.browicy.engine.dom.Element;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class JsComputedStyleDeclaration implements ProxyObject {

    private static final List<String> MEMBERS = List.of(
            "cssText", "length", "getPropertyValue", "getPropertyPriority", "item", "parentRule");

    private final Element element;

    JsComputedStyleDeclaration(Element element) {
        this.element = element;
    }

    @Override
    public Object getMember(String key) {
        Map<String, String> declarations = element.getComputedStyles();
        return switch (key) {
            case "cssText" -> cssText(declarations);
            case "length" -> declarations.size();
            case "getPropertyValue" -> (ProxyExecutable) args ->
                    declarations.getOrDefault(normalizeProperty(text(args, 0)), "");
            case "getPropertyPriority" -> (ProxyExecutable) args -> "";
            case "item" -> (ProxyExecutable) args -> item(declarations, index(args));
            case "parentRule" -> null;
            default -> {
                Integer index = numericIndex(key);
                yield index == null
                        ? declarations.getOrDefault(normalizeProperty(key), "")
                        : item(declarations, index);
            }
        };
    }

    @Override
    public void putMember(String key, Value value) {
    }

    @Override
    public Object getMemberKeys() {
        Map<String, String> declarations = element.getComputedStyles();
        List<String> keys = new ArrayList<>(MEMBERS);
        int index = 0;
        for (String property : declarations.keySet()) {
            keys.add(Integer.toString(index++));
            keys.add(toCamelCase(property));
        }
        return keys.toArray();
    }

    @Override
    public boolean hasMember(String key) {
        if (MEMBERS.contains(key)) return true;
        Integer index = numericIndex(key);
        if (index != null) return index >= 0 && index < element.getComputedStyles().size();
        return element.getComputedStyles().containsKey(normalizeProperty(key));
    }

    private static String item(Map<String, String> declarations, int index) {
        if (index < 0 || index >= declarations.size()) return "";
        return new ArrayList<>(declarations.keySet()).get(index);
    }

    private static int index(Value[] args) {
        return args.length == 0 || !args[0].fitsInInt() ? -1 : args[0].asInt();
    }

    private static Integer numericIndex(String key) {
        try {
            return Integer.valueOf(key);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String cssText(Map<String, String> declarations) {
        StringBuilder result = new StringBuilder();
        declarations.forEach((name, value) -> result.append(name).append(": ")
                .append(value).append("; "));
        return result.toString().stripTrailing();
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

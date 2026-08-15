package com.browicy.engine.js;

import com.browicy.engine.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

public final class JsDomStringMap implements ProxyObject {

    private static final String PREFIX = "data-";

    private final Element element;

    JsDomStringMap(Element element) {
        this.element = element;
    }

    private static String toKebab(String name) {
        StringBuilder result = new StringBuilder(name.length() + 8);
        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (Character.isUpperCase(character)) {
                result.append('-').append(Character.toLowerCase(character));
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static String toCamel(String name) {
        StringBuilder result = new StringBuilder(name.length());
        boolean upperNext = false;
        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (character == '-') {
                upperNext = true;
            } else {
                result.append(upperNext ? Character.toUpperCase(character) : character);
                upperNext = false;
            }
        }
        return result.toString();
    }

    @Override
    public Object getMember(String key) {
        return element.getAttribute(PREFIX + toKebab(key));
    }

    @Override
    public Object getMemberKeys() {
        List<String> keys = new ArrayList<>();
        for (String name : element.getAttributeNames()) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith(PREFIX)) {
                keys.add(toCamel(lower.substring(PREFIX.length())));
            }
        }
        return ProxyArray.fromArray(keys.toArray());
    }

    @Override
    public boolean hasMember(String key) {
        return element.hasAttribute(PREFIX + toKebab(key));
    }

    @Override
    public void putMember(String key, Value value) {
        String text = value == null || value.isNull() ? ""
                : value.isString() ? value.asString() : value.toString();
        element.setAttribute(PREFIX + toKebab(key), text);
    }
}

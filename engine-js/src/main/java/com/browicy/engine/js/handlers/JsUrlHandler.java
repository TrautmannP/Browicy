package com.browicy.engine.js.handlers;

import com.browicy.engine.dom.Element;
import com.browicy.engine.js.GraalPageRuntime;
import com.browicy.engine.js.JsDocument;
import com.browicy.engine.js.JsElement;
import org.graalvm.polyglot.Value;

import java.util.List;

import static com.browicy.engine.js.handlers.JsMemberHandler.orEmpty;
import static com.browicy.engine.js.handlers.JsMemberHandler.tag;
import static com.browicy.engine.js.handlers.JsMemberHandler.toText;

public final class JsUrlHandler implements JsMemberHandler {

    private static final List<String> KEYS = List.of(
            "href", "protocol", "host", "hostname", "port",
            "pathname", "search", "hash", "origin", "src");

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
            case "href" -> isUrlElement(el) ? resolvedUrl(el) : orEmpty(el.getAttribute("href"));
            case "src" -> reflectedUrl(el, "src");
            case "protocol", "host", "hostname", "port", "pathname", "search", "hash", "origin" -> urlPart(el, key);
            default -> null;
        };
    }

    @Override
    public boolean set(String key, Value value, JsElement element, JsDocument doc) {
        Element el = element.unwrap();
        if (!"href".equals(key) && !"src".equals(key)) {
            return false;
        }
        el.setAttribute(key, toText(value));
        return true;
    }

    private static String resolvedUrl(Element element) {
        return reflectedUrl(element, "href");
    }

    private static String reflectedUrl(Element element, String attribute) {
        String value = element.getAttribute(attribute);
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return element.getOwnerDocument().getBaseUri().resolve(value.strip()).toString();
        } catch (IllegalArgumentException invalid) {
            return value;
        }
    }

    private static boolean isUrlElement(Element element) {
        String name = tag(element);
        return "a".equals(name) || "area".equals(name);
    }

    private static Object urlPart(Element element, String part) {
        if (!isUrlElement(element)) {
            return "";
        }
        String href = resolvedUrl(element);
        if (href.isEmpty()) {
            return "";
        }
        return GraalPageRuntime.locationParts(href).get(part);
    }
}

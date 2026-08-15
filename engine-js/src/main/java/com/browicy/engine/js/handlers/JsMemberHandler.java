package com.browicy.engine.js.handlers;

import com.browicy.engine.dom.Element;
import com.browicy.engine.js.JsDocument;
import com.browicy.engine.js.JsElement;
import com.browicy.engine.js.JsNodeLike;
import org.graalvm.polyglot.Value;

import java.util.List;
import java.util.Locale;

public interface JsMemberHandler {

    default List<String> keys() {
        return List.of();
    }

    boolean canHandle(String key);

    Object get(String key, JsElement element, JsDocument doc);

    boolean set(String key, Value value, JsElement element, JsDocument doc);

    static String tag(Element element) {
        return element.getTagName().toLowerCase(Locale.ROOT);
    }

    static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    static String toText(Value value) {
        return value.isString() ? value.asString() : value.toString();
    }

    static String asString(Value[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Argument " + index + " fehlt");
        }
        return toText(args[index]);
    }

    static String nullableString(Value[] args, int index) {
        return index >= args.length || args[index].isNull() ? null : asString(args, index);
    }

    static JsNodeLike expectNode(Value[] args, int index, boolean nullable) {
        if (index < args.length && args[index].isNull() && nullable) {
            return null;
        }
        if (index < args.length && args[index].isProxyObject()
                && args[index].asProxyObject() instanceof JsNodeLike node) {
            return node;
        }
        throw new IllegalArgumentException("Es wird ein DOM-Knoten erwartet");
    }

    static int indexArg(Value[] args, int index, int defaultValue) {
        return index >= args.length ? defaultValue : args[index].asInt();
    }
}

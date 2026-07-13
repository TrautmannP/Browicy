package com.browicy.engine.js;

import com.browicy.engine.dom.DOMTokenList;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.List;

/** JavaScript-Adapter für eine live mit einem Element synchronisierte DOMTokenList. */
final class JsDomTokenList implements ProxyObject {

    private static final List<String> MEMBERS = List.of(
            "length", "value", "item", "contains", "add", "remove", "toggle");

    private final DOMTokenList tokens;
    private final JsDocument document;

    JsDomTokenList(DOMTokenList tokens, JsDocument document) {
        this.tokens = tokens;
        this.document = document;
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "length" -> tokens.getLength();
            case "value" -> tokens.getValue();
            case "item" -> (ProxyExecutable) args -> tokens.item(index(args));
            case "contains" -> document.domOperation((ProxyExecutable) args ->
                    tokens.contains(string(args, 0)));
            case "add" -> document.domOperation((ProxyExecutable) args -> {
                tokens.add(strings(args));
                return null;
            });
            case "remove" -> document.domOperation((ProxyExecutable) args -> {
                tokens.remove(strings(args));
                return null;
            });
            case "toggle" -> document.domOperation((ProxyExecutable) args -> {
                String token = string(args, 0);
                return args.length > 1
                        ? tokens.toggle(token, args[1].asBoolean())
                        : tokens.toggle(token);
            });
            default -> numericItem(key);
        };
    }

    private Object numericItem(String key) {
        try {
            return tokens.item(Integer.parseInt(key));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public void putMember(String key, Value value) {
        if ("value".equals(key)) {
            tokens.setValue(value.isNull() ? "null" : value.isString() ? value.asString() : value.toString());
            return;
        }
        throw new UnsupportedOperationException("DOMTokenList-Eigenschaft ist schreibgeschützt: " + key);
    }

    @Override
    public Object getMemberKeys() {
        List<String> keys = new ArrayList<>(MEMBERS);
        for (int index = 0; index < tokens.getLength(); index++) {
            keys.add(Integer.toString(index));
        }
        return keys.toArray();
    }

    @Override
    public boolean hasMember(String key) {
        if (MEMBERS.contains(key)) {
            return true;
        }
        try {
            int index = Integer.parseInt(key);
            return index >= 0 && index < tokens.getLength();
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String[] strings(Value[] args) {
        String[] result = new String[args.length];
        for (int index = 0; index < args.length; index++) {
            result[index] = text(args[index]);
        }
        return result;
    }

    private static String string(Value[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Argument " + index + " fehlt");
        }
        return text(args[index]);
    }

    private static String text(Value value) {
        return value.isNull() ? "null" : value.isString() ? value.asString() : value.toString();
    }

    private static int index(Value[] args) {
        return args.length == 0 || !args[0].fitsInInt() ? -1 : args[0].asInt();
    }

    @Override
    public String toString() {
        return tokens.toString();
    }
}

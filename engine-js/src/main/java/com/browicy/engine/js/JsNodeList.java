package com.browicy.engine.js;

import com.browicy.engine.dom.Element;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.List;

/** Statischer NodeList-Snapshot, wie er von querySelectorAll geliefert wird. */
final class JsNodeList implements ProxyObject {

    private final List<Element> elements;
    private final JsDocument document;

    JsNodeList(List<Element> elements, JsDocument document) {
        this.elements = List.copyOf(elements);
        this.document = document;
    }

    @Override
    public Object getMember(String key) {
        if ("length".equals(key)) {
            return elements.size();
        }
        if ("item".equals(key)) {
            return (ProxyExecutable) args -> item(index(args));
        }
        try {
            return item(Integer.parseInt(key));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Object item(int index) {
        return index >= 0 && index < elements.size() ? document.wrap(elements.get(index)) : null;
    }

    private static int index(Value[] args) {
        return args.length == 0 || !args[0].fitsInInt() ? -1 : args[0].asInt();
    }

    @Override
    public Object getMemberKeys() {
        List<String> keys = new ArrayList<>(List.of("length", "item"));
        for (int index = 0; index < elements.size(); index++) {
            keys.add(Integer.toString(index));
        }
        return keys.toArray();
    }

    @Override
    public boolean hasMember(String key) {
        if ("length".equals(key) || "item".equals(key)) {
            return true;
        }
        try {
            int index = Integer.parseInt(key);
            return index >= 0 && index < elements.size();
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("NodeList ist schreibgeschützt");
    }

    @Override
    public String toString() {
        return "[object NodeList]";
    }
}

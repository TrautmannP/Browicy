package com.browicy.engine.js;

import com.browicy.engine.dom.Element;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** A live DOM HTMLCollection backed by a query evaluated on every access. */
final class JsHtmlCollection implements ProxyObject {
    private final Supplier<List<Element>> query;
    private final JsDocument document;

    JsHtmlCollection(Supplier<List<Element>> query, JsDocument document) {
        this.query = query;
        this.document = document;
    }

    @Override
    public Object getMember(String key) {
        List<Element> elements = query.get();
        if ("length".equals(key)) return elements.size();
        if ("item".equals(key)) return (ProxyExecutable) args -> item(query.get(), index(args));
        if ("namedItem".equals(key)) return (ProxyExecutable) args ->
                named(query.get(), args.length == 0 ? "" : args[0].toString());
        try {
            return item(elements, Integer.parseInt(key));
        } catch (NumberFormatException ignored) {
            return named(elements, key);
        }
    }

    private Object item(List<Element> elements, int index) {
        return index >= 0 && index < elements.size() ? document.wrap(elements.get(index)) : null;
    }

    private Object named(List<Element> elements, String name) {
        for (Element element : elements) {
            if (name.equals(element.getAttribute("id")) || name.equals(element.getAttribute("name"))) {
                return document.wrap(element);
            }
        }
        return null;
    }

    private static int index(org.graalvm.polyglot.Value[] args) {
        return args.length == 0 || !args[0].fitsInInt() ? -1 : args[0].asInt();
    }

    @Override
    public Object getMemberKeys() {
        List<String> keys = new ArrayList<>(List.of("length", "item", "namedItem"));
        List<Element> elements = query.get();
        for (int i = 0; i < elements.size(); i++) keys.add(Integer.toString(i));
        for (Element element : elements) {
            String id = element.getAttribute("id");
            String name = element.getAttribute("name");
            if (id != null && !id.isEmpty()) keys.add(id);
            if (name != null && !name.isEmpty()) keys.add(name);
        }
        return keys.toArray();
    }

    @Override
    public boolean hasMember(String key) {
        if ("length".equals(key) || "item".equals(key) || "namedItem".equals(key)) return true;
        return getMember(key) != null;
    }

    @Override public void putMember(String key, org.graalvm.polyglot.Value value) {
        throw new UnsupportedOperationException("HTMLCollection ist schreibgeschützt");
    }

    @Override public String toString() { return "[object HTMLCollection]"; }
}

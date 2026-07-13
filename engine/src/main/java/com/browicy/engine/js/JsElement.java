package com.browicy.engine.js;

import com.browicy.engine.dom.Element;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.List;
import java.util.stream.Collectors;

/**
 * JavaScript-Sicht auf ein {@link Element} des Browicy-DOM. Implementiert
 * als {@link ProxyObject}, d.h. ohne Java-Reflection — der Polyglot-Kontext
 * kann dadurch vollständig ohne Host-Zugriff laufen.
 *
 * <p>Unterstützt einen minimalen, browserüblichen Ausschnitt:
 * {@code tagName}, {@code id}, {@code textContent} (lesen und schreiben),
 * {@code children}, {@code getAttribute}, {@code setAttribute},
 * {@code hasAttribute} und {@code appendChild}.</p>
 */
final class JsElement implements ProxyObject {

    private static final List<String> MEMBERS = List.of(
            "tagName", "id", "textContent", "children",
            "getAttribute", "setAttribute", "hasAttribute", "appendChild");

    private final Element element;
    private final JsDocument document;

    JsElement(Element element, JsDocument document) {
        this.element = element;
        this.document = document;
    }

    /** Das zugrunde liegende DOM-Element (für {@code appendChild} u.ä.). */
    Element unwrap() {
        return element;
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            // Wie im Browser-DOM: Tag-Namen von HTML-Elementen sind GROSS geschrieben
            case "tagName" -> element.getTagName().toUpperCase();
            case "id" -> orEmpty(element.getAttribute("id"));
            case "textContent" -> element.getTextContent();
            case "children" -> ProxyArray.fromList(element.getChildElements().stream()
                    .map(child -> (Object) document.wrap(child))
                    .collect(Collectors.toList()));
            case "getAttribute" -> (ProxyExecutable) args -> element.getAttribute(asString(args, 0));
            case "setAttribute" -> (ProxyExecutable) args -> {
                element.setAttribute(asString(args, 0), asString(args, 1));
                return null;
            };
            case "hasAttribute" -> (ProxyExecutable) args -> element.hasAttribute(asString(args, 0));
            case "appendChild" -> (ProxyExecutable) args -> {
                JsElement child = expectElement(args, 0);
                element.appendChild(child.unwrap());
                return child;
            };
            default -> null;
        };
    }

    @Override
    public void putMember(String key, Value value) {
        switch (key) {
            case "textContent" -> element.setTextContent(toText(value));
            case "id" -> element.setAttribute("id", toText(value));
            default -> throw new UnsupportedOperationException(
                    "Eigenschaft nicht unterstützt oder schreibgeschützt: " + key);
        }
    }

    @Override
    public Object getMemberKeys() {
        return MEMBERS.toArray();
    }

    @Override
    public boolean hasMember(String key) {
        return MEMBERS.contains(key);
    }

    private static String asString(Value[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Argument " + index + " fehlt");
        }
        return toText(args[index]);
    }

    private static JsElement expectElement(Value[] args, int index) {
        if (index < args.length && args[index].isProxyObject()
                && args[index].asProxyObject() instanceof JsElement jsElement) {
            return jsElement;
        }
        throw new IllegalArgumentException("Es wird ein DOM-Element erwartet");
    }

    private static String toText(Value value) {
        return value.isString() ? value.asString() : value.toString();
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String toString() {
        return "[object HTML" + element.getTagName() + "Element]";
    }
}

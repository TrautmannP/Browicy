package com.browicy.engine.js;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import com.browicy.engine.dom.TextNode;
import com.browicy.engine.html.HtmlParser;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JavaScript-Sicht auf das {@link Document}: das globale {@code document}-Objekt.
 *
 * <p>Unterstützt: {@code title} (lesen und schreiben), {@code body},
 * {@code documentElement}, {@code URL}, {@code getElementById},
 * {@code getElementsByTagName} und {@code createElement}.</p>
 *
 * <p>Element-Wrapper werden pro Dokument gecacht, damit Identität wie im
 * Browser funktioniert ({@code document.body === document.body}).</p>
 */
final class JsDocument implements ProxyObject {

    private static final List<String> MEMBERS = List.of(
            "title", "body", "documentElement", "URL",
            "currentScript", "getElementById", "getElementsByTagName", "createElement", "write");

    private final Document document;
    private final Map<Node, Object> wrappers = new IdentityHashMap<>();
    private Element currentScript;

    JsDocument(Document document) {
        this.document = document;
    }

    /** Liefert den (gecachten) JavaScript-Wrapper für ein DOM-Element. */
    JsElement wrap(Element element) {
        if (element == null) {
            return null;
        }
        return (JsElement) wrappers.computeIfAbsent(element, el -> new JsElement((Element) el, this));
    }

    Object wrap(Node node) {
        if (node == null) {
            return null;
        }
        if (node instanceof Element element) {
            return wrap(element);
        }
        return wrappers.computeIfAbsent(node, value -> new JsNode(value, this));
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "title" -> document.getTitle();
            case "body" -> wrap(document.getBody());
            case "documentElement" -> wrap(document.getDocumentElement());
            case "URL" -> document.getUrl();
            case "currentScript" -> wrap(currentScript);
            case "getElementById" -> (ProxyExecutable) args ->
                    wrap(document.getElementById(asString(args, 0)));
            case "getElementsByTagName" -> (ProxyExecutable) args ->
                    ProxyArray.fromList(document.getElementsByTagName(asString(args, 0)).stream()
                            .map(element -> (Object) wrap(element))
                            .collect(Collectors.toList()));
            case "createElement" -> (ProxyExecutable) args ->
                    wrap(new Element(asString(args, 0)));
            case "write" -> (ProxyExecutable) args -> {
                StringBuilder html = new StringBuilder();
                for (int i = 0; i < args.length; i++) {
                    html.append(asString(args, i));
                }
                write(html.toString());
                return null;
            };
            default -> null;
        };
    }

    void setCurrentScript(Element currentScript) {
        this.currentScript = currentScript;
    }

    private void write(String html) {
        Node parent = currentScript == null ? document.getBody() : currentScript.getParent();
        if (parent == null) {
            parent = document;
        }
        Node reference = null;
        if (currentScript != null) {
            List<Node> siblings = parent.getChildren();
            int index = siblings.indexOf(currentScript);
            if (index >= 0 && index + 1 < siblings.size()) {
                reference = siblings.get(index + 1);
            }
        }

        Document fragment = new HtmlParser().parse(html, document.getUrl());
        for (Node node : List.copyOf(fragment.getChildren())) {
            parent.insertBefore(node, reference);
        }
    }

    @Override
    public void putMember(String key, Value value) {
        if ("title".equals(key)) {
            setTitle(value.isString() ? value.asString() : value.toString());
            return;
        }
        throw new UnsupportedOperationException(
                "Eigenschaft nicht unterstützt oder schreibgeschützt: " + key);
    }

    /**
     * Setzt den Dokumenttitel. Fehlt das {@code <title>}-Element, wird es
     * im {@code <head>} angelegt; ohne {@code <head>} passiert nichts
     * (Fragment ohne Grundgerüst).
     */
    private void setTitle(String text) {
        Element title = firstByTag("title");
        if (title == null) {
            Element head = firstByTag("head");
            if (head == null) {
                return;
            }
            title = new Element("title");
            head.appendChild(title);
        }
        title.setTextContent(text);
    }

    private Element firstByTag(String tag) {
        List<Element> elements = document.getElementsByTagName(tag);
        return elements.isEmpty() ? null : elements.get(0);
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
        Value value = args[index];
        return value.isString() ? value.asString() : value.toString();
    }

    @Override
    public String toString() {
        return "[object HTMLDocument]";
    }
}

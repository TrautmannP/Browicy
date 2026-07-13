package com.browicy.engine.dom;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ein HTML-Element, z. B. {@code <p>} oder {@code <h1>}, mit Tag-Namen und Attributen.
 * Tag-Namen werden normalisiert in Kleinbuchstaben gehalten.
 */
public final class Element extends Node {

    private final String tagName;
    private final Map<String, String> attributes;

    public Element(String tagName) {
        this(tagName, Map.of());
    }

    public Element(String tagName, Map<String, String> attributes) {
        this.tagName = tagName.toLowerCase();
        this.attributes = new LinkedHashMap<>(attributes);
    }

    public String getTagName() {
        return tagName;
    }

    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public String getAttribute(String name) {
        return attributes.get(name.toLowerCase());
    }

    public boolean hasAttribute(String name) {
        return attributes.containsKey(name.toLowerCase());
    }

    /**
     * Setzt bzw. überschreibt ein Attribut. Attributnamen werden wie beim
     * Parsen in Kleinbuchstaben normalisiert.
     */
    public void setAttribute(String name, String value) {
        attributes.put(name.toLowerCase(), value == null ? "" : value);
    }

    public void removeAttribute(String name) {
        attributes.remove(name.toLowerCase());
    }

    /**
     * Liefert alle direkten Kind-Elemente (ohne Textknoten).
     */
    public List<Element> getChildElements() {
        return getChildren().stream()
                .filter(Element.class::isInstance)
                .map(Element.class::cast)
                .collect(Collectors.toList());
    }

    /**
     * Sucht in Dokumentreihenfolge das erste Element mit dem angegebenen Tag-Namen
     * in diesem Teilbaum (inklusive dieses Elements selbst).
     */
    public Element findFirst(String tag) {
        String wanted = tag.toLowerCase();
        if (tagName.equals(wanted)) {
            return this;
        }
        for (Node child : getChildren()) {
            if (child instanceof Element element) {
                Element found = element.findFirst(wanted);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "<" + tagName + ">";
    }
}

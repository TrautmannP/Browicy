package com.browicy.engine.dom;

import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;

public final class Element extends Node {

    @Getter
    private final String tagName;
    @Getter
    private final String namespaceUri;
    @Getter
    private final String prefix;
    @Getter
    private final String localName;
    private final Map<String, String> attributes;
    private final Map<String, String> computedStyles = new LinkedHashMap<>();
    private String valueState;
    private Boolean checkedState;

    public Element(String tagName) {
        this(tagName, Map.of());
    }

    public Element(String tagName, Map<String, String> attributes) {
        this(null, tagName.toLowerCase(java.util.Locale.ROOT), attributes);
    }

    public Element(String namespaceUri, String qualifiedName) {
        this(namespaceUri, qualifiedName, Map.of());
    }

    private Element(String namespaceUri, String qualifiedName, Map<String, String> attributes) {
        this.tagName = qualifiedName;
        this.namespaceUri = namespaceUri;
        int separator = qualifiedName.indexOf(':');
        this.prefix = separator < 0 ? null : qualifiedName.substring(0, separator);
        this.localName = separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
        this.attributes = new LinkedHashMap<>(attributes);
    }

    @Override public short getNodeType() { return ELEMENT_NODE; }
    @Override public String getNodeName() {
        return namespaceUri == null ? tagName.toUpperCase(java.util.Locale.ROOT) : tagName;
    }

    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public Map<String, String> getComputedStyles() {
        return Collections.unmodifiableMap(computedStyles);
    }

    public void setComputedStyle(String property, String value) {
        computedStyles.put(property, value);
    }

    public void clearComputedStyles() {
        computedStyles.clear();
    }

    public String getAttribute(String name) {
        return attributes.get(name.toLowerCase());
    }

    public boolean hasAttribute(String name) {
        return attributes.containsKey(name.toLowerCase());
    }

    public String getId() {
        return getAttribute("id");
    }

    public List<String> getClassNames() {
        String classAttribute = getAttribute("class");
        if (classAttribute == null || classAttribute.isBlank()) {
            return List.of();
        }
        return List.of(classAttribute.strip().split("\\s+"));
    }

    public boolean hasClass(String className) {
        return className != null && getClassNames().contains(className);
    }

    public void setAttribute(String name, String value) {
        attributes.put(name.toLowerCase(), value == null ? "" : value);
    }

    public void removeAttribute(String name) {
        attributes.remove(name.toLowerCase(Locale.ROOT));
    }

    public String getValueState() {
        return valueState == null ? getAttribute("value") : valueState;
    }

    public void setValueState(String value) {
        valueState = value == null ? "" : value;
    }

    public boolean isCheckedState() {
        return checkedState == null ? hasAttribute("checked") : checkedState;
    }

    public void setCheckedState(boolean checked) {
        checkedState = checked;
    }

    Element copyShallow() {
        Element copy = new Element(namespaceUri, tagName, attributes);
        copy.valueState = valueState;
        copy.checkedState = checkedState;
        copy.computedStyles.putAll(computedStyles);
        return copy;
    }

    public List<Element> getChildElements() {
        return getChildren().stream()
                .filter(Element.class::isInstance)
                .map(Element.class::cast)
                .collect(Collectors.toList());
    }

    public Element findFirst(String tag) {
        String wanted = tag.toLowerCase(Locale.ROOT);
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

    public List<Element> getElementsByTagName(String tag) {
        String wanted = tag.toLowerCase(Locale.ROOT);
        List<Element> result = new java.util.ArrayList<>();
        collectByTag(this, wanted, result);
        return result;
    }

    private static void collectByTag(Element root, String wanted, List<Element> result) {
        for (Node child : root.getChildren()) {
            if (child instanceof Element element) {
                if ("*".equals(wanted) || element.tagName.equals(wanted)) result.add(element);
                collectByTag(element, wanted, result);
            }
        }
    }

    @Override
    public String toString() {
        return "<" + tagName + ">";
    }
}

package com.browicy.engine.dom;

import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;

public final class Element extends Node implements ParentNode {

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
    private final Map<String, Map<String, String>> pseudoComputedStyles = new LinkedHashMap<>();
    private final DOMTokenList classList = new DOMTokenList(this);
    private String valueState;
    private Boolean checkedState;
    private boolean hovered;

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
        pseudoComputedStyles.clear();
    }

    public Map<String, String> getPseudoComputedStyles(String pseudoElement) {
        Map<String, String> styles = pseudoComputedStyles.get(pseudoElement);
        return styles == null ? Map.of() : Collections.unmodifiableMap(styles);
    }

    public void setPseudoComputedStyle(String pseudoElement, String property, String value) {
        pseudoComputedStyles.computeIfAbsent(pseudoElement, ignored -> new LinkedHashMap<>())
                .put(property, value);
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

    public DOMTokenList getClassList() {
        return classList;
    }

    public List<String> getClassNames() {
        return classList.tokens();
    }

    public boolean hasClass(String className) {
        return className != null && getClassNames().contains(className);
    }

    public void setAttribute(String name, String value) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        String normalizedValue = value == null ? "" : value;
        String oldValue = attributes.put(normalizedName, normalizedValue);
        if (!java.util.Objects.equals(oldValue, normalizedValue)) {
            notifyMutation(new DomMutation.AttributeChanged(
                    this, normalizedName, oldValue, normalizedValue));
        }
    }

    public void removeAttribute(String name) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        if (!attributes.containsKey(normalizedName)) {
            return;
        }
        String oldValue = attributes.remove(normalizedName);
        notifyMutation(new DomMutation.AttributeChanged(this, normalizedName, oldValue, null));
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

    public boolean isHovered() {
        return hovered;
    }

    public boolean isFocused() {
        return getOwnerDocument() != null && getOwnerDocument().getFocusedElement() == this;
    }

    public boolean isActive() {
        return getOwnerDocument() != null && getOwnerDocument().getActiveElement() == this;
    }

    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    Element copyShallow() {
        Element copy = new Element(namespaceUri, tagName, attributes);
        copy.valueState = valueState;
        copy.checkedState = checkedState;
        copy.computedStyles.putAll(computedStyles);
        pseudoComputedStyles.forEach((pseudo, styles) ->
                copy.pseudoComputedStyles.put(pseudo, new LinkedHashMap<>(styles)));
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

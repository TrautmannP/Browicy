package com.browicy.engine.selectors;

import java.util.Objects;

/**
 * Attributselektor.
 *
 * <p>{@code namespace} ist {@code null} ohne Namespace-Angabe (matcht jeden
 * Namespace), {@code "*"} für {@code [*|attr]} (jeder Namespace), {@code ""}
 * für {@code [|attr]} (kein Namespace) und sonst das Präfix aus
 * {@code [prefix|attr]} (nur Namespace-Attribute).</p>
 */
public record AttributeSelector(String namespace, String name, Operator operator, String value) {

    public AttributeSelector(String name, Operator operator, String value) {
        this(null, name, operator, value);
    }

    public enum Operator {
        PRESENT,
        EQUALS,
        INCLUDES,
        CONTAINS,
        PREFIX_MATCH,
        SUFFIX_MATCH
    }

    public AttributeSelector {
        if (namespace != null && !namespace.isEmpty() && namespace.isBlank()) {
            throw new IllegalArgumentException("Der Attribut-Namespace darf nicht leer sein");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Der Attributname darf nicht leer sein");
        }
        Objects.requireNonNull(operator, "operator");
        if (operator == Operator.PRESENT && value != null) {
            throw new IllegalArgumentException("Ein Präsenzselektor besitzt keinen Wert");
        }
        if (operator != Operator.PRESENT && value == null) {
            throw new IllegalArgumentException("Der Attributselektor benötigt einen Wert");
        }
    }

    <N> boolean matches(N element, SelectorNodeAdapter<N> adapter) {
        if (!adapter.hasAttribute(element, name)) {
            return false;
        }
        if (namespace != null && !"*".equals(namespace)) {
            String attributeNamespace = adapter.attributeNamespace(element, name);
            if ("".equals(namespace)) {
                if (attributeNamespace != null) {
                    return false;
                }
            } else if (attributeNamespace == null) {
                return false;
            }
        }
        if (operator == Operator.PRESENT) {
            return true;
        }
        String attributeValue = adapter.attributeValue(element, name);
        if (operator == Operator.EQUALS) {
            return value.equals(attributeValue);
        }
        if (operator == Operator.CONTAINS) {
            return attributeValue != null && !value.isEmpty() && attributeValue.contains(value);
        }
        if (operator == Operator.PREFIX_MATCH) {
            return attributeValue != null && !value.isEmpty()
                    && attributeValue.startsWith(value);
        }
        if (operator == Operator.SUFFIX_MATCH) {
            return attributeValue != null && !value.isEmpty()
                    && attributeValue.endsWith(value);
        }
        if (attributeValue == null || value.isEmpty()) {
            return false;
        }
        for (String token : attributeValue.split("\\s+")) {
            if (value.equals(token)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        String prefix = namespace == null ? "" : namespace + "|";
        return switch (operator) {
            case PRESENT -> "[" + prefix + name + "]";
            case EQUALS -> "[" + prefix + name + "=\"" + escapedValue() + "\"]";
            case INCLUDES -> "[" + prefix + name + "~=\"" + escapedValue() + "\"]";
            case CONTAINS -> "[" + prefix + name + "*=\"" + escapedValue() + "\"]";
            case PREFIX_MATCH -> "[" + prefix + name + "^=\"" + escapedValue() + "\"]";
            case SUFFIX_MATCH -> "[" + prefix + name + "$=\"" + escapedValue() + "\"]";
        };
    }

    private String escapedValue() {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

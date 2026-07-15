package com.browicy.engine.selectors;

import java.util.Objects;

public record AttributeSelector(String name, Operator operator, String value) {

    public enum Operator {
        PRESENT,
        EQUALS,
        INCLUDES,
        CONTAINS
    }

    public AttributeSelector {
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
        return switch (operator) {
            case PRESENT -> "[" + name + "]";
            case EQUALS -> "[" + name + "=\"" + escapedValue() + "\"]";
            case INCLUDES -> "[" + name + "~=\"" + escapedValue() + "\"]";
            case CONTAINS -> "[" + name + "*=\"" + escapedValue() + "\"]";
        };
    }

    private String escapedValue() {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

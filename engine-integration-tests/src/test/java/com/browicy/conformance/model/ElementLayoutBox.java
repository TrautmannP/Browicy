package com.browicy.conformance.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Geometry and selected computed styles for one DOM element. */
public record ElementLayoutBox(
        String selector,
        String tagName,
        float x,
        float y,
        float width,
        float height,
        Map<String, String> computedStyles) {

    public ElementLayoutBox {
        selector = requireText(selector, "selector");
        tagName = requireText(tagName, "tagName");
        computedStyles = Map.copyOf(new LinkedHashMap<>(
                Objects.requireNonNull(computedStyles, "computedStyles")));
    }

    public float right() {
        return x + width;
    }

    public float bottom() {
        return y + height;
    }

    public String style(String property) {
        return computedStyles.get(property);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

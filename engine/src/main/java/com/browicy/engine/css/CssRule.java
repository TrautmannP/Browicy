package com.browicy.engine.css;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Eine minimale CSS-Regel mit Elementselektor und unterstützten Deklarationen. */
public record CssRule(String selector, Map<String, String> declarations) {

    public CssRule {
        declarations = Collections.unmodifiableMap(new LinkedHashMap<>(declarations));
    }

    /** Element-Typ-Selektoren besitzen eine Spezifität von 0-0-1. */
    public int specificity() {
        return 1;
    }
}

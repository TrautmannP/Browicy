package com.browicy.engine.css;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Eine geparste CSS-Regel mit Selektor, Deklarationen und Quellreihenfolge. */
public record CssRule(CssSelector selector, Map<String, String> declarations, int sourceOrder) {

    public CssRule {
        Objects.requireNonNull(selector, "selector");
        declarations = Collections.unmodifiableMap(new LinkedHashMap<>(declarations));
    }

    public CssRule(CssSelector selector, Map<String, String> declarations) {
        this(selector, declarations, 0);
    }

    public Specificity specificity() {
        return selector.specificity();
    }
}

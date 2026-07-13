package com.browicy.engine.css;

import com.browicy.engine.selectors.Selector;
import com.browicy.engine.selectors.Specificity;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Eine geparste CSS-Regel mit Selektor, Deklarationen und Quellreihenfolge. */
public record CssRule(Selector selector, Map<String, String> declarations, int sourceOrder) {

    public CssRule {
        Objects.requireNonNull(selector, "selector");
        declarations = Collections.unmodifiableMap(new LinkedHashMap<>(declarations));
    }

    public CssRule(Selector selector, Map<String, String> declarations) {
        this(selector, declarations, 0);
    }

    public Specificity specificity() {
        return selector.specificity();
    }
}

package com.browicy.engine.css;

import com.browicy.engine.selectors.Selector;
import com.browicy.engine.selectors.Specificity;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record CssRule(Selector selector, Map<String, String> declarations, long sourceOrder,
                      MediaCondition mediaCondition, Set<String> importantProperties) {

    public CssRule {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(mediaCondition, "mediaCondition");
        declarations = Collections.unmodifiableMap(new LinkedHashMap<>(declarations));
        importantProperties = Set.copyOf(importantProperties);
    }

    public CssRule(Selector selector, Map<String, String> declarations) {
        this(selector, declarations, 0, MediaCondition.ALL, Set.of());
    }

    public CssRule(Selector selector, Map<String, String> declarations, long sourceOrder) {
        this(selector, declarations, sourceOrder, MediaCondition.ALL, Set.of());
    }

    public CssRule(Selector selector, Map<String, String> declarations, long sourceOrder,
                   MediaCondition mediaCondition) {
        this(selector, declarations, sourceOrder, mediaCondition, Set.of());
    }

    public Specificity specificity() {
        return selector.specificity();
    }
}

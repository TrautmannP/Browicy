package com.browicy.engine.selectors;

import java.util.List;
import java.util.Objects;

/** Kommagetrennte Liste komplexer Selektoren. */
public record SelectorList(List<ComplexSelector> selectors) {

    public SelectorList {
        selectors = List.copyOf(Objects.requireNonNull(selectors, "selectors"));
    }

    public boolean isEmpty() {
        return selectors.isEmpty();
    }

    public <N> boolean matchesAny(N element, SelectorNodeAdapter<N> adapter) {
        for (ComplexSelector selector : selectors) {
            if (selector.matches(element, adapter)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return String.join(", ", selectors.stream().map(Object::toString).toList());
    }
}

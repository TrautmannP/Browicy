package com.browicy.engine.selectors;

import java.util.Objects;

/**
 * Relativer Selektor als Argument von {@code :has()}: ein komplexer Selektor
 * mit optionalem führendem Kombinator (CSS Selectors Level 4).
 *
 * <p>{@code combinator} ist {@code null} für die Nachfahren-Relation
 * ({@code :has(.a)}), sonst {@code >}, {@code +} oder {@code ~}.</p>
 */
public record RelativeSelector(Combinator combinator, ComplexSelector selector) {

    public RelativeSelector {
        Objects.requireNonNull(selector, "selector");
    }

    <N> boolean matches(N element, SelectorNodeAdapter<N> adapter) {
        if (combinator == Combinator.CHILD) {
            for (N child : adapter.children(element)) {
                if (selector.matches(child, adapter)) {
                    return true;
                }
            }
            return false;
        }
        if (combinator == Combinator.ADJACENT_SIBLING) {
            N sibling = adapter.nextElementSibling(element);
            return sibling != null && selector.matches(sibling, adapter);
        }
        if (combinator == Combinator.GENERAL_SIBLING) {
            N sibling = adapter.nextElementSibling(element);
            while (sibling != null) {
                if (selector.matches(sibling, adapter)) {
                    return true;
                }
                sibling = adapter.nextElementSibling(sibling);
            }
            return false;
        }
        return hasMatchingDescendant(element, adapter);
    }

    private <N> boolean hasMatchingDescendant(N element, SelectorNodeAdapter<N> adapter) {
        for (N child : adapter.children(element)) {
            if (selector.matches(child, adapter) || hasMatchingDescendant(child, adapter)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return combinator == null ? selector.toString()
                : combinator.css() + " " + selector;
    }
}

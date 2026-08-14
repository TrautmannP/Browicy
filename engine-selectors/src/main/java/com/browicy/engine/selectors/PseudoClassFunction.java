package com.browicy.engine.selectors;

import java.util.Objects;

/**
 * Funktionale Pseudoklasse mit Selektorliste: {@code :is()}, {@code :where()}
 * und {@code :not()} (CSS Selectors Level 4).
 *
 * <p>{@code :is()} und {@code :not()} erben die Spezifität ihres
 * spezifischsten Arguments, {@code :where()} trägt immer null bei. Die
 * Argumentliste darf keine Pseudoelemente enthalten.</p>
 */
public record PseudoClassFunction(Kind kind, SelectorList selectors) {

    public PseudoClassFunction {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(selectors, "selectors");
    }

    public enum Kind { IS, WHERE, NOT }

    public Specificity specificity() {
        if (kind == Kind.WHERE) {
            return Specificity.ZERO;
        }
        Specificity maximum = Specificity.ZERO;
        for (ComplexSelector selector : selectors.selectors()) {
            Specificity candidate = selector.specificity();
            if (candidate.compareTo(maximum) > 0) {
                maximum = candidate;
            }
        }
        return maximum;
    }

    <N> boolean matches(N element, SelectorNodeAdapter<N> adapter) {
        boolean any = selectors.matchesAny(element, adapter);
        return kind == Kind.NOT ? !any : any;
    }

    @Override
    public String toString() {
        String name = switch (kind) {
            case IS -> "is";
            case WHERE -> "where";
            case NOT -> "not";
        };
        return ":" + name + "(" + selectors + ")";
    }
}

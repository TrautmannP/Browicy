package com.browicy.engine.selectors;

import java.util.List;
import java.util.Objects;

/**
 * Funktionale Pseudoklasse mit Selektorliste: {@code :is()}, {@code :where()},
 * {@code :not()} und {@code :has()} (CSS Selectors Level 4).
 *
 * <p>{@code :is()}, {@code :not()} und {@code :has()} erben die Spezifität
 * ihres spezifischsten Arguments, {@code :where()} trägt immer null bei. Die
 * Argumentlisten dürfen keine Pseudoelemente enthalten. {@code :has()} nimmt
 * statt komplexer Selektoren relative Selektoren entgegen.</p>
 */
public record PseudoClassFunction(Kind kind, SelectorList selectors,
                                  List<RelativeSelector> relatives) {

    public PseudoClassFunction {
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.HAS) {
            relatives = List.copyOf(Objects.requireNonNull(relatives, "relatives"));
            if (relatives.isEmpty()) {
                throw new IllegalArgumentException(":has() benötigt mindestens ein Argument");
            }
        } else {
            Objects.requireNonNull(selectors, "selectors");
            relatives = List.of();
        }
    }

    public PseudoClassFunction(Kind kind, SelectorList selectors) {
        this(kind, selectors, List.of());
    }

    public PseudoClassFunction(List<RelativeSelector> relatives) {
        this(Kind.HAS, null, relatives);
    }

    public enum Kind { IS, WHERE, NOT, HAS }

    public Specificity specificity() {
        if (kind == Kind.WHERE) {
            return Specificity.ZERO;
        }
        Specificity maximum = Specificity.ZERO;
        if (kind == Kind.HAS) {
            for (RelativeSelector relative : relatives) {
                Specificity candidate = relative.selector().specificity();
                if (candidate.compareTo(maximum) > 0) {
                    maximum = candidate;
                }
            }
            return maximum;
        }
        for (ComplexSelector selector : selectors.selectors()) {
            Specificity candidate = selector.specificity();
            if (candidate.compareTo(maximum) > 0) {
                maximum = candidate;
            }
        }
        return maximum;
    }

    <N> boolean matches(N element, SelectorNodeAdapter<N> adapter) {
        if (kind == Kind.HAS) {
            for (RelativeSelector relative : relatives) {
                if (relative.matches(element, adapter)) {
                    return true;
                }
            }
            return false;
        }
        boolean any = selectors.matchesAny(element, adapter);
        return kind == Kind.NOT ? !any : any;
    }

    @Override
    public String toString() {
        String name = switch (kind) {
            case IS -> "is";
            case WHERE -> "where";
            case NOT -> "not";
            case HAS -> "has";
        };
        if (kind == Kind.HAS) {
            return ":" + name + "(" + String.join(", ", relatives.stream()
                    .map(Object::toString).toList()) + ")";
        }
        return ":" + name + "(" + selectors + ")";
    }
}

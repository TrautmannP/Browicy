package com.browicy.engine.css;

import com.browicy.engine.selectors.Specificity;
import java.util.Objects;

/** Vergleichsgewicht einer Deklaration innerhalb der unterstützten CSS-Kaskade. */
public record CascadePriority(boolean inlineStyle, Specificity specificity, int sourceOrder)
        implements Comparable<CascadePriority> {

    public CascadePriority {
        Objects.requireNonNull(specificity, "specificity");
    }

    @Override
    public int compareTo(CascadePriority other) {
        int comparison = Boolean.compare(inlineStyle, other.inlineStyle);
        if (comparison != 0) {
            return comparison;
        }
        comparison = specificity.compareTo(other.specificity);
        if (comparison != 0) {
            return comparison;
        }
        return Integer.compare(sourceOrder, other.sourceOrder);
    }
}

package com.browicy.engine.css;

import com.browicy.engine.selectors.Specificity;
import java.util.Objects;

public record CascadePriority(boolean important, boolean inlineStyle,
                              Specificity specificity, long sourceOrder)
        implements Comparable<CascadePriority> {

    public CascadePriority(boolean inlineStyle, Specificity specificity, long sourceOrder) {
        this(false, inlineStyle, specificity, sourceOrder);
    }

    public CascadePriority {
        Objects.requireNonNull(specificity, "specificity");
    }

    @Override
    public int compareTo(CascadePriority other) {
        int comparison = Boolean.compare(important, other.important);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Boolean.compare(inlineStyle, other.inlineStyle);
        if (comparison != 0) {
            return comparison;
        }
        comparison = specificity.compareTo(other.specificity);
        if (comparison != 0) {
            return comparison;
        }
        return Long.compare(sourceOrder, other.sourceOrder);
    }
}

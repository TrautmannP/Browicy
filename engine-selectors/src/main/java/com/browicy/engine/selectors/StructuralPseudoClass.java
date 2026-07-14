package com.browicy.engine.selectors;

import java.util.Objects;

public record StructuralPseudoClass(Kind kind, int a, int b) {

    public StructuralPseudoClass {
        Objects.requireNonNull(kind, "kind");
    }

    public enum Kind {
        FIRST_CHILD,
        LAST_CHILD,
        NTH_CHILD,
        LAST_OF_TYPE,
        NTH_OF_TYPE
    }

    public static StructuralPseudoClass firstChild() {
        return new StructuralPseudoClass(Kind.FIRST_CHILD, 0, 1);
    }

    public static StructuralPseudoClass lastChild() {
        return new StructuralPseudoClass(Kind.LAST_CHILD, 0, 0);
    }

    public static StructuralPseudoClass nthChild(int a, int b) {
        return new StructuralPseudoClass(Kind.NTH_CHILD, a, b);
    }

    public static StructuralPseudoClass lastOfType() {
        return new StructuralPseudoClass(Kind.LAST_OF_TYPE, 0, 0);
    }

    public static StructuralPseudoClass nthOfType(int a, int b) {
        return new StructuralPseudoClass(Kind.NTH_OF_TYPE, a, b);
    }

    <N> boolean matches(N element, SelectorNodeAdapter<N> adapter) {
        if (adapter.parentElement(element) == null) {
            return false;
        }
        if (kind == Kind.FIRST_CHILD) {
            return adapter.previousElementSibling(element) == null;
        }
        if (kind == Kind.LAST_CHILD) {
            return adapter.nextElementSibling(element) == null;
        }

        if (kind == Kind.LAST_OF_TYPE) {
            N sibling = adapter.nextElementSibling(element);
            while (sibling != null) {
                if (sameType(element, sibling, adapter)) {
                    return false;
                }
                sibling = adapter.nextElementSibling(sibling);
            }
            return true;
        }

        int index = 1;
        N sibling = adapter.previousElementSibling(element);
        while (sibling != null) {
            if (kind == Kind.NTH_CHILD || sameType(element, sibling, adapter)) {
                index++;
            }
            sibling = adapter.previousElementSibling(sibling);
        }
        if (a == 0) {
            return index == b;
        }
        int difference = index - b;
        return difference % a == 0 && difference / a >= 0;
    }

    private <N> boolean sameType(N element, N sibling, SelectorNodeAdapter<N> adapter) {
        return adapter.matchesType(sibling, adapter.tagName(element));
    }

    @Override
    public String toString() {
        return switch (kind) {
            case FIRST_CHILD -> ":first-child";
            case LAST_CHILD -> ":last-child";
            case NTH_CHILD -> ":nth-child(" + formula() + ")";
            case LAST_OF_TYPE -> ":last-of-type";
            case NTH_OF_TYPE -> ":nth-of-type(" + formula() + ")";
        };
    }

    private String formula() {
        if (a == 0) {
            return Integer.toString(b);
        }
        String coefficient = a == 1 ? "" : a == -1 ? "-" : Integer.toString(a);
        String offset = b == 0 ? "" : b > 0 ? "+" + b : Integer.toString(b);
        return coefficient + "n" + offset;
    }
}

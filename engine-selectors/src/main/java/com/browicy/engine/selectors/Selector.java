package com.browicy.engine.selectors;

/** Ein geparster CSS-Selektor, der gegen einen beliebigen Elementadapter prüfbar ist. */
public interface Selector {

    Specificity specificity();

    <N> boolean matches(N element, SelectorNodeAdapter<N> adapter);

    /** The generated pseudo-element selected by this selector, if any. */
    default String pseudoElement() {
        return null;
    }
}

package com.browicy.engine.selectors;

/** Von Browicy unterstützte Beziehungen zwischen zusammengesetzten Selektoren. */
public enum Combinator {
    CHILD(">"),
    DESCENDANT(" ");

    private final String css;

    Combinator(String css) {
        this.css = css;
    }

    String css() {
        return css;
    }
}

package com.browicy.engine.selectors;

public enum Combinator {
    CHILD(">"),
    DESCENDANT(" "),
    ADJACENT_SIBLING("+"),
    GENERAL_SIBLING("~");

    private final String css;

    Combinator(String css) {
        this.css = css;
    }

    String css() {
        return css;
    }
}

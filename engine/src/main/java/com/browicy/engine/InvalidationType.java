package com.browicy.engine;

/** Ordered rendering work required after a page change. */
public enum InvalidationType {
    PAINT,
    LAYOUT,
    RENDER_TREE,
    STYLE;

    public InvalidationType merge(InvalidationType other) {
        return ordinal() >= other.ordinal() ? this : other;
    }

    public boolean requires(InvalidationType work) {
        return ordinal() >= work.ordinal();
    }
}

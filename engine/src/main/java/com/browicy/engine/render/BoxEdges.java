package com.browicy.engine.render;

/** Numeric values for the four edges of a CSS box. */
public record BoxEdges(float top, float right, float bottom, float left) {

    public static final BoxEdges ZERO = new BoxEdges(0, 0, 0, 0);

    public float horizontal() {
        return left + right;
    }

    public float vertical() {
        return top + bottom;
    }
}

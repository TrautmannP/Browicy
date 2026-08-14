package com.browicy.engine.render;

/**
 * Aufgelöste Eckradien einer Box im Uhrzeigersinn ab oben links.
 * Prozentwerte werden als unendlich aufgelöst und vom Painter auf die
 * halbe Boxkante begrenzt (runde Formen wie {@code border-radius:50%}).
 */
public record CornerRadii(float topLeft, float topRight,
                          float bottomRight, float bottomLeft) {

    public static final CornerRadii ZERO = new CornerRadii(0, 0, 0, 0);

    public CornerRadii {
        topLeft = Math.max(0, topLeft);
        topRight = Math.max(0, topRight);
        bottomRight = Math.max(0, bottomRight);
        bottomLeft = Math.max(0, bottomLeft);
    }
}

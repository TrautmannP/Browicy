package com.browicy.engine.render;

/**
 * Eine aufgelöste Box-Schatten-Ebene (CSS {@code box-shadow}). Versatz,
 * Weichzeichner und Ausbreitung sind Pixelwerte; der Painter begrenzt die
 * Weichzeichnung auf einen kleinen Kernel.
 */
public record BoxShadow(boolean inset, float xOffset, float yOffset,
                        float blur, float spread, CssColor color) {

    public BoxShadow {
        blur = Math.max(0, blur);
        spread = Math.max(0, spread);
    }
}

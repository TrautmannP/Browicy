package com.browicy.engine.render;

/**
 * Unveränderlicher Sub-Record für Farben, Dekorationen, Hintergründe und
 * Sichtbarkeit.
 *
 * <p>Enthält die Textfarbe, Textdekorationen (unterstrichen/durchgestrichen
 * samt Dekorationsfarbe), den Hintergrund (Farbe, Bild, Wiederholung,
 * Position und Größe) sowie Deckkraft und Sichtbarkeit. Die Deckkraft
 * ({@code opacity}) muss im Intervall [0, 1] liegen — wird hier validiert.</p>
 */
public record ColorBackgroundStyle(
        CssColor color,
        boolean underline,
        boolean lineThrough,
        CssColor textDecorationColor,
        CssColor backgroundColor,
        String backgroundImageUrl,
        RenderStyle.BackgroundRepeat backgroundRepeat,
        RenderStyle.BackgroundPositionX backgroundPositionX,
        RenderStyle.BackgroundPositionY backgroundPositionY,
        RenderLength backgroundPositionOffsetX,
        RenderLength backgroundPositionOffsetY,
        RenderLength backgroundSizeX,
        RenderLength backgroundSizeY,
        float opacity,
        boolean visible) {

    public ColorBackgroundStyle {
        if (!Float.isFinite(opacity) || opacity < 0 || opacity > 1) {
            throw new IllegalArgumentException("opacity must be between 0 and 1");
        }
    }
}

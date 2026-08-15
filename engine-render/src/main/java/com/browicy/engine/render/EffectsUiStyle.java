package com.browicy.engine.render;

import java.util.List;

/**
 * Unveränderlicher Sub-Record für Effekte und UI-Zustände.
 *
 * <p>Enthält Schatten (Box- und Textschatten), Outline (Breite, Farbe,
 * Sichtbarkeit, Offset) sowie Cursor und Pointer-Events. Die Schattenliste
 * ({@code boxShadows}) wird defensiv als unveränderliche Kopie übernommen.</p>
 */
public record EffectsUiStyle(
        List<BoxShadow> boxShadows,
        RenderStyle.TextShadow textShadow,
        float outlineWidth,
        CssColor outlineColor,
        boolean outlineVisible,
        float outlineOffset,
        RenderStyle.Cursor cursor,
        boolean pointerEvents) {

    public EffectsUiStyle {
        boxShadows = List.copyOf(boxShadows);
    }
}

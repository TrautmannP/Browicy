package com.browicy.engine.render;

/**
 * Unveränderlicher Sub-Record der Positionierung: Positionsmodus, Z-Index,
 * Floats, Clear und Transformationen.
 *
 * <p>Enthält alle Eigenschaften, die die Einordnung einer Box in den
 * Positionierungs-Kontext beschreiben (statisch/relativ/absolut/sticky/fixed),
 * ihre Verschachtelungstiefe ({@code zIndex}), den Float-/Clear-Mechanismus
 * sowie die Offset-Werte der vier Kanten und die Transformationsliste.</p>
 */
public record PositionStyle(
        RenderStyle.Position position,
        int zIndex,
        RenderStyle.FloatMode floatMode,
        RenderStyle.Clear clear,
        RenderOffset top,
        RenderOffset right,
        RenderOffset bottom,
        RenderOffset left,
        Transform transform) {
}

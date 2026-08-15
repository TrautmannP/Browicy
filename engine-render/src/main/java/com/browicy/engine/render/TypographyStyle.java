package com.browicy.engine.render;

/**
 * Unveränderlicher Sub-Record der Typografie: Schrift- und Textformatierung.
 *
 * <p>Enthält alle vererbten und textbezogenen Eigenschaften (Schriftgröße,
 * Schriftfamilie, -gewicht, -stil, Zeilenhöhe, Ausrichtung, Groß-/Kleinschreibung,
 * White-Space-Verhalten, Buchstabenabstand, Text-Overflow und Listenmarker).
 * Die Schriftgröße ({@code fontSizePx}) muss positiv und das Schriftgewicht
 * ({@code fontWeight}) zwischen 100 und 900 sein — beides wird hier
 * validiert.</p>
 */
public record TypographyStyle(
        float fontSizePx,
        String fontFamily,
        int fontWeight,
        boolean italic,
        float lineHeight,
        RenderStyle.TextAlign textAlign,
        RenderStyle.TextTransform textTransform,
        RenderStyle.WhiteSpace whiteSpace,
        float letterSpacingPx,
        RenderStyle.TextOverflow textOverflow,
        RenderStyle.ListStyleType listStyleType) {

    public TypographyStyle {
        if (fontSizePx <= 0) {
            throw new IllegalArgumentException("fontSizePx must be positive");
        }
        if (fontWeight < 100 || fontWeight > 900) {
            throw new IllegalArgumentException("fontWeight outside 100..900");
        }
    }
}

package com.browicy.engine.render;

/** Fully resolved style values consumed by layout and painting. */
public record RenderStyle(
        Display display,
        float fontSizePx,
        int fontWeight,
        boolean italic,
        CssColor color,
        CssColor backgroundColor,
        RenderLength width,
        RenderLength height,
        BoxEdges margin,
        HorizontalAutoMargins autoMargins,
        BoxEdges padding,
        BoxEdges borderWidth,
        BoxColors borderColor,
        BoxBorders borderStyle,
        TextAlign textAlign) {

    public enum Display { BLOCK, INLINE, INLINE_BLOCK, NONE }
    public enum TextAlign { LEFT, CENTER, RIGHT }

    public RenderStyle {
        if (fontSizePx <= 0) {
            throw new IllegalArgumentException("fontSizePx must be positive");
        }
        if (fontWeight < 100 || fontWeight > 900) {
            throw new IllegalArgumentException("fontWeight outside 100..900");
        }
    }

    public boolean bold() {
        return fontWeight >= 600;
    }
}

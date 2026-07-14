package com.browicy.engine.render;

public record RenderStyle(
        Display display,
        Position position,
        RenderOffset top,
        RenderOffset right,
        RenderOffset bottom,
        RenderOffset left,
        float fontSizePx,
        int fontWeight,
        boolean italic,
        CssColor color,
        CssColor backgroundColor,
        RenderLength width,
        RenderLength height,
        RenderLength minWidth,
        RenderLength maxWidth,
        RenderLength minHeight,
        RenderLength maxHeight,
        BoxSizing boxSizing,
        BoxEdges margin,
        HorizontalAutoMargins autoMargins,
        BoxEdges padding,
        BoxEdges borderWidth,
        BoxColors borderColor,
        BoxBorders borderStyle,
        TextAlign textAlign,
        Overflow overflow,
        VerticalAlign verticalAlign) {

    public enum Display { BLOCK, INLINE, INLINE_BLOCK, NONE }
    public enum Position { STATIC, RELATIVE, ABSOLUTE }
    public enum TextAlign { LEFT, CENTER, RIGHT }
    public enum BoxSizing { CONTENT_BOX, BORDER_BOX }
    public enum Overflow { VISIBLE, HIDDEN, AUTO, SCROLL }
    public enum VerticalAlign { BASELINE, TOP, MIDDLE, BOTTOM }

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

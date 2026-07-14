package com.browicy.engine.render;

public record RenderStyle(
        Display display,
        Position position,
        int zIndex,
        FloatMode floatMode,
        Clear clear,
        RenderOffset top,
        RenderOffset right,
        RenderOffset bottom,
        RenderOffset left,
        float fontSizePx,
        String fontFamily,
        int fontWeight,
        boolean italic,
        float lineHeight,
        CssColor color,
        ListStyleType listStyleType,
        boolean underline,
        CssColor textDecorationColor,
        Cursor cursor,
        CssColor backgroundColor,
        String backgroundImageUrl,
        BackgroundRepeat backgroundRepeat,
        BackgroundPositionX backgroundPositionX,
        BackgroundPositionY backgroundPositionY,
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
        float borderRadius,
        float outlineWidth,
        CssColor outlineColor,
        boolean outlineVisible,
        BorderCollapse borderCollapse,
        TextAlign textAlign,
        Overflow overflow,
        VerticalAlign verticalAlign) {

    public enum Display {
        BLOCK, INLINE, INLINE_BLOCK, NONE,
        TABLE, INLINE_TABLE, TABLE_ROW_GROUP, TABLE_HEADER_GROUP, TABLE_FOOTER_GROUP,
        TABLE_ROW, TABLE_CELL, TABLE_COLUMN_GROUP, TABLE_COLUMN, TABLE_CAPTION
    }
    public enum BorderCollapse { SEPARATE, COLLAPSE }
    public enum ListStyleType { DISC, CIRCLE, SQUARE, NONE }
    public enum BackgroundRepeat { REPEAT, REPEAT_X, REPEAT_Y, NO_REPEAT }
    public enum BackgroundPositionX { LEFT, CENTER, RIGHT }
    public enum BackgroundPositionY { TOP, CENTER, BOTTOM }
    public enum Position { STATIC, RELATIVE, ABSOLUTE }
    public enum Cursor { DEFAULT, POINTER, TEXT }
    public enum FloatMode { NONE, LEFT, RIGHT }
    public enum Clear { NONE, LEFT, RIGHT, BOTH }
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

    public float usedLineHeightPx() {
        return lineHeight < 0 ? -lineHeight * fontSizePx : lineHeight;
    }
}

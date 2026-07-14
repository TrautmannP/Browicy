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
        VerticalAlign verticalAlign,
        FlexDirection flexDirection,
        JustifyContent justifyContent,
        AlignItems alignItems,
        float flexGrow) {

    public enum Display {
        BLOCK, INLINE, INLINE_BLOCK, FLEX, INLINE_FLEX, NONE,
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
    public enum FlexDirection { ROW, ROW_REVERSE, COLUMN, COLUMN_REVERSE }
    public enum JustifyContent { FLEX_START, CENTER, FLEX_END, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY }
    public enum AlignItems { STRETCH, FLEX_START, CENTER, FLEX_END }

    public RenderStyle {
        if (fontSizePx <= 0) {
            throw new IllegalArgumentException("fontSizePx must be positive");
        }
        if (fontWeight < 100 || fontWeight > 900) {
            throw new IllegalArgumentException("fontWeight outside 100..900");
        }
        if (!Float.isFinite(flexGrow) || flexGrow < 0) {
            throw new IllegalArgumentException("flexGrow must be a finite non-negative number");
        }
    }

    public boolean bold() {
        return fontWeight >= 600;
    }

    public float usedLineHeightPx() {
        return lineHeight < 0 ? -lineHeight * fontSizePx : lineHeight;
    }

    public RenderStyle withDisplay(Display value) {
        return copy(value, width, height, flexGrow);
    }

    public RenderStyle withWidth(RenderLength value) {
        return copy(display, value, height, flexGrow);
    }

    public RenderStyle withHeight(RenderLength value) {
        return copy(display, width, value, flexGrow);
    }

    public RenderStyle withFlexGrow(float value) {
        return copy(display, width, height, value);
    }

    private RenderStyle copy(Display newDisplay,
                             RenderLength newWidth,
                             RenderLength newHeight,
                             float newFlexGrow) {
        return new RenderStyle(newDisplay, position, zIndex, floatMode, clear,
                top, right, bottom, left, fontSizePx, fontFamily, fontWeight, italic,
                lineHeight, color, listStyleType, underline, textDecorationColor, cursor,
                backgroundColor, backgroundImageUrl, backgroundRepeat, backgroundPositionX,
                backgroundPositionY, newWidth, newHeight, minWidth, maxWidth, minHeight,
                maxHeight, boxSizing, margin, autoMargins, padding, borderWidth, borderColor,
                borderStyle, borderRadius, outlineWidth, outlineColor, outlineVisible,
                borderCollapse, textAlign, overflow, verticalAlign, flexDirection,
                justifyContent, alignItems, newFlexGrow);
    }
}

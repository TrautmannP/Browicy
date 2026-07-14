package com.browicy.engine.render;

public record RenderOffset(float value, Unit unit) {
    public static final RenderOffset AUTO = new RenderOffset(0, Unit.AUTO);

    public enum Unit { AUTO, PX, PERCENT, REM, VW, VH }

    public boolean isAuto() { return unit == Unit.AUTO; }

    public float resolve(float percentageBase) {
        if (unit == Unit.REM || unit == Unit.VW || unit == Unit.VH) {
            throw new IllegalStateException("A root-font and viewport context is required for " + unit);
        }
        return unit == Unit.PERCENT ? percentageBase * value / 100f : value;
    }

    public float resolve(float percentageBase,
                         float rootFontSizePx,
                         float viewportWidth,
                         float viewportHeight) {
        return switch (unit) {
            case PERCENT -> percentageBase * value / 100f;
            case REM -> rootFontSizePx * value;
            case VW -> viewportWidth * value / 100f;
            case VH -> viewportHeight * value / 100f;
            case AUTO, PX -> value;
        };
    }
}

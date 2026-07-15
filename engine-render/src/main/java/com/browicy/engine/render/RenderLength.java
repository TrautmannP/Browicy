package com.browicy.engine.render;

public record RenderLength(float value, Unit unit, float offsetPx) {

    public static final RenderLength AUTO = new RenderLength(0, Unit.AUTO);

    public RenderLength(float value, Unit unit) {
        this(value, unit, 0);
    }

    public enum Unit { AUTO, PX, PERCENT, REM, VW, VH }

    public RenderLength {
        if (value < 0) {
            throw new IllegalArgumentException("CSS dimensions cannot be negative");
        }
    }

    public boolean isAuto() {
        return unit == Unit.AUTO;
    }

    public float resolve(float percentageBase) {
        if (unit == Unit.REM || unit == Unit.VW || unit == Unit.VH) {
            throw new IllegalStateException("A root-font and viewport context is required for " + unit);
        }
        return unit == Unit.PERCENT ? percentageBase * value / 100f + offsetPx : value + offsetPx;
    }

    public float resolve(float percentageBase,
                         float rootFontSizePx,
                         float viewportWidth,
                         float viewportHeight) {
        return switch (unit) {
            case PERCENT -> percentageBase * value / 100f + offsetPx;
            case REM -> rootFontSizePx * value + offsetPx;
            case VW -> viewportWidth * value / 100f + offsetPx;
            case VH -> viewportHeight * value / 100f + offsetPx;
            case AUTO, PX -> value + offsetPx;
        };
    }
}

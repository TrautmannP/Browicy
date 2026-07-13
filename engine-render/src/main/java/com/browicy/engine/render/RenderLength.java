package com.browicy.engine.render;

public record RenderLength(float value, Unit unit) {

    public static final RenderLength AUTO = new RenderLength(0, Unit.AUTO);

    public enum Unit { AUTO, PX, PERCENT }

    public RenderLength {
        if (value < 0) {
            throw new IllegalArgumentException("CSS dimensions cannot be negative");
        }
    }

    public boolean isAuto() {
        return unit == Unit.AUTO;
    }

    public float resolve(float percentageBase) {
        return unit == Unit.PERCENT ? percentageBase * value / 100f : value;
    }
}

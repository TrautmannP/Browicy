package com.browicy.engine.render;

public record RenderOffset(float value, Unit unit) {
    public static final RenderOffset AUTO = new RenderOffset(0, Unit.AUTO);

    public enum Unit { AUTO, PX, PERCENT }

    public boolean isAuto() { return unit == Unit.AUTO; }

    public float resolve(float percentageBase) {
        return unit == Unit.PERCENT ? percentageBase * value / 100f : value;
    }
}

package com.browicy.engine.render;

public record HorizontalAutoMargins(boolean left, boolean right) {
    public static final HorizontalAutoMargins NONE = new HorizontalAutoMargins(false, false);
}

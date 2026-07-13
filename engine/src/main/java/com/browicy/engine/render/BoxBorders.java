package com.browicy.engine.render;

/** Whether each border edge uses the currently supported solid style. */
public record BoxBorders(boolean top, boolean right, boolean bottom, boolean left) {

    public static final BoxBorders NONE = new BoxBorders(false, false, false, false);
}

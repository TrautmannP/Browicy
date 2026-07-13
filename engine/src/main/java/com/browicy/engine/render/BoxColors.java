package com.browicy.engine.render;

/** Colors for the four border edges. A null value means current text color. */
public record BoxColors(CssColor top, CssColor right, CssColor bottom, CssColor left) {

    public static final BoxColors CURRENT_COLOR = new BoxColors(null, null, null, null);
}

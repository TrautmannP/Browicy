package com.browicy.engine.render;

public record RenderTree(RenderBox root,
                         float rootFontSizePx,
                         float viewportWidth,
                         float viewportHeight) {

    public RenderTree(RenderBox root) {
        this(root, 16f, 800f, 600f);
    }
}

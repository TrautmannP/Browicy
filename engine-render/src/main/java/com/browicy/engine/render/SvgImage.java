package com.browicy.engine.render;

/** Toolkit-independent serialized inline SVG and its intrinsic dimensions. */
public record SvgImage(String source, float width, float height) {

    public SvgImage {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("SVG source must not be blank");
        }
        if (!(width > 0) || !(height > 0)) {
            throw new IllegalArgumentException("SVG viewBox dimensions must be positive");
        }
    }
}

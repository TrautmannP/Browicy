package com.browicy.engine.render;

import com.browicy.engine.dom.Element;
import java.util.Objects;

/**
 * Übernimmt und liefert das Byte-Array ohne Kopie; Aufrufer dürfen es nicht mutieren.
 * Die defensive Kopie entsteht bereits in {@code BinaryResource.content()}, weiteres
 * Klonen würde bei jedem Render-Tree-Rebuild sämtliche Bilddaten duplizieren.
 */
public record RenderImage(Element source,
                          RenderStyle style,
                          byte[] data,
                          Integer htmlWidth,
                          Integer htmlHeight,
                          SvgImage svg) implements RenderNode {

    public RenderImage {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(style, "style");
    }

    public RenderImage(Element source, RenderStyle style, byte[] data,
                       Integer htmlWidth, Integer htmlHeight) {
        this(source, style, data, htmlWidth, htmlHeight, null);
    }
}

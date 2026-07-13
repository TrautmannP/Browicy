package com.browicy.engine.render;

import com.browicy.engine.dom.TextNode;

/** Inline text whose resolved inherited style remains stable during layout. */
public record RenderTextRun(TextNode source, String text, RenderStyle style) implements RenderNode {
}

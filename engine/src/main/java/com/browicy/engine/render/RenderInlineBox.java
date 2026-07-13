package com.browicy.engine.render;

import com.browicy.engine.dom.Element;
import java.util.List;

/** An inline-level CSS box whose style boundaries survive render-tree construction. */
public record RenderInlineBox(Element source, RenderStyle style, List<RenderNode> children)
        implements RenderNode {

    public RenderInlineBox {
        children = List.copyOf(children);
    }

    public String tagName() {
        return source == null ? "#anonymous-inline" : source.getTagName();
    }
}

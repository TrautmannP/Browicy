package com.browicy.engine.render;

import com.browicy.engine.dom.Element;
import java.util.List;

/** A block-level CSS box containing nested boxes and inline content. */
public record RenderBox(Element source, RenderStyle style, List<RenderNode> children)
        implements RenderNode {

    public RenderBox {
        children = List.copyOf(children);
    }

    public String tagName() {
        return source == null ? "#anonymous" : source.getTagName();
    }
}

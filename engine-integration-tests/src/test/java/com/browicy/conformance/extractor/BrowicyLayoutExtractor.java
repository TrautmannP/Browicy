package com.browicy.conformance.extractor;

import com.browicy.conformance.model.ElementLayoutBox;
import com.browicy.conformance.selector.LayoutSelector;
import com.browicy.engine.css.StyleApplicator;
import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.html.HtmlParser;
import com.browicy.engine.render.BoxEdges;
import com.browicy.engine.render.RenderStyle;
import com.browicy.engine.render.RenderTree;
import com.browicy.engine.render.RenderTreeBuilder;
import com.browicy.ui.render.RenderLayoutEngine;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Builds Browicy's render tree and exposes its laid-out element fragments. */
public final class BrowicyLayoutExtractor {
    private static final String ABOUT_BLANK = "about:blank";

    public Map<String, ElementLayoutBox> extract(String html,
                                                  int viewportWidth,
                                                  int viewportHeight) {
        validateViewport(viewportWidth, viewportHeight);
        Objects.requireNonNull(html, "html");

        Document document = new HtmlParser().parse(html, ABOUT_BLANK);
        new StyleApplicator().apply(document, viewportWidth, viewportHeight);
        RenderTree tree = new RenderTreeBuilder().build(document, viewportWidth, viewportHeight);

        BufferedImage metricsSurface = new BufferedImage(
                viewportWidth, viewportHeight, BufferedImage.TYPE_INT_ARGB);
        RenderLayoutEngine.LayoutResult layout;
        Graphics2D graphics = metricsSurface.createGraphics();
        try {
            layout = new RenderLayoutEngine().layout(tree, viewportWidth,
                    new Insets(0, 0, 0, 0), graphics);
        } finally {
            graphics.dispose();
        }

        Map<String, MutableBox> aggregated = new LinkedHashMap<>();
        for (RenderLayoutEngine.PaintFragment fragment : layout.fragments()) {
            Element element;
            RenderStyle style;
            float x;
            float y;
            float width;
            float height;
            if (fragment instanceof RenderLayoutEngine.BoxFragment box
                    && box.box().source() != null) {
                element = box.box().source();
                style = box.box().style();
                x = box.x();
                y = box.y();
                width = box.width();
                height = box.height();
            } else if (fragment instanceof RenderLayoutEngine.InlineBoxFragment inline
                    && inline.box().source() != null) {
                element = inline.box().source();
                style = inline.box().style();
                x = inline.x();
                y = inline.y();
                width = inline.width();
                height = inline.height();
            } else {
                continue;
            }
            String selector = LayoutSelector.forElement(element);
            aggregated.computeIfAbsent(selector,
                    ignored -> new MutableBox(element, style, x, y, width, height))
                    .include(x, y, width, height);
        }

        Map<String, ElementLayoutBox> result = new LinkedHashMap<>();
        for (Map.Entry<String, MutableBox> entry : aggregated.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toBox(entry.getKey()));
        }
        Element documentElement = document.getDocumentElement();
        if (documentElement != null) {
            String selector = LayoutSelector.forElement(documentElement);
            result.putIfAbsent(selector, new ElementLayoutBox(selector,
                    documentElement.getTagName(), 0, 0, viewportWidth, layout.height(),
                    rootStyles(documentElement)));
        }
        return result;
    }

    private static Map<String, String> rootStyles(Element root) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("display", "block");
        values.put("position", "static");
        values.put("margin", "0px");
        values.put("padding", "0px");
        values.put("border-width", "0px");
        values.put("font-size", "16px");
        values.put("box-sizing", "content-box");
        root.getComputedStyles().forEach((property, value) -> {
            if (values.containsKey(property)) values.put(property, value);
        });
        return values;
    }

    private static Map<String, String> computedStyles(RenderStyle style) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("display", cssEnum(style.display()));
        values.put("position", cssEnum(style.position()));
        values.put("margin", cssQuad(style.margin()));
        values.put("padding", cssQuad(style.padding()));
        values.put("border-width", cssQuad(style.borderWidth()));
        values.put("font-size", cssPx(style.fontSizePx()));
        values.put("font-family", style.fontFamily());
        values.put("font-weight", Integer.toString(style.fontWeight()));
        values.put("line-height", style.lineHeight() < 0
                ? cssPx(style.usedLineHeightPx()) : style.lineHeight() == 0 ? "normal" : cssPx(style.lineHeight()));
        values.put("box-sizing", cssEnum(style.boxSizing()));
        values.put("flex-direction", cssEnum(style.flexDirection()));
        values.put("flex-wrap", cssEnum(style.flexWrap()));
        values.put("justify-content", cssEnum(style.justifyContent()));
        values.put("align-items", cssEnum(style.alignItems()));
        values.put("gap", cssPx(style.rowGapPx()) + " " + cssPx(style.columnGapPx()));
        values.put("grid-template-columns", gridTracks(style.gridTemplateColumns()));
        values.put("grid-template-rows", gridTracks(style.gridTemplateRows()));
        return values;
    }

    private static String gridTracks(java.util.List<RenderStyle.GridTrack> tracks) {
        if (tracks.isEmpty()) {
            return "none";
        }
        return tracks.stream().map(track -> switch (track.type()) {
            case FIXED -> cssPx(track.fixed());
            case PERCENT -> cssNumber(track.fixed()) + "%";
            case FRACTION -> cssNumber(track.fraction()) + "fr";
            case AUTO -> "auto";
            case MINMAX -> "minmax(" + cssPx(track.minFixed()) + ", " + cssPx(track.maxFixed()) + ")";
        }).collect(java.util.stream.Collectors.joining(" "));
    }

    private static String cssEnum(Enum<?> value) {
        return value.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    private static String cssQuad(BoxEdges edges) {
        String top = cssPx(edges.top());
        String right = cssPx(edges.right());
        String bottom = cssPx(edges.bottom());
        String left = cssPx(edges.left());
        if (top.equals(right) && top.equals(bottom) && top.equals(left)) return top;
        if (top.equals(bottom) && right.equals(left)) return top + " " + right;
        if (right.equals(left)) return top + " " + right + " " + bottom;
        return top + " " + right + " " + bottom + " " + left;
    }

    private static String cssPx(float value) {
        return cssNumber(value) + "px";
    }

    private static String cssNumber(float value) {
        if (Math.abs(value) < 0.0001f) return "0";
        return Float.toString(value).replaceFirst("\\.0+$", "");
    }

    private static void validateViewport(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("viewport dimensions must be positive");
        }
    }

    private static final class MutableBox {
        private final Element element;
        private final RenderStyle style;
        private float left;
        private float top;
        private float right;
        private float bottom;

        private MutableBox(Element element, RenderStyle style,
                            float x, float y, float width, float height) {
            this.element = element;
            this.style = style;
            this.left = x;
            this.top = y;
            this.right = x + width;
            this.bottom = y + height;
        }

        private void include(float x, float y, float width, float height) {
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, x + width);
            bottom = Math.max(bottom, y + height);
        }

        private ElementLayoutBox toBox(String selector) {
            return new ElementLayoutBox(selector, element.getTagName(), left, top,
                    Math.max(0, right - left), Math.max(0, bottom - top), computedStyles(style));
        }
    }
}

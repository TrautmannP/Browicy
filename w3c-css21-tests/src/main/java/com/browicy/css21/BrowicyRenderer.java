package com.browicy.css21;

import com.browicy.engine.BrowicyEngine;
import com.browicy.engine.PageSession;
import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.DomSelectorAdapter;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import com.browicy.engine.js.LayoutElementMetrics;
import com.browicy.ui.DomViewPanel;
import com.browicy.ui.render.RenderLayoutMetrics;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BrowicyRenderer implements AutoCloseable {

    private static final List<String> IGNORED_TAGS =
            List.of("html", "head", "title", "meta", "link", "script", "style", "base", "noscript");

    private static final List<String> COMPUTED_STYLE_PROPERTIES = List.of(
            "margin-top", "margin-right", "margin-bottom", "margin-left",
            "padding-top", "padding-right", "padding-bottom", "padding-left",
            "border-top-width", "border-right-width", "border-bottom-width", "border-left-width",
            "width", "height", "display", "position", "float", "overflow-x", "overflow-y",
            "font-size", "line-height", "text-align", "vertical-align", "white-space",
            "background-color", "color", "z-index", "opacity");

    private final BrowicyEngine engine;

    public BrowicyRenderer() {
        engine = new BrowicyEngine();
    }

    public BufferedImage screenshot(String url, int width, int height) {
        try (PageSession session = engine.loadPageSession(url, ignored -> { })) {
            session.awaitResources();
            DomViewPanel panel = new DomViewPanel(session);
            try {
                return panel.captureScreenshot(width, height, false);
            } finally {
                panel.dispose();
            }
        }
    }

    public List<ElementGeometrySnapshot> extractGeometry(String url, int width, int height) {
        try (PageSession session = engine.loadPageSession(url, ignored -> { })) {
            session.awaitResources();
            RenderLayoutMetrics metrics = new RenderLayoutMetrics(session.styleSheets(), width, height);
            Document document = session.document();
            List<ElementGeometrySnapshot> result = new ArrayList<>();
            collectGeometry(document, metrics, width, height, result);
            return result;
        }
    }

    private void collectGeometry(Node node, RenderLayoutMetrics metrics, int width, int height,
                                 List<ElementGeometrySnapshot> result) {
        for (Node child : node.getChildren()) {
            if (child instanceof Element element) {
                String tag = element.getTagName().toLowerCase(Locale.ROOT);
                Map<String, String> styles = element.getComputedStyles();
                boolean hidden = styles != null
                        && ("none".equalsIgnoreCase(styles.getOrDefault("display", ""))
                                || "hidden".equalsIgnoreCase(styles.getOrDefault("visibility", "")));
                if (!IGNORED_TAGS.contains(tag) && !hidden) {
                    LayoutElementMetrics m = metrics.metricsFor(element);
                    ElementGeometrySnapshot.Rect rect;
                    if ("html".equals(tag)) {
                        rect = new ElementGeometrySnapshot.Rect(0, 0, width, height);
                    } else if (m.rendered()) {
                        rect = new ElementGeometrySnapshot.Rect(m.left(), m.top(), m.width(), m.height());
                    } else {
                        rect = ElementGeometrySnapshot.Rect.ZERO;
                    }
                    Map<String, String> computedStyles = new LinkedHashMap<>();
                    for (String property : COMPUTED_STYLE_PROPERTIES) {
                        computedStyles.put(property, metrics.resolvedValue(element, property));
                    }
                    result.add(new ElementGeometrySnapshot(
                            computePath(element), tag, element.getId(),
                            element.getAttribute("class"), rect, computedStyles));
                }
                if (!hidden) {
                    collectGeometry(element, metrics, width, height, result);
                }
            } else {
                collectGeometry(child, metrics, width, height, result);
            }
        }
    }

    private String computePath(Element element) {
        StringBuilder sb = new StringBuilder(element.getTagName().toLowerCase(Locale.ROOT));
        String id = element.getId();
        if (id != null && !id.isBlank()) {
            sb.append('#').append(id.strip());
        } else {
            String className = element.getAttribute("class");
            if (className != null && !className.isBlank()) {
                sb.append('.').append(String.join(".", className.trim().split("\\s+")));
            }
        }
        Element parent = DomSelectorAdapter.INSTANCE.parentElement(element);
        if (parent != null) {
            int siblingIndex = 0;
            for (Element sibling : parent.getChildElements()) {
                if (sibling.getTagName().equalsIgnoreCase(element.getTagName())) {
                    siblingIndex++;
                }
                if (sibling == element) {
                    break;
                }
            }
            int index = Math.max(1, siblingIndex);
            return computePath(parent) + " > " + sb + ":nth-of-type(" + index + ")";
        }
        return sb.toString();
    }

    @Override
    public void close() {
        engine.close();
    }
}

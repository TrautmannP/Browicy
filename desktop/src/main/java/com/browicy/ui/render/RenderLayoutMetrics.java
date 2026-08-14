package com.browicy.ui.render;

import com.browicy.engine.css.StyleApplicator;
import com.browicy.engine.css.StyleSheetRegistry;
import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.js.LayoutElementMetrics;
import com.browicy.engine.js.LayoutMetricsAccess;
import com.browicy.engine.render.BoxEdges;
import com.browicy.engine.render.RenderStyle;
import com.browicy.engine.render.RenderTree;
import com.browicy.engine.render.RenderTreeBuilder;
import com.browicy.ui.render.RenderLayoutEngine.BoxFragment;
import com.browicy.ui.render.RenderLayoutEngine.InlineBoxFragment;
import com.browicy.ui.render.RenderLayoutEngine.LayoutResult;
import com.browicy.ui.render.RenderLayoutEngine.PaintFragment;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Objects;

/**
 * Produktions-Implementierung von {@link LayoutMetricsAccess} auf Basis der
 * {@link RenderLayoutEngine}: führt bei jeder Anfrage synchron einen vollständigen
 * Style-/Render-Tree-/Layout-Durchlauf gegen das aktuelle Dokument aus (Forced
 * Reflow wie in Browsern) und liefert die berechneten Pixelwerte.
 */
public final class RenderLayoutMetrics implements LayoutMetricsAccess {

    private final StyleSheetRegistry styleSheets;
    private final int viewportWidth;
    private final int viewportHeight;
    private final RenderLayoutEngine layoutEngine = new RenderLayoutEngine();

    public RenderLayoutMetrics(int viewportWidth, int viewportHeight) {
        this(new StyleSheetRegistry(), viewportWidth, viewportHeight);
    }

    public RenderLayoutMetrics(StyleSheetRegistry styleSheets,
                               int viewportWidth,
                               int viewportHeight) {
        this.styleSheets = Objects.requireNonNull(styleSheets, "styleSheets");
        this.viewportWidth = Math.max(1, viewportWidth);
        this.viewportHeight = Math.max(1, viewportHeight);
    }

    @Override
    public synchronized LayoutElementMetrics metricsFor(Element element) {
        Objects.requireNonNull(element, "element");
        Document document = element.getOwnerDocument();
        if (document == null) {
            return LayoutElementMetrics.ZERO;
        }
        if (element == document.getDocumentElement()) {
            // Das html-Element erzeugt keine eigene Box, deckt aber den Viewport ab.
            return new LayoutElementMetrics(true, 0, 0, viewportWidth, viewportHeight,
                    0, 0, 0, 0, 0, 0, 0, 0);
        }
        Pass pass = pass(document);
        ElementLayout laidOut = findElementLayout(element, pass.layout());
        if (laidOut == null) {
            return LayoutElementMetrics.ZERO;
        }
        BoxEdges border = laidOut.style().borderWidth();
        BoxEdges padding = laidOut.style().padding();
        return new LayoutElementMetrics(true,
                laidOut.left(), laidOut.top(),
                laidOut.right() - laidOut.left(), laidOut.bottom() - laidOut.top(),
                border.left(), border.right(), border.top(), border.bottom(),
                padding.left(), padding.right(), padding.top(), padding.bottom());
    }

    @Override
    public synchronized String resolvedValue(Element element, String property) {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(property, "property");
        Document document = element.getOwnerDocument();
        if (document == null) {
            return null;
        }
        String normalized = property.toLowerCase(Locale.ROOT);
        Pass pass = pass(document);
        return switch (normalized) {
            case "font-size" -> cssPx(fontSizeOf(element, document, pass));
            case "width" -> {
                LayoutElementMetrics metrics = metricsFrom(element, document, pass);
                yield metrics == null ? null : cssPx(metrics.contentWidth());
            }
            case "height" -> {
                LayoutElementMetrics metrics = metricsFrom(element, document, pass);
                yield metrics == null ? null : cssPx(metrics.contentHeight());
            }
            case "padding-top" -> cssPx(edge(pass, element, document, style -> style.padding().top()));
            case "padding-right" -> cssPx(edge(pass, element, document, style -> style.padding().right()));
            case "padding-bottom" -> cssPx(edge(pass, element, document, style -> style.padding().bottom()));
            case "padding-left" -> cssPx(edge(pass, element, document, style -> style.padding().left()));
            case "border-top-width" -> cssPx(edge(pass, element, document, style -> style.borderWidth().top()));
            case "border-right-width" -> cssPx(edge(pass, element, document, style -> style.borderWidth().right()));
            case "border-bottom-width" -> cssPx(edge(pass, element, document, style -> style.borderWidth().bottom()));
            case "border-left-width" -> cssPx(edge(pass, element, document, style -> style.borderWidth().left()));
            case "margin-left", "margin-right" -> horizontalMargin(element, document, pass, normalized);
            case "margin-top", "margin-bottom" -> verticalMargin(element, document, pass, normalized);
            case "top", "left", "right", "bottom" -> offset(element, document, pass, normalized);
            default -> null;
        };
    }

    /**
     * Aggregierte Border-Box eines Elements aus den Layout-Fragmenten
     * (Vereinigung aller Fragmente, wie getBoundingClientRect es verlangt).
     */
    private ElementLayout findElementLayout(Element element, LayoutResult layout) {
        MutableBox box = null;
        RenderStyle style = null;
        for (PaintFragment fragment : layout.fragments()) {
            Element source;
            RenderStyle fragmentStyle;
            float x;
            float y;
            float width;
            float height;
            if (fragment instanceof BoxFragment boxFragment
                    && boxFragment.box().source() != null) {
                source = boxFragment.box().source();
                fragmentStyle = boxFragment.box().style();
                x = boxFragment.x();
                y = boxFragment.y();
                width = boxFragment.width();
                height = boxFragment.height();
            } else if (fragment instanceof InlineBoxFragment inline
                    && inline.box().source() != null) {
                source = inline.box().source();
                fragmentStyle = inline.box().style();
                x = inline.x();
                y = inline.y();
                width = inline.width();
                height = inline.height();
            } else {
                continue;
            }
            if (source != element) {
                continue;
            }
            if (box == null) {
                box = new MutableBox(x, y, width, height);
                style = fragmentStyle;
            } else {
                box.include(x, y, width, height);
            }
        }
        return box == null ? null : new ElementLayout(
                box.left, box.top, box.right, box.bottom, style);
    }

    /** Erste Box-Style eines Elements im Layout (für Padding/Border/Font-Werte). */
    private RenderStyle findStyle(Element element, LayoutResult layout) {
        ElementLayout found = findElementLayout(element, layout);
        return found == null ? null : found.style();
    }

    private LayoutElementMetrics metricsFrom(Element element, Document document, Pass pass) {
        if (element == document.getDocumentElement()) {
            return new LayoutElementMetrics(true, 0, 0, viewportWidth, viewportHeight,
                    0, 0, 0, 0, 0, 0, 0, 0);
        }
        ElementLayout laidOut = findElementLayout(element, pass.layout());
        if (laidOut == null) {
            return null;
        }
        BoxEdges border = laidOut.style().borderWidth();
        BoxEdges padding = laidOut.style().padding();
        return new LayoutElementMetrics(true,
                laidOut.left(), laidOut.top(),
                laidOut.right() - laidOut.left(), laidOut.bottom() - laidOut.top(),
                border.left(), border.right(), border.top(), border.bottom(),
                padding.left(), padding.right(), padding.top(), padding.bottom());
    }

    private Float edge(Pass pass, Element element, Document document,
                       java.util.function.Function<RenderStyle, Float> side) {
        if (element == document.getDocumentElement()) {
            return 0f;
        }
        RenderStyle style = findStyle(element, pass.layout());
        return style == null ? null : side.apply(style);
    }

    /** Used Value horizontaler Margins: Position relativ zur Content-Box des Eltern-Elements. */
    private String horizontalMargin(Element element, Document document, Pass pass,
                                    String normalized) {
        if (isPositioned(element)) {
            return null;
        }
        LayoutElementMetrics own = metricsFrom(element, document, pass);
        if (own == null) {
            return null;
        }
        Element parent = parentElement(element);
        LayoutElementMetrics parentMetrics = parent == null ? null : metricsFrom(parent, document, pass);
        float parentContentLeft;
        float parentContentRight;
        if (parentMetrics == null || !parentMetrics.rendered()) {
            parentContentLeft = 0;
            parentContentRight = viewportWidth;
        } else {
            parentContentLeft = parentMetrics.left() + parentMetrics.borderLeft()
                    + parentMetrics.paddingLeft();
            parentContentRight = parentMetrics.left() + parentMetrics.width()
                    - parentMetrics.borderRight() - parentMetrics.paddingRight();
        }
        float value = normalized.equals("margin-left")
                ? own.left() - parentContentLeft
                : parentContentRight - own.right();
        return cssPx(value);
    }

    /**
     * Vertikale Margins kollabieren zwischen Geschwistern und Eltern/Kind;
     * die Geometrie ist daher nicht eindeutig ableitbar. Stattdessen wird der
     * deklarierte Wert gegen die eigene Fontgröße aufgelöst (wie der Builder
     * es tut); {@code auto} wird zum Used Value {@code 0px}.
     */
    private String verticalMargin(Element element, Document document, Pass pass,
                                  String normalized) {
        String cascade = cascade(element, normalized);
        if (cascade == null || "auto".equals(cascade)) {
            return "0px";
        }
        Float resolved = resolveLengthValue(cascade, fontSizeOf(element, document, pass),
                pass.tree().rootFontSizePx());
        return resolved == null ? null : cssPx(resolved);
    }

    private String offset(Element element, Document document, Pass pass, String normalized) {
        String position = positionOf(element);
        if ("static".equals(position)) {
            return "auto";
        }
        String cascade = cascade(element, normalized);
        if ("auto".equals(cascade)) {
            return "auto";
        }
        LayoutElementMetrics own = metricsFrom(element, document, pass);
        if (own == null) {
            return null;
        }
        if ("absolute".equals(position) || "fixed".equals(position)) {
            Element containingBlock = containingBlockElement(element);
            LayoutElementMetrics cb = containingBlock == null ? null
                    : metricsFrom(containingBlock, document, pass);
            float cbLeft = cb == null ? 0 : cb.left();
            float cbTop = cb == null ? 0 : cb.top();
            float cbBorderLeft = cb == null ? 0 : cb.borderLeft();
            float cbBorderTop = cb == null ? 0 : cb.borderTop();
            float cbRight = cb == null ? viewportWidth
                    : cb.left() + cb.width() - cb.borderRight();
            float cbBottom = cb == null ? viewportHeight
                    : cb.top() + cb.height() - cb.borderBottom();
            float used = switch (normalized) {
                case "left" -> own.left() - (cbLeft + cbBorderLeft);
                case "top" -> own.top() - (cbTop + cbBorderTop);
                case "right" -> cbRight - own.right();
                default -> cbBottom - own.bottom();
            };
            return cssPx(used);
        }
        // relative / sticky: deklarierter Versatz gegen die eigene Fontgröße.
        Float resolved = resolveLengthValue(cascade, fontSizeOf(element, document, pass),
                pass.tree().rootFontSizePx());
        return resolved == null ? null : cssPx(resolved);
    }

    /** Element-Fontgröße aus dem Layout, sonst aus der Kaskade gegen Root/Parent. */
    private float fontSizeOf(Element element, Document document, Pass pass) {
        if (element == document.getDocumentElement()) {
            return pass.tree().rootFontSizePx();
        }
        RenderStyle style = findStyle(element, pass.layout());
        if (style != null) {
            return style.fontSizePx();
        }
        String cascade = cascade(element, "font-size");
        if (cascade == null) {
            return pass.tree().rootFontSizePx();
        }
        Element parent = parentElement(element);
        float parentFontSize = parent == null ? pass.tree().rootFontSizePx()
                : fontSizeOf(parent, document, pass);
        Float resolved = resolveLengthValue(cascade, parentFontSize,
                pass.tree().rootFontSizePx());
        return resolved == null ? pass.tree().rootFontSizePx() : resolved;
    }

    /** Deklarierter Wert einer Eigenschaft aus der Kaskade (inkl. Inline-Styles). */
    private static String cascade(Element element, String property) {
        return element.getComputedStyles().get(property);
    }

    private static String positionOf(Element element) {
        return element.getComputedStyles().getOrDefault("position", "static");
    }

    private static boolean isPositioned(Element element) {
        String position = positionOf(element);
        return "absolute".equals(position) || "fixed".equals(position);
    }

    private static Element parentElement(Element element) {
        return element.getParent() instanceof Element parent ? parent : null;
    }

    /** Nächster positionierter Vorfahre = Containing Block (Padding-Box) für absolute Elemente. */
    private static Element containingBlockElement(Element element) {
        for (com.browicy.engine.dom.Node node = element.getParent(); node != null;
             node = node.getParent()) {
            if (node instanceof Element ancestor
                    && !"static".equals(positionOf(ancestor))) {
                return ancestor;
            }
        }
        return null;
    }

    /**
     * Längenwert der Kaskade gegen Font- und Root-Fontgröße auflösen
     * (px/em/rem; % nur für Margin/Padding, konsistent zum RenderTreeBuilder).
     */
    private static Float resolveLengthValue(String value, float fontSize, float rootFontSize) {
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if ("0".equals(normalized)) {
            return 0f;
        }
        try {
            if (normalized.endsWith("rem")) {
                return Float.parseFloat(normalized.substring(0, normalized.length() - 3))
                        * rootFontSize;
            }
            if (normalized.endsWith("em")) {
                return Float.parseFloat(normalized.substring(0, normalized.length() - 2))
                        * fontSize;
            }
            if (normalized.endsWith("%")) {
                return Float.parseFloat(normalized.substring(0, normalized.length() - 1))
                        * fontSize / 100f;
            }
            if (normalized.endsWith("px")) {
                return Float.parseFloat(normalized.substring(0, normalized.length() - 2));
            }
            try {
                return Float.parseFloat(normalized);
            } catch (NumberFormatException notPlain) {
                return null;
            }
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static String cssPx(float value) {
        if (Math.abs(value) < 0.0001f) {
            return "0px";
        }
        String text = Float.toString(value);
        if (text.endsWith(".0")) {
            text = text.substring(0, text.length() - 2);
        }
        return text + "px";
    }

    private Pass pass(Document document) {
        synchronized (document) {
            new StyleApplicator().apply(document, styleSheets, viewportWidth, viewportHeight);
        }
        RenderTree tree = new RenderTreeBuilder().build(document, viewportWidth, viewportHeight);
        BufferedImage metricsImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = metricsImage.createGraphics();
        try {
            LayoutResult layout = layoutEngine.layout(
                    tree, viewportWidth, new Insets(0, 0, 0, 0), graphics);
            return new Pass(tree, layout);
        } finally {
            graphics.dispose();
        }
    }

    private record Pass(RenderTree tree, LayoutResult layout) {
    }

    private record ElementLayout(float left, float top, float right, float bottom,
                                 RenderStyle style) {
    }

    private static final class MutableBox {
        private float left;
        private float top;
        private float right;
        private float bottom;

        private MutableBox(float x, float y, float width, float height) {
            left = x;
            top = y;
            right = x + width;
            bottom = y + height;
        }

        private void include(float x, float y, float width, float height) {
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, x + width);
            bottom = Math.max(bottom, y + height);
        }
    }
}

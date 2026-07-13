package com.browicy.engine.render;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import com.browicy.engine.dom.TextNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Converts DOM nodes and computed declarations into an immutable render tree. */
public final class RenderTreeBuilder {

    private static final float DEFAULT_FONT_SIZE = 16f;
    private static final CssColor DEFAULT_COLOR = CssColor.rgb(0x1c1b1f);

    private static final Set<String> HIDDEN_TAGS =
            Set.of("head", "title", "meta", "link", "script", "style");
    private static final Set<String> BLOCK_TAGS = Set.of(
            "html", "body", "p", "div", "section", "article", "main", "header", "footer",
            "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote");

    public RenderTree build(Document document) {
        Element rootElement = document.getBody();
        if (rootElement == null) {
            rootElement = document.getDocumentElement();
        }
        RenderStyle initial = new RenderStyle(
                RenderStyle.Display.BLOCK,
                DEFAULT_FONT_SIZE,
                400,
                false,
                DEFAULT_COLOR,
                null,
                BoxEdges.ZERO,
                BoxEdges.ZERO,
                BoxEdges.ZERO,
                BoxColors.CURRENT_COLOR,
                BoxBorders.NONE);
        if (rootElement == null) {
            return new RenderTree(new RenderBox(null, initial, List.of()));
        }
        RenderStyle rootStyle = resolveStyle(rootElement, initial);
        if (rootStyle.display() == RenderStyle.Display.NONE) {
            return new RenderTree(new RenderBox(rootElement, initial, List.of()));
        }
        if (rootStyle.display() != RenderStyle.Display.BLOCK) {
            rootStyle = copyWithDisplay(rootStyle, RenderStyle.Display.BLOCK);
        }
        return new RenderTree(buildBox(rootElement, rootStyle));
    }

    private RenderBox buildBox(Element element, RenderStyle style) {
        List<RenderNode> children = new ArrayList<>();
        collectChildren(element, style, children);
        return new RenderBox(element, style, children);
    }

    private void collectChildren(Node parent, RenderStyle parentStyle, List<RenderNode> output) {
        for (Node child : parent.getChildren()) {
            if (child instanceof TextNode text) {
                if (!text.getData().isEmpty()) {
                    output.add(new RenderTextRun(text, text.getData(), parentStyle));
                }
                continue;
            }
            if (!(child instanceof Element element) || HIDDEN_TAGS.contains(element.getTagName())) {
                continue;
            }

            RenderStyle style = resolveStyle(element, parentStyle);
            if (style.display() == RenderStyle.Display.NONE) {
                continue;
            }
            if ("br".equals(element.getTagName())) {
                output.add(new RenderLineBreak(style));
            } else if (style.display() == RenderStyle.Display.BLOCK) {
                output.add(buildBox(element, style));
            } else {
                collectChildren(element, style, output);
            }
        }
    }

    private static RenderStyle resolveStyle(Element element, RenderStyle parent) {
        String tag = element.getTagName();
        Map<String, String> declarations = element.getComputedStyles();

        RenderStyle.Display display = defaultDisplay(tag);
        float fontSize = defaultFontSize(tag, parent.fontSizePx());
        int fontWeight = defaultFontWeight(tag, parent.fontWeight());
        boolean italic = defaultItalic(tag, parent.italic());
        CssColor color = parent.color();
        CssColor background = null;
        BoxEdges margin = defaultMargin(tag);
        BoxEdges padding = BoxEdges.ZERO;
        BoxEdges borderWidth = BoxEdges.ZERO;
        BoxColors borderColor = BoxColors.CURRENT_COLOR;
        BoxBorders borderStyle = BoxBorders.NONE;

        if (declarations.containsKey("display")) {
            display = switch (declarations.get("display")) {
                case "block" -> RenderStyle.Display.BLOCK;
                case "none" -> RenderStyle.Display.NONE;
                default -> RenderStyle.Display.INLINE;
            };
        }
        if (declarations.containsKey("font-size")) {
            fontSize = resolveLength(declarations.get("font-size"), parent.fontSizePx(), fontSize);
        }
        if (declarations.containsKey("font-weight")) {
            fontWeight = parseFontWeight(declarations.get("font-weight"), parent.fontWeight());
        }
        if (declarations.containsKey("font-style")) {
            italic = !"normal".equals(declarations.get("font-style"));
        }
        CssColor declaredColor = CssColor.parse(declarations.get("color"));
        if (declaredColor != null) {
            color = declaredColor;
        }
        CssColor declaredBackground = CssColor.parse(declarations.get("background-color"));
        if (declaredBackground != null && !declaredBackground.isTransparent()) {
            background = declaredBackground;
        }

        margin = resolveEdges(declarations, "margin", fontSize, margin);
        padding = nonNegative(resolveEdges(declarations, "padding", fontSize, padding));
        borderWidth = nonNegative(resolveEdges(declarations, "border", fontSize, borderWidth, "-width"));
        borderColor = resolveBorderColors(declarations, color);
        borderStyle = resolveBorderStyles(declarations);
        borderWidth = effectiveBorderWidths(borderWidth, borderStyle);

        return new RenderStyle(display, fontSize, fontWeight, italic, color, background,
                margin, padding, borderWidth, borderColor, borderStyle);
    }

    private static RenderStyle.Display defaultDisplay(String tag) {
        if (HIDDEN_TAGS.contains(tag)) {
            return RenderStyle.Display.NONE;
        }
        return BLOCK_TAGS.contains(tag) ? RenderStyle.Display.BLOCK : RenderStyle.Display.INLINE;
    }

    private static float defaultFontSize(String tag, float inherited) {
        return switch (tag) {
            case "h1" -> 32f;
            case "h2" -> 26f;
            case "h3" -> 22f;
            case "h4", "h5", "h6" -> 17f;
            default -> inherited;
        };
    }

    private static int defaultFontWeight(String tag, int inherited) {
        return switch (tag) {
            case "h1", "h2", "h3", "h4", "h5", "h6", "strong", "b" -> 700;
            default -> inherited;
        };
    }

    private static boolean defaultItalic(String tag, boolean inherited) {
        return "em".equals(tag) || "i".equals(tag) || inherited;
    }

    private static BoxEdges defaultMargin(String tag) {
        return switch (tag) {
            case "p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol" ->
                    new BoxEdges(0, 0, 8, 0);
            case "blockquote" -> new BoxEdges(8, 24, 8, 24);
            default -> BoxEdges.ZERO;
        };
    }

    private static int parseFontWeight(String value, int inherited) {
        return switch (value) {
            case "normal" -> 400;
            case "bold" -> 700;
            case "bolder" -> Math.min(900, inherited + 300);
            case "lighter" -> Math.max(100, inherited - 300);
            default -> Integer.parseInt(value);
        };
    }

    private static BoxEdges resolveEdges(Map<String, String> declarations,
                                         String prefix,
                                         float emBase,
                                         BoxEdges defaults) {
        return resolveEdges(declarations, prefix, emBase, defaults, "");
    }

    private static BoxEdges resolveEdges(Map<String, String> declarations,
                                         String prefix,
                                         float emBase,
                                         BoxEdges defaults,
                                         String suffix) {
        float top = resolveLength(declarations.get(prefix + "-top" + suffix), emBase, defaults.top());
        float right = resolveLength(declarations.get(prefix + "-right" + suffix), emBase, defaults.right());
        float bottom = resolveLength(declarations.get(prefix + "-bottom" + suffix), emBase, defaults.bottom());
        float left = resolveLength(declarations.get(prefix + "-left" + suffix), emBase, defaults.left());
        return new BoxEdges(top, right, bottom, left);
    }

    private static BoxEdges effectiveBorderWidths(BoxEdges widths, BoxBorders styles) {
        return new BoxEdges(
                styles.top() ? widths.top() : 0,
                styles.right() ? widths.right() : 0,
                styles.bottom() ? widths.bottom() : 0,
                styles.left() ? widths.left() : 0);
    }

    private static BoxColors resolveBorderColors(Map<String, String> declarations, CssColor currentColor) {
        return new BoxColors(
                colorOrCurrent(declarations.get("border-top-color"), currentColor),
                colorOrCurrent(declarations.get("border-right-color"), currentColor),
                colorOrCurrent(declarations.get("border-bottom-color"), currentColor),
                colorOrCurrent(declarations.get("border-left-color"), currentColor));
    }

    private static CssColor colorOrCurrent(String value, CssColor currentColor) {
        CssColor parsed = CssColor.parse(value);
        return parsed == null ? currentColor : parsed;
    }

    private static BoxBorders resolveBorderStyles(Map<String, String> declarations) {
        return new BoxBorders(
                "solid".equals(declarations.get("border-top-style")),
                "solid".equals(declarations.get("border-right-style")),
                "solid".equals(declarations.get("border-bottom-style")),
                "solid".equals(declarations.get("border-left-style")));
    }

    private static float resolveLength(String value, float emBase, float fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if ("0".equals(normalized)) {
            return 0;
        }
        try {
            float number = Float.parseFloat(normalized.substring(0, normalized.length() - 2));
            return normalized.endsWith("em") ? number * emBase : number;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static BoxEdges nonNegative(BoxEdges edges) {
        return new BoxEdges(
                Math.max(0, edges.top()),
                Math.max(0, edges.right()),
                Math.max(0, edges.bottom()),
                Math.max(0, edges.left()));
    }

    private static RenderStyle copyWithDisplay(RenderStyle style, RenderStyle.Display display) {
        return new RenderStyle(display, style.fontSizePx(), style.fontWeight(), style.italic(),
                style.color(), style.backgroundColor(), style.margin(), style.padding(),
                style.borderWidth(), style.borderColor(), style.borderStyle());
    }
}

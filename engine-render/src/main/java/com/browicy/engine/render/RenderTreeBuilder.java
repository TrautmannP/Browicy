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
import java.util.function.Function;

public final class RenderTreeBuilder {

    private static final float DEFAULT_FONT_SIZE = 16f;
    private static final float DEFAULT_VIEWPORT_WIDTH = 800f;
    private static final float DEFAULT_VIEWPORT_HEIGHT = 600f;
    private static final CssColor DEFAULT_COLOR = CssColor.rgb(0x1c1b1f);

    private static final Set<String> HIDDEN_TAGS =
            Set.of("head", "title", "meta", "link", "script", "style");
    private static final Set<String> BLOCK_TAGS = Set.of(
            "html", "body", "p", "div", "section", "article", "main", "header", "footer",
            "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote",
            "address", "aside", "center", "details", "dialog", "dl", "dt", "dd",
            "fieldset", "figcaption", "figure", "form", "hr", "nav", "pre",
            "table", "thead", "tbody", "tfoot", "tr", "td", "th");

    private final Function<Element, byte[]> imageData;
    private float rootFontSizePx = DEFAULT_FONT_SIZE;
    private float viewportWidth = DEFAULT_VIEWPORT_WIDTH;
    private float viewportHeight = DEFAULT_VIEWPORT_HEIGHT;
    private Element documentElement;

    public RenderTreeBuilder() {
        this(ignored -> null);
    }

    public RenderTreeBuilder(Function<Element, byte[]> imageData) {
        this.imageData = java.util.Objects.requireNonNull(imageData, "imageData");
    }

    public RenderTree build(Document document) {
        return build(document, DEFAULT_VIEWPORT_WIDTH, DEFAULT_VIEWPORT_HEIGHT);
    }

    public RenderTree build(Document document, float viewportWidth, float viewportHeight) {
        this.viewportWidth = Math.max(1, viewportWidth);
        this.viewportHeight = Math.max(1, viewportHeight);
        documentElement = document.getDocumentElement();
        rootFontSizePx = resolveRootFontSize(documentElement);

        Element rootElement = document.getBody();
        if (rootElement == null) {
            rootElement = documentElement;
        }
        RenderStyle initial = new RenderStyle(
                RenderStyle.Display.BLOCK,
                RenderStyle.Position.STATIC,
                RenderOffset.AUTO, RenderOffset.AUTO, RenderOffset.AUTO, RenderOffset.AUTO,
                DEFAULT_FONT_SIZE,
                400,
                false,
                DEFAULT_COLOR,
                null,
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderLength.AUTO,
                BoxEdges.ZERO,
                HorizontalAutoMargins.NONE,
                BoxEdges.ZERO,
                BoxEdges.ZERO,
                BoxColors.CURRENT_COLOR,
                BoxBorders.NONE,
                RenderStyle.TextAlign.LEFT,
                RenderStyle.Overflow.VISIBLE,
                RenderStyle.VerticalAlign.BASELINE);
        if (rootElement == null) {
            return renderTree(new RenderBox(null, initial, List.of()));
        }
        RenderStyle inherited = initial;
        if (documentElement != null && rootElement != documentElement) {
            inherited = resolveStyle(documentElement, initial);
        }
        RenderStyle rootStyle = resolveStyle(rootElement, inherited);
        if (rootStyle.display() == RenderStyle.Display.NONE) {
            return renderTree(new RenderBox(rootElement, initial, List.of()));
        }
        if (rootStyle.display() != RenderStyle.Display.BLOCK) {
            rootStyle = copyWithDisplay(rootStyle, RenderStyle.Display.BLOCK);
        }
        return renderTree(buildBox(rootElement, rootStyle));
    }

    private RenderTree renderTree(RenderBox root) {
        return new RenderTree(root, rootFontSizePx, viewportWidth, viewportHeight);
    }

    private float resolveRootFontSize(Element root) {
        if (root == null) {
            return DEFAULT_FONT_SIZE;
        }
        float fallback = defaultFontSize(root.getTagName(), DEFAULT_FONT_SIZE);
        return resolveLength(root.getComputedStyles().get("font-size"),
                DEFAULT_FONT_SIZE, DEFAULT_FONT_SIZE, fallback);
    }

    private RenderBox buildBox(Element element, RenderStyle style) {
        List<RenderNode> children = new ArrayList<>();
        collectChildren(element, style, children);
        return new RenderBox(element, style, wrapMixedInlineContent(children, style));
    }

    private RenderInlineBox buildInlineBox(Element element, RenderStyle style) {
        List<RenderNode> children = new ArrayList<>();
        collectChildren(element, style, children);
        return new RenderInlineBox(element, style, children);
    }

    private RenderInlineBlock buildInlineBlock(Element element, RenderStyle style) {
        return new RenderInlineBlock(buildBox(element, style));
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
            if (style.position() == RenderStyle.Position.ABSOLUTE
                    && style.display() != RenderStyle.Display.BLOCK
                    && !"br".equals(element.getTagName())
                    && !"img".equals(element.getTagName())) {
                style = copyWithDisplay(style, RenderStyle.Display.BLOCK);
            }
            if ("br".equals(element.getTagName())) {
                output.add(new RenderLineBreak(style));
            } else if ("img".equals(element.getTagName())) {
                output.add(new RenderImage(
                        element, style, imageData.apply(element),
                        positiveIntegerAttribute(element, "width"),
                        positiveIntegerAttribute(element, "height")));
            } else if (style.display() == RenderStyle.Display.BLOCK) {
                output.add(buildBox(element, style));
            } else if (style.display() == RenderStyle.Display.INLINE_BLOCK) {
                output.add(buildInlineBlock(element, style));
            } else {
                output.add(buildInlineBox(element, style));
            }
        }
    }

    private static Integer positiveIntegerAttribute(Element element, String name) {
        String value = element.getAttribute(name);
        if (value == null || value.isBlank()) return null;
        try {
            int parsed = Integer.parseInt(value.strip());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static List<RenderNode> wrapMixedInlineContent(List<RenderNode> children,
                                                            RenderStyle parentStyle) {
        boolean containsBlock = children.stream().anyMatch(RenderBox.class::isInstance);
        boolean containsInline = children.stream().anyMatch(child -> !(child instanceof RenderBox));
        if (!containsBlock || !containsInline) {
            return List.copyOf(children);
        }

        List<RenderNode> normalized = new ArrayList<>();
        List<RenderNode> inlineRun = new ArrayList<>();
        for (RenderNode child : children) {
            if (child instanceof RenderBox) {
                flushAnonymousBlock(inlineRun, parentStyle, normalized);
                normalized.add(child);
            } else {
                inlineRun.add(child);
            }
        }
        flushAnonymousBlock(inlineRun, parentStyle, normalized);
        return List.copyOf(normalized);
    }

    private static void flushAnonymousBlock(List<RenderNode> inlineRun,
                                            RenderStyle parentStyle,
                                            List<RenderNode> output) {
        if (inlineRun.isEmpty()) {
            return;
        }
        if (inlineRun.stream().allMatch(node -> node instanceof RenderTextRun run
                && run.text().isBlank())) {
            inlineRun.clear();
            return;
        }
        output.add(new RenderBox(null, anonymousBlockStyle(parentStyle), List.copyOf(inlineRun)));
        inlineRun.clear();
    }

    private static RenderStyle anonymousBlockStyle(RenderStyle inherited) {
        return new RenderStyle(
                RenderStyle.Display.BLOCK,
                RenderStyle.Position.STATIC,
                RenderOffset.AUTO, RenderOffset.AUTO, RenderOffset.AUTO, RenderOffset.AUTO,
                inherited.fontSizePx(),
                inherited.fontWeight(),
                inherited.italic(),
                inherited.color(),
                null,
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderLength.AUTO,
                BoxEdges.ZERO,
                HorizontalAutoMargins.NONE,
                BoxEdges.ZERO,
                BoxEdges.ZERO,
                BoxColors.CURRENT_COLOR,
                BoxBorders.NONE,
                inherited.textAlign(),
                RenderStyle.Overflow.VISIBLE,
                RenderStyle.VerticalAlign.BASELINE);
    }

    private RenderStyle resolveStyle(Element element, RenderStyle parent) {
        String tag = element.getTagName();
        Map<String, String> declarations = element.getComputedStyles();

        RenderStyle.Display display = defaultDisplay(tag);
        RenderStyle.Position position = RenderStyle.Position.STATIC;
        RenderOffset top = RenderOffset.AUTO;
        RenderOffset right = RenderOffset.AUTO;
        RenderOffset bottom = RenderOffset.AUTO;
        RenderOffset left = RenderOffset.AUTO;
        float fontSize = defaultFontSize(tag, parent.fontSizePx());
        int fontWeight = defaultFontWeight(tag, parent.fontWeight());
        boolean italic = defaultItalic(tag, parent.italic());
        CssColor color = parent.color();
        CssColor background = null;
        RenderLength width = RenderLength.AUTO;
        RenderLength height = RenderLength.AUTO;
        RenderLength minWidth = RenderLength.AUTO;
        RenderLength maxWidth = RenderLength.AUTO;
        RenderLength minHeight = RenderLength.AUTO;
        RenderLength maxHeight = RenderLength.AUTO;
        BoxEdges margin = defaultMargin(tag);
        HorizontalAutoMargins autoMargins = HorizontalAutoMargins.NONE;
        BoxEdges padding = BoxEdges.ZERO;
        BoxEdges borderWidth = BoxEdges.ZERO;
        BoxColors borderColor = BoxColors.CURRENT_COLOR;
        BoxBorders borderStyle = BoxBorders.NONE;
        RenderStyle.TextAlign textAlign = parent.textAlign();
        RenderStyle.Overflow overflow = RenderStyle.Overflow.VISIBLE;
        RenderStyle.VerticalAlign verticalAlign = RenderStyle.VerticalAlign.BASELINE;

        if (declarations.containsKey("display")) {
            display = switch (declarations.get("display")) {
                case "block" -> RenderStyle.Display.BLOCK;
                case "inline-block" -> RenderStyle.Display.INLINE_BLOCK;
                case "none" -> RenderStyle.Display.NONE;
                default -> RenderStyle.Display.INLINE;
            };
        }
        position = switch (declarations.getOrDefault("position", "static")) {
            case "relative" -> RenderStyle.Position.RELATIVE;
            case "absolute" -> RenderStyle.Position.ABSOLUTE;
            default -> RenderStyle.Position.STATIC;
        };
        if (declarations.containsKey("font-size")) {
            float remBase = element == documentElement ? DEFAULT_FONT_SIZE : rootFontSizePx;
            fontSize = resolveLength(
                    declarations.get("font-size"), parent.fontSizePx(), remBase, fontSize);
        }
        if (declarations.containsKey("font-weight")) {
            fontWeight = parseFontWeight(declarations.get("font-weight"), parent.fontWeight());
        }
        if (declarations.containsKey("font-style")) {
            italic = !"normal".equals(declarations.get("font-style"));
        }
        width = resolveDimension(declarations.get("width"), fontSize);
        height = resolveDimension(declarations.get("height"), fontSize);
        minWidth = resolveDimension(declarations.get("min-width"), fontSize);
        maxWidth = resolveDimension(declarations.get("max-width"), fontSize);
        minHeight = resolveDimension(declarations.get("min-height"), fontSize);
        maxHeight = resolveDimension(declarations.get("max-height"), fontSize);
        top = resolveOffset(declarations.get("top"), fontSize);
        right = resolveOffset(declarations.get("right"), fontSize);
        bottom = resolveOffset(declarations.get("bottom"), fontSize);
        left = resolveOffset(declarations.get("left"), fontSize);
        if (declarations.containsKey("text-align")) {
            textAlign = switch (declarations.get("text-align")) {
                case "center" -> RenderStyle.TextAlign.CENTER;
                case "right" -> RenderStyle.TextAlign.RIGHT;
                default -> RenderStyle.TextAlign.LEFT;
            };
        }
        overflow = switch (declarations.getOrDefault("overflow", "visible")) {
            case "hidden" -> RenderStyle.Overflow.HIDDEN;
            case "auto" -> RenderStyle.Overflow.AUTO;
            case "scroll" -> RenderStyle.Overflow.SCROLL;
            default -> RenderStyle.Overflow.VISIBLE;
        };
        verticalAlign = switch (declarations.getOrDefault("vertical-align", "baseline")) {
            case "top", "text-top" -> RenderStyle.VerticalAlign.TOP;
            case "middle" -> RenderStyle.VerticalAlign.MIDDLE;
            case "bottom", "text-bottom" -> RenderStyle.VerticalAlign.BOTTOM;
            default -> RenderStyle.VerticalAlign.BASELINE;
        };
        CssColor declaredColor = CssColor.parse(declarations.get("color"));
        if (declaredColor != null) {
            color = declaredColor;
        }
        CssColor declaredBackground = CssColor.parse(declarations.get("background-color"));
        if (declaredBackground != null && !declaredBackground.isTransparent()) {
            background = declaredBackground;
        }

        margin = resolveEdges(declarations, "margin", fontSize, margin);
        autoMargins = new HorizontalAutoMargins(
                "auto".equals(declarations.get("margin-left")),
                "auto".equals(declarations.get("margin-right")));
        padding = nonNegative(resolveEdges(declarations, "padding", fontSize, padding));
        borderWidth = nonNegative(resolveEdges(declarations, "border", fontSize, borderWidth, "-width"));
        borderColor = resolveBorderColors(declarations, color);
        borderStyle = resolveBorderStyles(declarations);
        borderWidth = effectiveBorderWidths(borderWidth, borderStyle);

        return new RenderStyle(display, position, top, right, bottom, left,
                fontSize, fontWeight, italic, color, background,
                width, height, minWidth, maxWidth, minHeight, maxHeight, margin,
                autoMargins, padding, borderWidth, borderColor, borderStyle, textAlign,
                overflow, verticalAlign);
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

    private BoxEdges resolveEdges(Map<String, String> declarations,
                                  String prefix,
                                  float emBase,
                                  BoxEdges defaults) {
        return resolveEdges(declarations, prefix, emBase, defaults, "");
    }

    private BoxEdges resolveEdges(Map<String, String> declarations,
                                  String prefix,
                                  float emBase,
                                  BoxEdges defaults,
                                  String suffix) {
        float top = resolveLength(declarations.get(prefix + "-top" + suffix), emBase,
                rootFontSizePx, defaults.top());
        float right = resolveLength(declarations.get(prefix + "-right" + suffix), emBase,
                rootFontSizePx, defaults.right());
        float bottom = resolveLength(declarations.get(prefix + "-bottom" + suffix), emBase,
                rootFontSizePx, defaults.bottom());
        float left = resolveLength(declarations.get(prefix + "-left" + suffix), emBase,
                rootFontSizePx, defaults.left());
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

    private float resolveLength(String value, float emBase, float remBase, float fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if ("0".equals(normalized)) {
            return 0;
        }
        try {
            ParsedLength parsed = parseLength(normalized);
            return switch (parsed.unit()) {
                case "em" -> parsed.value() * emBase;
                case "rem" -> parsed.value() * remBase;
                case "vw" -> parsed.value() * viewportWidth / 100f;
                case "vh" -> parsed.value() * viewportHeight / 100f;
                default -> parsed.value();
            };
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private RenderLength resolveDimension(String value, float emBase) {
        if (value == null || "auto".equals(value)) {
            return RenderLength.AUTO;
        }
        if ("0".equals(value)) {
            return new RenderLength(0, RenderLength.Unit.PX);
        }
        try {
            if (value.endsWith("%")) {
                return new RenderLength(Float.parseFloat(value.substring(0, value.length() - 1)),
                        RenderLength.Unit.PERCENT);
            }
            ParsedLength parsed = parseLength(value);
            return switch (parsed.unit()) {
                case "em" -> new RenderLength(parsed.value() * emBase, RenderLength.Unit.PX);
                case "rem" -> new RenderLength(parsed.value(), RenderLength.Unit.REM);
                case "vw" -> new RenderLength(parsed.value(), RenderLength.Unit.VW);
                case "vh" -> new RenderLength(parsed.value(), RenderLength.Unit.VH);
                default -> new RenderLength(parsed.value(), RenderLength.Unit.PX);
            };
        } catch (RuntimeException ignored) {
            return RenderLength.AUTO;
        }
    }

    private RenderOffset resolveOffset(String value, float emBase) {
        if (value == null || "auto".equals(value)) return RenderOffset.AUTO;
        if ("0".equals(value)) return new RenderOffset(0, RenderOffset.Unit.PX);
        try {
            if (value.endsWith("%")) {
                return new RenderOffset(Float.parseFloat(value.substring(0, value.length() - 1)),
                        RenderOffset.Unit.PERCENT);
            }
            ParsedLength parsed = parseLength(value);
            return switch (parsed.unit()) {
                case "em" -> new RenderOffset(parsed.value() * emBase, RenderOffset.Unit.PX);
                case "rem" -> new RenderOffset(parsed.value(), RenderOffset.Unit.REM);
                case "vw" -> new RenderOffset(parsed.value(), RenderOffset.Unit.VW);
                case "vh" -> new RenderOffset(parsed.value(), RenderOffset.Unit.VH);
                default -> new RenderOffset(parsed.value(), RenderOffset.Unit.PX);
            };
        } catch (RuntimeException ignored) {
            return RenderOffset.AUTO;
        }
    }

    private static ParsedLength parseLength(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String unit : List.of("rem", "px", "em", "vw", "vh")) {
            if (normalized.endsWith(unit)) {
                float number = Float.parseFloat(
                        normalized.substring(0, normalized.length() - unit.length()));
                return new ParsedLength(number, unit);
            }
        }
        throw new IllegalArgumentException("Unsupported CSS length: " + value);
    }

    private record ParsedLength(float value, String unit) {
    }

    private static BoxEdges nonNegative(BoxEdges edges) {
        return new BoxEdges(
                Math.max(0, edges.top()),
                Math.max(0, edges.right()),
                Math.max(0, edges.bottom()),
                Math.max(0, edges.left()));
    }

    private static RenderStyle copyWithDisplay(RenderStyle style, RenderStyle.Display display) {
        return new RenderStyle(display, style.position(), style.top(), style.right(),
                style.bottom(), style.left(), style.fontSizePx(), style.fontWeight(), style.italic(),
                style.color(), style.backgroundColor(), style.width(), style.height(),
                style.minWidth(), style.maxWidth(), style.minHeight(), style.maxHeight(),
                style.margin(), style.autoMargins(), style.padding(), style.borderWidth(),
                style.borderColor(), style.borderStyle(), style.textAlign(), style.overflow(),
                style.verticalAlign());
    }
}

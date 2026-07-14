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
                0,
                RenderStyle.FloatMode.NONE,
                RenderStyle.Clear.NONE,
                RenderOffset.AUTO, RenderOffset.AUTO, RenderOffset.AUTO, RenderOffset.AUTO,
                DEFAULT_FONT_SIZE,
                "sans-serif",
                400,
                false,
                0,
                DEFAULT_COLOR,
                RenderStyle.ListStyleType.DISC,
                false,
                DEFAULT_COLOR,
                RenderStyle.Cursor.DEFAULT,
                null,
                null,
                RenderStyle.BackgroundRepeat.REPEAT,
                RenderStyle.BackgroundPositionX.LEFT,
                RenderStyle.BackgroundPositionY.TOP,
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderStyle.BoxSizing.CONTENT_BOX,
                BoxEdges.ZERO,
                HorizontalAutoMargins.NONE,
                BoxEdges.ZERO,
                BoxEdges.ZERO,
                BoxColors.CURRENT_COLOR,
                BoxBorders.NONE,
                0,
                0,
                DEFAULT_COLOR,
                false,
                RenderStyle.BorderCollapse.SEPARATE,
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
            } else if (isBlockContainer(style.display())) {
                output.add(buildBox(element, style));
            } else if (style.display() == RenderStyle.Display.INLINE_BLOCK
                    || style.display() == RenderStyle.Display.INLINE_TABLE) {
                output.add(buildInlineBlock(element, style));
            } else {
                output.add(buildInlineBox(element, style));
            }
        }
    }

    private static boolean isBlockContainer(RenderStyle.Display display) {
        return switch (display) {
            case BLOCK, TABLE, TABLE_ROW_GROUP, TABLE_HEADER_GROUP, TABLE_FOOTER_GROUP,
                 TABLE_ROW, TABLE_CELL, TABLE_COLUMN_GROUP, TABLE_COLUMN, TABLE_CAPTION -> true;
            default -> false;
        };
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
                0,
                RenderStyle.FloatMode.NONE,
                RenderStyle.Clear.NONE,
                RenderOffset.AUTO, RenderOffset.AUTO, RenderOffset.AUTO, RenderOffset.AUTO,
                inherited.fontSizePx(),
                inherited.fontFamily(),
                inherited.fontWeight(),
                inherited.italic(),
                inherited.lineHeight(),
                inherited.color(),
                inherited.listStyleType(),
                inherited.underline(),
                inherited.textDecorationColor(),
                inherited.cursor(),
                null,
                null,
                RenderStyle.BackgroundRepeat.REPEAT,
                RenderStyle.BackgroundPositionX.LEFT,
                RenderStyle.BackgroundPositionY.TOP,
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderStyle.BoxSizing.CONTENT_BOX,
                BoxEdges.ZERO,
                HorizontalAutoMargins.NONE,
                BoxEdges.ZERO,
                BoxEdges.ZERO,
                BoxColors.CURRENT_COLOR,
                BoxBorders.NONE,
                0,
                0,
                inherited.color(),
                false,
                inherited.borderCollapse(),
                inherited.textAlign(),
                RenderStyle.Overflow.VISIBLE,
                RenderStyle.VerticalAlign.BASELINE);
    }

    private RenderStyle resolveStyle(Element element, RenderStyle parent) {
        String tag = element.getTagName();
        Map<String, String> declarations = element.getComputedStyles();

        RenderStyle.Display display = defaultDisplay(tag);
        RenderStyle.Position position = RenderStyle.Position.STATIC;
        int zIndex = 0;
        RenderStyle.FloatMode floatMode = RenderStyle.FloatMode.NONE;
        RenderStyle.Clear clear = RenderStyle.Clear.NONE;
        RenderOffset top = RenderOffset.AUTO;
        RenderOffset right = RenderOffset.AUTO;
        RenderOffset bottom = RenderOffset.AUTO;
        RenderOffset left = RenderOffset.AUTO;
        float fontSize = defaultFontSize(tag, parent.fontSizePx());
        String fontFamily = parent.fontFamily();
        int fontWeight = defaultFontWeight(tag, parent.fontWeight());
        boolean italic = defaultItalic(tag, parent.italic());
        float lineHeight = parent.lineHeight();
        CssColor color = parent.color();
        RenderStyle.ListStyleType listStyleType = parent.listStyleType();
        boolean underline = parent.underline();
        CssColor textDecorationColor = parent.textDecorationColor();
        RenderStyle.Cursor cursor = parent.cursor();
        CssColor background = null;
        String backgroundImageUrl = null;
        RenderStyle.BackgroundRepeat backgroundRepeat = RenderStyle.BackgroundRepeat.REPEAT;
        RenderStyle.BackgroundPositionX backgroundPositionX = RenderStyle.BackgroundPositionX.LEFT;
        RenderStyle.BackgroundPositionY backgroundPositionY = RenderStyle.BackgroundPositionY.TOP;
        RenderLength width = RenderLength.AUTO;
        RenderLength height = RenderLength.AUTO;
        RenderLength minWidth = RenderLength.AUTO;
        RenderLength maxWidth = RenderLength.AUTO;
        RenderLength minHeight = RenderLength.AUTO;
        RenderLength maxHeight = RenderLength.AUTO;
        RenderStyle.BoxSizing boxSizing = RenderStyle.BoxSizing.CONTENT_BOX;
        BoxEdges margin = defaultMargin(tag);
        HorizontalAutoMargins autoMargins = HorizontalAutoMargins.NONE;
        BoxEdges padding = BoxEdges.ZERO;
        BoxEdges borderWidth = BoxEdges.ZERO;
        BoxColors borderColor = BoxColors.CURRENT_COLOR;
        BoxBorders borderStyle = BoxBorders.NONE;
        float borderRadius = 0;
        float outlineWidth = 0;
        CssColor outlineColor = color;
        boolean outlineVisible = false;
        RenderStyle.BorderCollapse borderCollapse = RenderStyle.BorderCollapse.SEPARATE;
        RenderStyle.TextAlign textAlign = parent.textAlign();
        RenderStyle.Overflow overflow = RenderStyle.Overflow.VISIBLE;
        RenderStyle.VerticalAlign verticalAlign = RenderStyle.VerticalAlign.BASELINE;

        if (declarations.containsKey("display")) {
            display = switch (declarations.get("display")) {
                case "block" -> RenderStyle.Display.BLOCK;
                case "inline-block" -> RenderStyle.Display.INLINE_BLOCK;
                case "none" -> RenderStyle.Display.NONE;
                case "table" -> RenderStyle.Display.TABLE;
                case "inline-table" -> RenderStyle.Display.INLINE_TABLE;
                case "table-row-group" -> RenderStyle.Display.TABLE_ROW_GROUP;
                case "table-header-group" -> RenderStyle.Display.TABLE_HEADER_GROUP;
                case "table-footer-group" -> RenderStyle.Display.TABLE_FOOTER_GROUP;
                case "table-row" -> RenderStyle.Display.TABLE_ROW;
                case "table-cell" -> RenderStyle.Display.TABLE_CELL;
                case "table-column-group" -> RenderStyle.Display.TABLE_COLUMN_GROUP;
                case "table-column" -> RenderStyle.Display.TABLE_COLUMN;
                case "table-caption" -> RenderStyle.Display.TABLE_CAPTION;
                default -> RenderStyle.Display.INLINE;
            };
        }
        if ("collapse".equals(declarations.get("border-collapse"))) {
            borderCollapse = RenderStyle.BorderCollapse.COLLAPSE;
        }
        position = switch (declarations.getOrDefault("position", "static")) {
            case "relative" -> RenderStyle.Position.RELATIVE;
            case "absolute" -> RenderStyle.Position.ABSOLUTE;
            default -> RenderStyle.Position.STATIC;
        };
        zIndex = parseZIndex(declarations.get("z-index"));
        cursor = switch (declarations.getOrDefault("cursor",
                cursor.name().toLowerCase(Locale.ROOT))) {
            case "pointer" -> RenderStyle.Cursor.POINTER;
            case "text" -> RenderStyle.Cursor.TEXT;
            default -> RenderStyle.Cursor.DEFAULT;
        };
        floatMode = switch (declarations.getOrDefault("float", "none")) {
            case "left" -> RenderStyle.FloatMode.LEFT;
            case "right" -> RenderStyle.FloatMode.RIGHT;
            default -> RenderStyle.FloatMode.NONE;
        };
        clear = switch (declarations.getOrDefault("clear", "none")) {
            case "left" -> RenderStyle.Clear.LEFT;
            case "right" -> RenderStyle.Clear.RIGHT;
            case "both" -> RenderStyle.Clear.BOTH;
            default -> RenderStyle.Clear.NONE;
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
        if (declarations.containsKey("font-family")) {
            fontFamily = firstFontFamily(declarations.get("font-family"));
        }
        if (declarations.containsKey("line-height")) {
            lineHeight = resolveLineHeight(declarations.get("line-height"), fontSize);
        }
        width = resolveDimension(declarations.get("width"), fontSize);
        height = resolveDimension(declarations.get("height"), fontSize);
        minWidth = resolveDimension(declarations.get("min-width"), fontSize);
        maxWidth = resolveDimension(declarations.get("max-width"), fontSize);
        minHeight = resolveDimension(declarations.get("min-height"), fontSize);
        maxHeight = resolveDimension(declarations.get("max-height"), fontSize);
        if ("border-box".equals(declarations.get("box-sizing"))) {
            boxSizing = RenderStyle.BoxSizing.BORDER_BOX;
        }
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
        listStyleType = switch (declarations.getOrDefault("list-style-type",
                listStyleType.name().toLowerCase(Locale.ROOT))) {
            case "none" -> RenderStyle.ListStyleType.NONE;
            case "circle" -> RenderStyle.ListStyleType.CIRCLE;
            case "square" -> RenderStyle.ListStyleType.SQUARE;
            default -> RenderStyle.ListStyleType.DISC;
        };
        if (declarations.containsKey("text-decoration-line")) {
            underline = "underline".equals(declarations.get("text-decoration-line"));
        }
        CssColor declaredDecorationColor = CssColor.parse(declarations.get("text-decoration-color"));
        if (declaredDecorationColor != null) textDecorationColor = declaredDecorationColor;
        else if (underline && !parent.underline()) textDecorationColor = color;
        CssColor declaredBackground = CssColor.parse(declarations.get("background-color"));
        if (declaredBackground != null && !declaredBackground.isTransparent()) {
            background = declaredBackground;
        }
        backgroundImageUrl = backgroundImageUrl(declarations.get("background-image"));
        backgroundRepeat = switch (declarations.getOrDefault("background-repeat", "repeat")) {
            case "repeat-x" -> RenderStyle.BackgroundRepeat.REPEAT_X;
            case "repeat-y" -> RenderStyle.BackgroundRepeat.REPEAT_Y;
            case "no-repeat" -> RenderStyle.BackgroundRepeat.NO_REPEAT;
            default -> RenderStyle.BackgroundRepeat.REPEAT;
        };
        backgroundPositionX = switch (declarations.getOrDefault("background-position-x", "left")) {
            case "center" -> RenderStyle.BackgroundPositionX.CENTER;
            case "right" -> RenderStyle.BackgroundPositionX.RIGHT;
            default -> RenderStyle.BackgroundPositionX.LEFT;
        };
        backgroundPositionY = switch (declarations.getOrDefault("background-position-y", "top")) {
            case "center" -> RenderStyle.BackgroundPositionY.CENTER;
            case "bottom" -> RenderStyle.BackgroundPositionY.BOTTOM;
            default -> RenderStyle.BackgroundPositionY.TOP;
        };

        margin = resolveEdges(declarations, "margin", fontSize, margin);
        autoMargins = new HorizontalAutoMargins(
                "auto".equals(declarations.get("margin-left")),
                "auto".equals(declarations.get("margin-right")));
        padding = nonNegative(resolveEdges(declarations, "padding", fontSize, padding));
        borderWidth = nonNegative(resolveEdges(declarations, "border", fontSize, borderWidth, "-width"));
        borderColor = resolveBorderColors(declarations, color);
        borderStyle = resolveBorderStyles(declarations);
        borderWidth = effectiveBorderWidths(borderWidth, borderStyle);
        borderRadius = Math.max(0, resolveLength(
                declarations.get("border-radius"), fontSize, rootFontSizePx, 0));
        outlineWidth = Math.max(0, resolveLength(
                declarations.get("outline-width"), fontSize, rootFontSizePx, 0));
        outlineColor = colorOrCurrent(declarations.get("outline-color"), color);
        outlineVisible = "solid".equals(declarations.get("outline-style")) && outlineWidth > 0;

        return new RenderStyle(display, position, zIndex, floatMode, clear, top, right, bottom, left,
                fontSize, fontFamily, fontWeight, italic, lineHeight, color, listStyleType,
                underline, textDecorationColor, cursor, background,
                backgroundImageUrl, backgroundRepeat, backgroundPositionX, backgroundPositionY,
                width, height, minWidth, maxWidth, minHeight, maxHeight, boxSizing, margin,
                autoMargins, padding, borderWidth, borderColor, borderStyle, borderRadius,
                outlineWidth, outlineColor, outlineVisible, borderCollapse, textAlign,
                overflow, verticalAlign);
    }

    private static RenderStyle.Display defaultDisplay(String tag) {
        if (HIDDEN_TAGS.contains(tag)) {
            return RenderStyle.Display.NONE;
        }
        return switch (tag) {
            case "table" -> RenderStyle.Display.TABLE;
            case "thead" -> RenderStyle.Display.TABLE_HEADER_GROUP;
            case "tbody" -> RenderStyle.Display.TABLE_ROW_GROUP;
            case "tfoot" -> RenderStyle.Display.TABLE_FOOTER_GROUP;
            case "tr" -> RenderStyle.Display.TABLE_ROW;
            case "td", "th" -> RenderStyle.Display.TABLE_CELL;
            case "colgroup" -> RenderStyle.Display.TABLE_COLUMN_GROUP;
            case "col" -> RenderStyle.Display.TABLE_COLUMN;
            case "caption" -> RenderStyle.Display.TABLE_CAPTION;
            default -> BLOCK_TAGS.contains(tag)
                    ? RenderStyle.Display.BLOCK : RenderStyle.Display.INLINE;
        };
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

    private static int parseZIndex(String value) {
        if (value == null || "auto".equals(value)) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private float resolveLineHeight(String value, float fontSize) {
        if (value == null || value.equals("normal")) return 0;
        try {
            if (value.endsWith("%")) {
                return -Float.parseFloat(value.substring(0, value.length() - 1)) / 100f;
            }
            if (value.matches("(?:\\d+(?:\\.\\d+)?|\\.\\d+)")) {
                return -Float.parseFloat(value);
            }
            return resolveLength(value, fontSize, rootFontSizePx, 0);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String firstFontFamily(String value) {
        if (value == null || value.isBlank()) return "sans-serif";
        String family = value.split(",", 2)[0].strip();
        if (family.length() >= 2 && (family.startsWith("\"") && family.endsWith("\"")
                || family.startsWith("'") && family.endsWith("'"))) {
            family = family.substring(1, family.length() - 1);
        }
        return family;
    }

    private static String backgroundImageUrl(String value) {
        if (value == null || value.equalsIgnoreCase("none")) return null;
        return CssUrl.parseSingle(value);
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
        return new RenderStyle(display, style.position(), style.zIndex(), style.floatMode(), style.clear(), style.top(), style.right(),
                style.bottom(), style.left(), style.fontSizePx(), style.fontFamily(), style.fontWeight(), style.italic(), style.lineHeight(),
                style.color(), style.listStyleType(), style.underline(), style.textDecorationColor(),
                style.cursor(),
                style.backgroundColor(), style.backgroundImageUrl(),
                style.backgroundRepeat(), style.backgroundPositionX(), style.backgroundPositionY(),
                style.width(), style.height(),
                style.minWidth(), style.maxWidth(), style.minHeight(), style.maxHeight(),
                style.boxSizing(), style.margin(), style.autoMargins(), style.padding(),
                style.borderWidth(),
                style.borderColor(), style.borderStyle(), style.borderRadius(), style.outlineWidth(),
                style.outlineColor(), style.outlineVisible(), style.borderCollapse(), style.textAlign(), style.overflow(),
                style.verticalAlign());
    }
}

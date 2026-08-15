package com.browicy.engine.render;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import com.browicy.engine.dom.TextNode;
import java.nio.charset.StandardCharsets;
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
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderLength.AUTO,
                Float.NaN,
                RenderStyle.ObjectFit.FILL,
                RenderStyle.BoxSizing.CONTENT_BOX,
                BoxEdges.ZERO,
                HorizontalAutoMargins.NONE,
                BoxEdges.ZERO,
                BoxEdges.ZERO,
                BoxColors.CURRENT_COLOR,
                BoxBorders.NONE,
                CornerRadii.ZERO,
                java.util.List.of(),
                Transform.NONE,
                0,
                DEFAULT_COLOR,
                false,
                0,
                true,
                true,
                RenderStyle.BorderCollapse.SEPARATE,
                RenderStyle.TextAlign.LEFT,
                RenderStyle.TextTransform.NONE,
                RenderStyle.WhiteSpace.NORMAL,
                0,
                RenderStyle.TextOverflow.CLIP,
                RenderStyle.Overflow.VISIBLE,
                RenderStyle.VerticalAlign.BASELINE,
                RenderStyle.FlexDirection.ROW,
                RenderStyle.FlexWrap.NOWRAP,
                RenderStyle.JustifyContent.FLEX_START,
                RenderStyle.AlignItems.STRETCH,
                RenderStyle.AlignSelf.AUTO,
                RenderStyle.AlignContent.NORMAL,
                0,
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                null,
                RenderStyle.GridAutoFlow.ROW,
                RenderStyle.GridLine.AUTO,
                RenderStyle.GridLine.AUTO,
                RenderStyle.GridLine.AUTO,
                RenderStyle.GridLine.AUTO,
                0,
                0,
                null,
                0,
                1,
                RenderLength.AUTO,
                1);
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
        if (rootStyle.display() == RenderStyle.Display.INLINE_FLEX) {
            rootStyle = copyWithDisplay(rootStyle, RenderStyle.Display.FLEX);
        } else if (rootStyle.display() != RenderStyle.Display.BLOCK
                && rootStyle.display() != RenderStyle.Display.FLEX) {
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
        List<RenderNode> normalized = isFlexContainer(style.display())
                ? wrapFlexItems(children, style)
                : wrapMixedInlineContent(children, style);
        return new RenderBox(element, style, normalized);
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
        if (parent instanceof Element element) {
            collectGeneratedContent(element, "before", parentStyle, output);
            if ("input".equals(element.getTagName())
                    && !"hidden".equals(element.getAttribute("type"))) {
                addInputText(element, parentStyle, output);
            }
        }
        for (Node child : parent.getChildren()) {
            if (child instanceof TextNode text) {
                if (!text.getData().isEmpty()) {
                    output.add(new RenderTextRun(text,
                            transformText(text.getData(), parentStyle.textTransform()), parentStyle));
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
            if (style.display() == RenderStyle.Display.CONTENTS) {
                if (style.position() == RenderStyle.Position.STATIC) {
                    // display:contents erzeugt keine Box; Kinder nehmen direkt am Layout
                    // des umgebenden Containers teil (Block-, Flex- oder Grid-Kontext).
                    collectChildren(element, parentStyle, output);
                    continue;
                }
                // Positioniertes display:contents erzeugt gemäß CSS-Transform-Regel
                // eine Box; display wird zu block aufgelöst.
                style = copyWithDisplay(style, RenderStyle.Display.BLOCK);
            }
            if (style.position() == RenderStyle.Position.ABSOLUTE
                    && style.display() == RenderStyle.Display.INLINE_FLEX) {
                style = copyWithDisplay(style, RenderStyle.Display.FLEX);
            } else if (style.position() == RenderStyle.Position.ABSOLUTE
                    && style.display() != RenderStyle.Display.BLOCK
                    && !"br".equals(element.getTagName())
                    && !"img".equals(element.getTagName())) {
                style = copyWithDisplay(style, RenderStyle.Display.BLOCK);
            }
            if ("br".equals(element.getTagName())) {
                output.add(new RenderLineBreak(style));
            } else if ("img".equals(element.getTagName())) {
                byte[] data = imageData.apply(element);
                SvgImage svg = parseExternalSvg(data);
                output.add(new RenderImage(
                        element, style, svg == null ? data : null,
                        positiveIntegerAttribute(element, "width"),
                        positiveIntegerAttribute(element, "height"), svg));
            } else if ("svg".equals(element.getTagName())) {
                output.add(new RenderImage(element, style, null,
                        positiveIntegerAttribute(element, "width"),
                        positiveIntegerAttribute(element, "height"), parseSvg(element, style)));
            } else if (isFlexContainer(parentStyle.display())
                    || isGridContainer(parentStyle.display())) {
                RenderStyle itemStyle = switch (style.display()) {
                    case INLINE -> copyWithDisplay(style, RenderStyle.Display.BLOCK);
                    case INLINE_FLEX -> copyWithDisplay(style, RenderStyle.Display.FLEX);
                    case INLINE_GRID -> copyWithDisplay(style, RenderStyle.Display.GRID);
                    default -> style;
                };
                output.add(buildBox(element, itemStyle));
            } else if (isBlockContainer(style.display())) {
                output.add(buildBox(element, style));
            } else if (style.display() == RenderStyle.Display.INLINE_BLOCK
                    || style.display() == RenderStyle.Display.INLINE_TABLE
                    || style.display() == RenderStyle.Display.INLINE_FLEX
                    || style.display() == RenderStyle.Display.INLINE_GRID) {
                output.add(buildInlineBlock(element, style));
            } else if (containsBlockLevelDescendant(element, style)) {
                // HTML5 erlaubt Block-Elemente in Inline-Elementen (z. B. <a><div>):
                // gemäß CSS-Blockifizierung wird das Inline-Element dann als Block behandelt.
                style = copyWithDisplay(style, RenderStyle.Display.BLOCK);
                output.add(buildBox(element, style));
            } else {
                output.add(buildInlineBox(element, style));
            }
        }
        if (parent instanceof Element element) {
            collectGeneratedContent(element, "after", parentStyle, output);
        }
    }

    private void addInputText(Element element,
                              RenderStyle parentStyle,
                              List<RenderNode> output) {
        String value = element.getAttribute("value");
        boolean fromValue = value != null && !value.isBlank();
        String text = fromValue ? value : element.getAttribute("placeholder");
        if (text == null || text.isBlank()) {
            return;
        }
        RenderStyle textStyle = fromValue ? parentStyle
                : placeholderTextStyle(element, parentStyle);
        output.add(new RenderTextRun(null, text, textStyle));
    }

    private RenderStyle placeholderTextStyle(Element element,
                                             RenderStyle parentStyle) {
        Map<String, String> declarations = new java.util.HashMap<>(
                element.getPseudoComputedStyles("placeholder"));
        // Browser-Default: grauer Platzhalter, falls die Seite keine Farbe setzt.
        declarations.putIfAbsent("color", "#767676");
        return resolveStyle("span", false, declarations, parentStyle);
    }

    private void collectGeneratedContent(Element element,
                                         String pseudo,
                                         RenderStyle parentStyle,
                                         List<RenderNode> output) {
        Map<String, String> declarations = element.getPseudoComputedStyles(pseudo);
        String text = generatedContent(element, declarations.get("content"));
        if (text == null) return;
        RenderStyle style = resolveStyle("span", false, declarations, parentStyle);
        if (style.display() == RenderStyle.Display.NONE) return;
        RenderTextRun run = new RenderTextRun(null,
                transformText(text, style.textTransform()), style);
        if (isFlexContainer(parentStyle.display())) {
            RenderStyle itemStyle = switch (style.display()) {
                case INLINE -> copyWithDisplay(style, RenderStyle.Display.BLOCK);
                case INLINE_FLEX -> copyWithDisplay(style, RenderStyle.Display.FLEX);
                default -> style;
            };
            output.add(new RenderBox(null, itemStyle, List.of(run)));
        } else if (isBlockContainer(style.display())) {
            output.add(new RenderBox(null, style, List.of(run)));
        } else if (style.display() == RenderStyle.Display.INLINE_BLOCK
                || style.display() == RenderStyle.Display.INLINE_TABLE
                || style.display() == RenderStyle.Display.INLINE_FLEX) {
            output.add(new RenderInlineBlock(new RenderBox(null, style, List.of(run))));
        } else {
            output.add(new RenderInlineBox(null, style, List.of(run)));
        }
    }

    private static String generatedContent(Element element, String content) {
        if (content == null || content.equalsIgnoreCase("normal")
                || content.equalsIgnoreCase("none")) return null;
        String stripped = content.strip();
        if (stripped.regionMatches(true, 0, "attr(", 0, 5) && stripped.endsWith(")")) {
            String value = element.getAttribute(stripped.substring(5, stripped.length() - 1).strip());
            return value == null ? "" : value;
        }
        if (stripped.length() >= 2 && (stripped.charAt(0) == '\'' || stripped.charAt(0) == '"')
                && stripped.charAt(stripped.length() - 1) == stripped.charAt(0)) {
            return decodeCssString(stripped.substring(1, stripped.length() - 1));
        }
        return null;
    }

    private static String decodeCssString(String source) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < source.length();) {
            char current = source.charAt(index++);
            if (current != '\\' || index >= source.length()) {
                result.append(current);
                continue;
            }
            int start = index;
            int end = start;
            while (end < source.length() && end - start < 6
                    && Character.digit(source.charAt(end), 16) >= 0) end++;
            if (end > start) {
                result.appendCodePoint(Integer.parseInt(source.substring(start, end), 16));
                index = end;
                if (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
            } else {
                result.append(source.charAt(index++));
            }
        }
        return result.toString();
    }

    private static boolean isBlockContainer(RenderStyle.Display display) {
        return switch (display) {
            case BLOCK, FLEX, GRID, TABLE, TABLE_ROW_GROUP, TABLE_HEADER_GROUP, TABLE_FOOTER_GROUP,
                 TABLE_ROW, TABLE_CELL, TABLE_COLUMN_GROUP, TABLE_COLUMN, TABLE_CAPTION -> true;
            default -> false;
        };
    }

    private boolean containsBlockLevelDescendant(Element element, RenderStyle parentStyle) {
        for (Node child : element.getChildren()) {
            if (!(child instanceof Element childElement)
                    || HIDDEN_TAGS.contains(childElement.getTagName())) {
                continue;
            }
            String tag = childElement.getTagName();
            if ("br".equals(tag)) {
                continue;
            }
            RenderStyle childStyle = resolveStyle(childElement, parentStyle);
            if (childStyle.display() == RenderStyle.Display.NONE) {
                continue;
            }
            boolean blockified = childStyle.position() != RenderStyle.Position.STATIC
                    && !"img".equals(tag);
            if (blockified || isBlockContainer(childStyle.display())) {
                return true;
            }
            if (containsBlockLevelDescendant(childElement, childStyle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGridContainer(RenderStyle.Display display) {
        return display == RenderStyle.Display.GRID || display == RenderStyle.Display.INLINE_GRID;
    }

    private static boolean isFlexContainer(RenderStyle.Display display) {
        return display == RenderStyle.Display.FLEX || display == RenderStyle.Display.INLINE_FLEX;
    }

    private static List<RenderNode> wrapFlexItems(List<RenderNode> children,
                                                   RenderStyle parentStyle) {
        List<RenderNode> items = new ArrayList<>();
        List<RenderNode> textRun = new ArrayList<>();
        for (RenderNode child : children) {
            if (child instanceof RenderBox box) {
                flushFlexText(textRun, parentStyle, items);
                items.add(box);
            } else if (child instanceof RenderTextRun text) {
                textRun.add(text);
            } else if (child instanceof RenderInlineBlock inlineBlock) {
                flushFlexText(textRun, parentStyle, items);
                items.add(inlineBlock.box());
            } else {
                flushFlexText(textRun, parentStyle, items);
                RenderStyle itemStyle = anonymousBlockStyle(parentStyle);
                if (child instanceof RenderImage image) {
                    itemStyle = itemStyle.withFlexGrow(image.style().flexGrow());
                }
                items.add(new RenderBox(null, itemStyle, List.of(child)));
            }
        }
        flushFlexText(textRun, parentStyle, items);
        return List.copyOf(items);
    }

    private static void flushFlexText(List<RenderNode> textRun,
                                      RenderStyle parentStyle,
                                      List<RenderNode> output) {
        if (textRun.isEmpty()) return;
        if (!textRun.stream().allMatch(node -> node instanceof RenderTextRun run
                && run.text().isBlank())) {
            output.add(new RenderBox(null, anonymousBlockStyle(parentStyle), List.copyOf(textRun)));
        }
        textRun.clear();
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

    private static SvgImage parseSvg(Element svg, RenderStyle style) {
        float[] viewBox = parseViewBox(svg.getAttribute("viewbox"));
        if (viewBox == null) {
            Integer width = positiveIntegerAttribute(svg, "width");
            Integer height = positiveIntegerAttribute(svg, "height");
            viewBox = new float[]{0, 0, width == null ? 300 : width, height == null ? 150 : height};
        }
        StringBuilder source = new StringBuilder();
        serializeSvgElement(svg, source, true, style);
        return new SvgImage(source.toString(), viewBox[2], viewBox[3]);
    }

    private static SvgImage parseExternalSvg(byte[] data) {
        if (data == null || data.length == 0) return null;
        String source = new String(data, StandardCharsets.UTF_8);
        var root = java.util.regex.Pattern.compile(
                "(?is)^\\s*(?:<\\?xml[^>]*>\\s*)?<svg\\b([^>]*)>")
                .matcher(source);
        if (!root.find()) return null;
        String attributes = root.group(1);
        float[] viewBox = parseViewBox(svgAttribute(attributes, "viewBox"));
        if (viewBox != null) return new SvgImage(source, viewBox[2], viewBox[3]);
        Float width = svgDimension(svgAttribute(attributes, "width"));
        Float height = svgDimension(svgAttribute(attributes, "height"));
        return new SvgImage(source, width == null ? 300 : width,
                height == null ? 150 : height);
    }

    private static String svgAttribute(String attributes, String name) {
        var matcher = java.util.regex.Pattern.compile(
                "(?is)(?:^|\\s)" + java.util.regex.Pattern.quote(name)
                        + "\\s*=\\s*(['\"])(.*?)\\1")
                .matcher(attributes);
        return matcher.find() ? matcher.group(2) : null;
    }

    private static Float svgDimension(String value) {
        if (value == null) return null;
        var matcher = java.util.regex.Pattern.compile(
                "(?i)^\\s*([0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)(?:px)?\\s*$")
                .matcher(value);
        if (!matcher.matches()) return null;
        try {
            float parsed = Float.parseFloat(matcher.group(1));
            return Float.isFinite(parsed) && parsed > 0 ? parsed : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static void serializeSvgElement(Element element, StringBuilder target,
                                            boolean root, RenderStyle rootStyle) {
        target.append('<').append(svgTagName(element.getTagName()));
        boolean hasNamespace = false;
        for (Map.Entry<String, String> attribute : element.getAttributes().entrySet()) {
            String name = svgAttributeName(attribute.getKey());
            if (root && name.equals("xmlns")) hasNamespace = true;
            if (name.equals("style")) continue;
            target.append(' ').append(name).append("=\"")
                    .append(escapeXml(attribute.getValue())).append('"');
        }
        if (root && !hasNamespace) {
            target.append(" xmlns=\"http://www.w3.org/2000/svg\"");
        }
        String style = svgStyle(element, root, rootStyle);
        if (!style.isBlank()) {
            target.append(" style=\"").append(escapeXml(style)).append('"');
        }
        target.append('>');
        for (Node child : element.getChildren()) {
            if (child instanceof Element childElement) {
                serializeSvgElement(childElement, target, false, rootStyle);
            } else if (child instanceof TextNode text) {
                target.append(escapeXml(text.getData()));
            }
        }
        target.append("</").append(svgTagName(element.getTagName())).append('>');
    }

    private static String svgStyle(Element element, boolean root, RenderStyle rootStyle) {
        StringBuilder style = new StringBuilder();
        String inline = element.getAttribute("style");
        if (inline != null && !inline.isBlank()) {
            for (String declaration : inline.split(";")) {
                int separator = declaration.indexOf(':');
                if (separator < 1) continue;
                String property = declaration.substring(0, separator).strip();
                if (root && property.equalsIgnoreCase("opacity")) continue;
                appendStyle(style, property, declaration.substring(separator + 1).strip());
            }
        }
        if (root) appendStyle(style, "color", cssColor(rootStyle.color()));
        for (String property : List.of("fill", "stroke", "fill-opacity", "stroke-opacity")) {
            String value = element.getComputedStyles().get(property);
            if (value != null) appendStyle(style, property, value);
        }
        if (!root) {
            String opacity = element.getComputedStyles().get("opacity");
            if (opacity != null) appendStyle(style, "opacity", opacity);
        }
        return style.toString();
    }

    private static void appendStyle(StringBuilder target, String property, String value) {
        if (value == null || value.isBlank()) return;
        if (!target.isEmpty() && target.charAt(target.length() - 1) != ';') target.append(';');
        target.append(property).append(':').append(value).append(';');
    }

    private static String cssColor(CssColor color) {
        return "rgba(" + color.red() + ',' + color.green() + ',' + color.blue() + ','
                + color.alpha() / 255f + ')';
    }

    private static String svgTagName(String name) {
        return switch (name) {
            case "clippath" -> "clipPath";
            case "foreignobject" -> "foreignObject";
            case "lineargradient" -> "linearGradient";
            case "radialgradient" -> "radialGradient";
            case "textpath" -> "textPath";
            case "fegaussianblur" -> "feGaussianBlur";
            case "fecolormatrix" -> "feColorMatrix";
            case "fecomponenttransfer" -> "feComponentTransfer";
            case "fecomposite" -> "feComposite";
            case "fedropshadow" -> "feDropShadow";
            case "feflood" -> "feFlood";
            case "feimage" -> "feImage";
            case "femerge" -> "feMerge";
            case "femergenode" -> "feMergeNode";
            case "feoffset" -> "feOffset";
            default -> name;
        };
    }

    private static String svgAttributeName(String name) {
        return switch (name) {
            case "viewbox" -> "viewBox";
            case "preserveaspectratio" -> "preserveAspectRatio";
            case "gradientunits" -> "gradientUnits";
            case "gradienttransform" -> "gradientTransform";
            case "patternunits" -> "patternUnits";
            case "patterncontentunits" -> "patternContentUnits";
            case "markerwidth" -> "markerWidth";
            case "markerheight" -> "markerHeight";
            case "markerunits" -> "markerUnits";
            case "refx" -> "refX";
            case "refy" -> "refY";
            case "stddeviation" -> "stdDeviation";
            case "attributename" -> "attributeName";
            case "attributetype" -> "attributeType";
            case "calcmode" -> "calcMode";
            case "keytimes" -> "keyTimes";
            case "keysplines" -> "keySplines";
            case "xlink:href" -> "xlink:href";
            default -> name;
        };
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private static float[] parseViewBox(String source) {
        if (source == null || source.isBlank()) return null;
        String[] values = source.strip().split("[\\s,]+");
        if (values.length != 4) return null;
        try {
            float[] parsed = new float[4];
            for (int index = 0; index < 4; index++) parsed[index] = Float.parseFloat(values[index]);
            return parsed[2] > 0 && parsed[3] > 0 ? parsed : null;
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
                inherited.lineThrough(),
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
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderLength.AUTO,
                RenderLength.AUTO,
                Float.NaN,
                RenderStyle.ObjectFit.FILL,
                RenderStyle.BoxSizing.CONTENT_BOX,
                BoxEdges.ZERO,
                HorizontalAutoMargins.NONE,
                BoxEdges.ZERO,
                BoxEdges.ZERO,
                BoxColors.CURRENT_COLOR,
                BoxBorders.NONE,
                CornerRadii.ZERO,
                java.util.List.of(),
                Transform.NONE,
                0,
                inherited.color(),
                false,
                0,
                true,
                true,
                inherited.borderCollapse(),
                inherited.textAlign(),
                inherited.textTransform(),
                inherited.whiteSpace(),
                inherited.letterSpacingPx(),
                RenderStyle.TextOverflow.CLIP,
                RenderStyle.Overflow.VISIBLE,
                RenderStyle.VerticalAlign.BASELINE,
                RenderStyle.FlexDirection.ROW,
                RenderStyle.FlexWrap.NOWRAP,
                RenderStyle.JustifyContent.FLEX_START,
                RenderStyle.AlignItems.STRETCH,
                RenderStyle.AlignSelf.AUTO,
                RenderStyle.AlignContent.NORMAL,
                0,
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                null,
                RenderStyle.GridAutoFlow.ROW,
                RenderStyle.GridLine.AUTO,
                RenderStyle.GridLine.AUTO,
                RenderStyle.GridLine.AUTO,
                RenderStyle.GridLine.AUTO,
                0,
                0,
                inherited.textShadow(),
                0,
                1,
                RenderLength.AUTO,
                inherited.opacity());
    }

    private RenderStyle resolveStyle(Element element, RenderStyle parent) {
        return resolveStyle(element.getTagName(), element == documentElement,
                element.getComputedStyles(), parent);
    }

    private RenderStyle resolveStyle(String tag, boolean rootElement,
                                     Map<String, String> declarations, RenderStyle parent) {

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
        boolean lineThrough = parent.lineThrough();
        CssColor textDecorationColor = parent.textDecorationColor();
        RenderStyle.Cursor cursor = parent.cursor();
        CssColor background = null;
        String backgroundImageUrl = null;
        RenderStyle.BackgroundRepeat backgroundRepeat = RenderStyle.BackgroundRepeat.REPEAT;
        RenderStyle.BackgroundPositionX backgroundPositionX = RenderStyle.BackgroundPositionX.LEFT;
        RenderStyle.BackgroundPositionY backgroundPositionY = RenderStyle.BackgroundPositionY.TOP;
        RenderLength backgroundPositionOffsetX = RenderLength.AUTO;
        RenderLength backgroundPositionOffsetY = RenderLength.AUTO;
        RenderLength backgroundSizeX = RenderLength.AUTO;
        RenderLength backgroundSizeY = RenderLength.AUTO;
        RenderLength width = RenderLength.AUTO;
        RenderLength height = RenderLength.AUTO;
        RenderLength minWidth = RenderLength.AUTO;
        RenderLength maxWidth = RenderLength.AUTO;
        RenderLength minHeight = RenderLength.AUTO;
        RenderLength maxHeight = RenderLength.AUTO;
        float aspectRatio = Float.NaN;
        RenderStyle.ObjectFit objectFit = RenderStyle.ObjectFit.FILL;
        RenderStyle.BoxSizing boxSizing = RenderStyle.BoxSizing.CONTENT_BOX;
        BoxEdges margin = defaultMargin(tag);
        HorizontalAutoMargins autoMargins = HorizontalAutoMargins.NONE;
        BoxEdges padding = BoxEdges.ZERO;
        BoxEdges borderWidth = BoxEdges.ZERO;
        BoxColors borderColor = BoxColors.CURRENT_COLOR;
        BoxBorders borderStyle = BoxBorders.NONE;
        CornerRadii borderRadius = CornerRadii.ZERO;
        java.util.List<BoxShadow> boxShadows = java.util.List.of();
        float outlineWidth = 0;
        float outlineOffset = 0;
        boolean visible = parent.visible();
        if (declarations.containsKey("visibility")) {
            visible = !"hidden".equals(declarations.get("visibility"));
        }
        boolean pointerEvents = true;
        CssColor outlineColor = color;
        boolean outlineVisible = false;
        RenderStyle.BorderCollapse borderCollapse = RenderStyle.BorderCollapse.SEPARATE;
        RenderStyle.TextAlign textAlign = parent.textAlign();
        RenderStyle.TextTransform textTransform = parent.textTransform();
        RenderStyle.WhiteSpace whiteSpace = parent.whiteSpace();
        float letterSpacingPx = parent.letterSpacingPx();
        RenderStyle.TextOverflow textOverflow = RenderStyle.TextOverflow.CLIP;
        RenderStyle.Overflow overflow = RenderStyle.Overflow.VISIBLE;
        RenderStyle.VerticalAlign verticalAlign = RenderStyle.VerticalAlign.BASELINE;
        RenderStyle.FlexDirection flexDirection = RenderStyle.FlexDirection.ROW;
        RenderStyle.FlexWrap flexWrap = RenderStyle.FlexWrap.NOWRAP;
        RenderStyle.JustifyContent justifyContent = RenderStyle.JustifyContent.FLEX_START;
        RenderStyle.AlignItems alignItems = RenderStyle.AlignItems.STRETCH;
        RenderStyle.AlignSelf alignSelf = RenderStyle.AlignSelf.AUTO;
        RenderStyle.AlignContent alignContent = RenderStyle.AlignContent.NORMAL;
        int order = 0;
        java.util.List<RenderStyle.GridTrack> gridTemplateColumns = java.util.List.of();
        java.util.List<RenderStyle.GridTrack> gridTemplateRows = java.util.List.of();
        java.util.List<RenderStyle.GridTrack> gridAutoColumns = java.util.List.of();
        java.util.List<RenderStyle.GridTrack> gridAutoRows = java.util.List.of();
        String[][] gridTemplateAreas = null;
        RenderStyle.GridAutoFlow gridAutoFlow = RenderStyle.GridAutoFlow.ROW;
        RenderStyle.GridLine gridColumnStart = RenderStyle.GridLine.AUTO;
        RenderStyle.GridLine gridColumnEnd = RenderStyle.GridLine.AUTO;
        RenderStyle.GridLine gridRowStart = RenderStyle.GridLine.AUTO;
        RenderStyle.GridLine gridRowEnd = RenderStyle.GridLine.AUTO;
        float rowGapPx = 0;
        float columnGapPx = 0;
        float flexGrow = 0;
        float flexShrink = 1;
        RenderLength flexBasis = RenderLength.AUTO;
        float opacity = parent.opacity();

        if (declarations.containsKey("display")) {
            String displayValue = declarations.get("display");
            display = switch (displayValue) {
                case "inherit" -> parent.display();
                case "-webkit-box" -> RenderStyle.Display.FLEX;
                case "contents" -> RenderStyle.Display.CONTENTS;
                case "block", "flow-root" -> RenderStyle.Display.BLOCK;
                case "inline-block" -> RenderStyle.Display.INLINE_BLOCK;
                case "none" -> RenderStyle.Display.NONE;
                case "flex" -> RenderStyle.Display.FLEX;
                case "inline-flex" -> RenderStyle.Display.INLINE_FLEX;
                case "grid" -> RenderStyle.Display.GRID;
                case "inline-grid" -> RenderStyle.Display.INLINE_GRID;
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
            case "sticky" -> RenderStyle.Position.STICKY;
            case "fixed" -> RenderStyle.Position.FIXED;
            default -> RenderStyle.Position.STATIC;
        };
        zIndex = parseZIndex(declarations.get("z-index"));
        cursor = switch (declarations.getOrDefault("cursor",
                cursor.name().toLowerCase(Locale.ROOT))) {
            case "pointer" -> RenderStyle.Cursor.POINTER;
            case "text" -> RenderStyle.Cursor.TEXT;
            case "grab", "grabbing" -> RenderStyle.Cursor.GRABBING;
            case "crosshair" -> RenderStyle.Cursor.CROSSHAIR;
            case "help" -> RenderStyle.Cursor.HELP;
            case "move", "all-scroll" -> RenderStyle.Cursor.MOVE;
            case "not-allowed" -> RenderStyle.Cursor.NOT_ALLOWED;
            case "wait" -> RenderStyle.Cursor.WAIT;
            case "progress" -> RenderStyle.Cursor.PROGRESS;
            case "zoom-in" -> RenderStyle.Cursor.ZOOM_IN;
            case "zoom-out" -> RenderStyle.Cursor.ZOOM_OUT;
            case "cell" -> RenderStyle.Cursor.CELL;
            case "copy" -> RenderStyle.Cursor.COPY;
            case "no-drop" -> RenderStyle.Cursor.NO_DROP;
            case "alias" -> RenderStyle.Cursor.ALIAS;
            case "context-menu" -> RenderStyle.Cursor.CONTEXT_MENU;
            case "vertical-text" -> RenderStyle.Cursor.VERTICAL_TEXT;
            case "col-resize" -> RenderStyle.Cursor.COL_RESIZE;
            case "row-resize", "ns-resize", "n-resize", "s-resize"
                    -> RenderStyle.Cursor.NS_RESIZE;
            case "ew-resize", "e-resize", "w-resize" -> RenderStyle.Cursor.EW_RESIZE;
            case "ne-resize", "nw-resize", "se-resize", "sw-resize"
                    -> RenderStyle.Cursor.MOVE;
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
            if ("inherit".equals(declarations.get("font-size"))) {
                fontSize = parent.fontSizePx();
            } else {
                float remBase = rootElement ? DEFAULT_FONT_SIZE : rootFontSizePx;
                fontSize = resolveLength(
                        declarations.get("font-size"), parent.fontSizePx(), remBase, fontSize);
            }
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
            String lineHeightValue = declarations.get("line-height");
            lineHeight = lineHeightValue.equals("inherit")
                    ? parent.lineHeight() : resolveLineHeight(lineHeightValue, fontSize);
        }
        width = "inherit".equals(declarations.get("width"))
                ? parent.width() : resolveDimension(declarations.get("width"), fontSize);
        height = "inherit".equals(declarations.get("height"))
                ? parent.height() : resolveDimension(declarations.get("height"), fontSize);
        minWidth = resolveDimension(declarations.get("min-width"), fontSize);
        maxWidth = "inherit".equals(declarations.get("max-width"))
                ? parent.maxWidth() : resolveDimension(declarations.get("max-width"), fontSize);
        minHeight = resolveDimension(declarations.get("min-height"), fontSize);
        maxHeight = "inherit".equals(declarations.get("max-height"))
                ? parent.maxHeight() : resolveDimension(declarations.get("max-height"), fontSize);
        aspectRatio = parseAspectRatio(declarations.get("aspect-ratio"));
        objectFit = switch (declarations.getOrDefault("object-fit", "fill")) {
            case "contain" -> RenderStyle.ObjectFit.CONTAIN;
            case "cover" -> RenderStyle.ObjectFit.COVER;
            case "none" -> RenderStyle.ObjectFit.NONE;
            case "scale-down" -> RenderStyle.ObjectFit.SCALE_DOWN;
            default -> RenderStyle.ObjectFit.FILL;
        };
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
        textTransform = switch (declarations.getOrDefault("text-transform",
                textTransform.name().toLowerCase(Locale.ROOT))) {
            case "uppercase" -> RenderStyle.TextTransform.UPPERCASE;
            case "lowercase" -> RenderStyle.TextTransform.LOWERCASE;
            case "capitalize" -> RenderStyle.TextTransform.CAPITALIZE;
            default -> RenderStyle.TextTransform.NONE;
        };
        String overflowValue = declarations.getOrDefault("overflow", null);
        if (overflowValue == null) {
            overflowValue = declarations.getOrDefault("overflow-y",
                    declarations.getOrDefault("overflow-x", "visible"));
        }
        overflow = switch (overflowValue) {
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
        flexDirection = switch (declarations.getOrDefault("flex-direction", "row")) {
            case "row-reverse" -> RenderStyle.FlexDirection.ROW_REVERSE;
            case "column" -> RenderStyle.FlexDirection.COLUMN;
            case "column-reverse" -> RenderStyle.FlexDirection.COLUMN_REVERSE;
            default -> RenderStyle.FlexDirection.ROW;
        };
        flexWrap = switch (declarations.getOrDefault("flex-wrap", "nowrap")) {
            case "wrap" -> RenderStyle.FlexWrap.WRAP;
            case "wrap-reverse" -> RenderStyle.FlexWrap.WRAP_REVERSE;
            default -> RenderStyle.FlexWrap.NOWRAP;
        };
        justifyContent = switch (declarations.getOrDefault("justify-content", "flex-start")) {
            case "center" -> RenderStyle.JustifyContent.CENTER;
            case "flex-end", "end", "right" -> RenderStyle.JustifyContent.FLEX_END;
            case "space-between" -> RenderStyle.JustifyContent.SPACE_BETWEEN;
            case "space-around" -> RenderStyle.JustifyContent.SPACE_AROUND;
            case "space-evenly" -> RenderStyle.JustifyContent.SPACE_EVENLY;
            default -> RenderStyle.JustifyContent.FLEX_START; // start/left/Flex-Fallback
        };
        alignItems = switch (declarations.getOrDefault("align-items", "stretch")) {
            case "flex-start", "start", "top" -> RenderStyle.AlignItems.FLEX_START;
            case "center" -> RenderStyle.AlignItems.CENTER;
            case "flex-end", "end", "bottom" -> RenderStyle.AlignItems.FLEX_END;
            case "baseline" -> RenderStyle.AlignItems.BASELINE;
            case "inherit" -> parent.alignItems();
            default -> RenderStyle.AlignItems.STRETCH;
        };
        alignSelf = switch (declarations.getOrDefault("align-self", "auto")) {
            case "inherit" -> parent.alignSelf();
            case "stretch" -> RenderStyle.AlignSelf.STRETCH;
            case "flex-start", "start", "self-start" -> RenderStyle.AlignSelf.FLEX_START;
            case "center" -> RenderStyle.AlignSelf.CENTER;
            case "flex-end", "end", "self-end" -> RenderStyle.AlignSelf.FLEX_END;
            case "baseline" -> RenderStyle.AlignSelf.BASELINE;
            default -> RenderStyle.AlignSelf.AUTO;
        };
        alignContent = switch (declarations.getOrDefault("align-content", "normal")) {
            case "flex-start" -> RenderStyle.AlignContent.FLEX_START;
            case "flex-end" -> RenderStyle.AlignContent.FLEX_END;
            case "center" -> RenderStyle.AlignContent.CENTER;
            case "space-between" -> RenderStyle.AlignContent.SPACE_BETWEEN;
            case "space-around" -> RenderStyle.AlignContent.SPACE_AROUND;
            case "space-evenly" -> RenderStyle.AlignContent.SPACE_EVENLY;
            case "stretch" -> RenderStyle.AlignContent.STRETCH;
            default -> RenderStyle.AlignContent.NORMAL;
        };
        order = 0;
        if (declarations.containsKey("order")) {
            try {
                order = Integer.parseInt(declarations.get("order"));
            } catch (NumberFormatException ignored) {
                // "inherit" und ähnliche Werte verhalten sich wie der Default 0.
            }
        }
        gridTemplateColumns = parseGridTracks(
                declarations.get("grid-template-columns"), fontSize, rootFontSizePx);
        gridTemplateRows = parseGridTracks(
                declarations.get("grid-template-rows"), fontSize, rootFontSizePx);
        gridTemplateAreas = parseGridAreas(declarations.get("grid-template-areas"));
        // grid-area ist eine Kurzform und wird vom Parser in die vier
        // Langformen expandiert; hier werden nur die Langformen gelesen.
        gridColumnStart = parseGridLine(declarations.get("grid-column-start"));
        gridColumnEnd = parseGridLine(declarations.get("grid-column-end"));
        gridRowStart = parseGridLine(declarations.get("grid-row-start"));
        gridRowEnd = parseGridLine(declarations.get("grid-row-end"));
        gridAutoFlow = switch (declarations.getOrDefault("grid-auto-flow", "row")) {
            case "column" -> RenderStyle.GridAutoFlow.COLUMN;
            case "row dense" -> RenderStyle.GridAutoFlow.ROW_DENSE;
            case "column dense" -> RenderStyle.GridAutoFlow.COLUMN_DENSE;
            default -> RenderStyle.GridAutoFlow.ROW;
        };
        gridAutoColumns = parseGridTracks(
                declarations.get("grid-auto-columns"), fontSize, rootFontSizePx);
        gridAutoRows = parseGridTracks(
                declarations.get("grid-auto-rows"), fontSize, rootFontSizePx);
        RenderStyle.TextShadow textShadow = parseTextShadow(
                declarations.get("text-shadow"), fontSize);
        rowGapPx = Math.max(0, resolveLength(
                declarations.get("row-gap"), fontSize, rootFontSizePx, 0));
        columnGapPx = Math.max(0, resolveLength(
                declarations.get("column-gap"), fontSize, rootFontSizePx, 0));
        if (declarations.containsKey("flex-grow")) {
            flexGrow = Float.parseFloat(declarations.get("flex-grow"));
        }
        if (declarations.containsKey("flex-shrink")) {
            flexShrink = Float.parseFloat(declarations.get("flex-shrink"));
        }
        if (declarations.containsKey("flex-basis")) {
            flexBasis = resolveDimension(declarations.get("flex-basis"), fontSize);
        }
        if (declarations.containsKey("opacity")) {
            opacity *= Float.parseFloat(declarations.get("opacity"));
        }
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
            String decorationLine = declarations.get("text-decoration-line");
            underline = "underline".equals(decorationLine);
            lineThrough = "line-through".equals(decorationLine);
        }
        CssColor declaredDecorationColor = CssColor.parse(declarations.get("text-decoration-color"));
        if (declaredDecorationColor != null) textDecorationColor = declaredDecorationColor;
        else if ("currentcolor".equals(declarations.get("text-decoration-color"))) {
            textDecorationColor = color;
        } else if ((underline || lineThrough)
                && !(parent.underline() || parent.lineThrough())) textDecorationColor = color;
        CssColor declaredBackground = "inherit".equals(declarations.get("background-color"))
                ? parent.backgroundColor()
                : CssColor.parse(declarations.get("background-color"));
        if (declaredBackground != null && !declaredBackground.isTransparent()) {
            background = declaredBackground;
        } else if ("currentcolor".equals(declarations.get("background-color"))) {
            background = color;
        } else if ("canvastext".equals(declarations.get("background-color"))) {
            background = color;
        } else if ("canvas".equals(declarations.get("background-color"))) {
            background = CssColor.rgb(0xffffff);
        } else if ("linktext".equals(declarations.get("background-color"))) {
            background = CssColor.rgb(0x0000ee);
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
        backgroundPositionOffsetX = resolveDimension(
                declarations.get("background-position-x-offset"), fontSize);
        backgroundPositionOffsetY = resolveDimension(
                declarations.get("background-position-y-offset"), fontSize);
        backgroundSizeX = resolveDimension(declarations.get("background-size-x"), fontSize);
        backgroundSizeY = resolveDimension(declarations.get("background-size-y"), fontSize);

        margin = "inherit".equals(declarations.get("margin"))
                ? parent.margin()
                : resolveEdges(declarations, "margin", fontSize, margin, "", parent.margin());
        autoMargins = new HorizontalAutoMargins(
                "auto".equals(declarations.get("margin-left")),
                "auto".equals(declarations.get("margin-right")));
        padding = "inherit".equals(declarations.get("padding"))
                ? parent.padding()
                : nonNegative(resolveEdges(
                        declarations, "padding", fontSize, padding, "", parent.padding()));
        borderWidth = nonNegative(resolveEdges(declarations, "border", fontSize, borderWidth, "-width"));
        borderColor = "inherit".equals(declarations.get("border-color"))
                ? parent.borderColor()
                : resolveBorderColors(declarations, color);
        borderStyle = resolveBorderStyles(declarations);
        borderWidth = effectiveBorderWidths(borderWidth, borderStyle);
        String radiusDeclaration = declarations.get("border-radius");
        borderRadius = "inherit".equals(radiusDeclaration) || "unset".equals(radiusDeclaration)
                ? parent.borderRadius()
                : resolveCornerRadii(declarations, fontSize, rootFontSizePx);
        boxShadows = parseBoxShadows(declarations.get("box-shadow"), fontSize,
                rootFontSizePx, color);
        outlineWidth = Math.max(0, resolveLength(
                declarations.get("outline-width"), fontSize, rootFontSizePx, 0));
        outlineColor = colorOrCurrent(declarations.get("outline-color"), color);
        outlineVisible = "solid".equals(declarations.get("outline-style")) && outlineWidth > 0;
        outlineOffset = resolveLength(declarations.get("outline-offset"),
                fontSize, rootFontSizePx, 0);
        Transform transform = Transform.parse(declarations.get("transform"), rootFontSizePx);
        if (transform == null) {
            transform = Transform.NONE;
        }
        if (declarations.get("transform-origin") != null) {
            RenderOffset[] origin = parseTransformOrigin(
                    declarations.get("transform-origin"), fontSize, rootFontSizePx);
            if (origin != null) {
                transform = transform.withOrigin(origin[0], origin[1]);
            }
        }
        String visibilityValue = declarations.get("visibility");
        if (visibilityValue != null) {
            visible = !"hidden".equals(visibilityValue)
                    && !"collapse".equals(visibilityValue);
        }
        pointerEvents = !"none".equals(declarations.get("pointer-events"));
        whiteSpace = switch (declarations.getOrDefault("white-space", "normal")) {
            case "nowrap" -> RenderStyle.WhiteSpace.NOWRAP;
            case "pre" -> RenderStyle.WhiteSpace.PRE;
            case "pre-wrap" -> RenderStyle.WhiteSpace.PRE_WRAP;
            case "pre-line" -> RenderStyle.WhiteSpace.PRE_LINE;
            case "break-spaces" -> RenderStyle.WhiteSpace.BREAK_SPACES;
            default -> parent.whiteSpace();
        };
        letterSpacingPx = "normal".equals(declarations.getOrDefault("letter-spacing", "normal"))
                ? 0 : resolveLength(declarations.get("letter-spacing"), fontSize,
                        rootFontSizePx, 0);
        textOverflow = switch (declarations.getOrDefault("text-overflow", "clip")) {
            case "ellipsis" -> RenderStyle.TextOverflow.ELLIPSIS;
            case "inherit" -> parent.textOverflow();
            default -> RenderStyle.TextOverflow.CLIP;
        };

        return new RenderStyle(display, position, zIndex, floatMode, clear, top, right, bottom, left,
                fontSize, fontFamily, fontWeight, italic, lineHeight, color, listStyleType,
                underline, lineThrough, textDecorationColor, cursor, background,
                backgroundImageUrl, backgroundRepeat, backgroundPositionX, backgroundPositionY,
                backgroundPositionOffsetX, backgroundPositionOffsetY,
                backgroundSizeX, backgroundSizeY,
                width, height, minWidth, maxWidth, minHeight, maxHeight,
                aspectRatio, objectFit, boxSizing, margin,
                autoMargins, padding, borderWidth, borderColor, borderStyle, borderRadius,
                boxShadows, transform, outlineWidth, outlineColor, outlineVisible,
                outlineOffset, visible, pointerEvents,
                borderCollapse, textAlign, textTransform,
                whiteSpace, letterSpacingPx, textOverflow,
                overflow, verticalAlign, flexDirection, flexWrap, justifyContent,
                alignItems, alignSelf, alignContent, order,
                gridTemplateColumns, gridTemplateRows, gridAutoColumns, gridAutoRows,
                gridTemplateAreas,
                gridAutoFlow, gridColumnStart, gridColumnEnd, gridRowStart, gridRowEnd,
                rowGapPx, columnGapPx, textShadow,
                flexGrow, flexShrink, flexBasis,
                opacity);
    }

    private java.util.List<RenderStyle.GridTrack> parseGridTracks(
            String value, float fontSize, float rootFontSizePx) {
        if (value == null || value.isBlank() || value.equals("none")) {
            return java.util.List.of();
        }
        java.util.List<RenderStyle.GridTrack> tracks = new ArrayList<>();
        for (String rawToken : splitTopLevelWhitespace(value)) {
            String token = rawToken.replaceAll("^,|,$", "").strip();
            if (token.isEmpty()) {
                continue;
            }
            if (!expandGridTrack(tracks, token, fontSize, rootFontSizePx)) {
                return java.util.List.of();
            }
        }
        return tracks.isEmpty() ? java.util.List.of() : tracks;
    }

    private boolean expandGridTrack(java.util.List<RenderStyle.GridTrack> tracks,
                                    String token, float fontSize, float rootFontSizePx) {
        String lower = token.toLowerCase(Locale.ROOT);
        if (lower.equals("auto")) {
            tracks.add(new RenderStyle.GridTrack(RenderStyle.GridTrack.Type.AUTO,
                    0, 0, 0, 0, false, false));
            return true;
        }
        if (lower.endsWith("fr")) {
            try {
                tracks.add(new RenderStyle.GridTrack(RenderStyle.GridTrack.Type.FRACTION,
                        0, Float.parseFloat(token.substring(0, token.length() - 2)), 0, 0,
                        false, false));
                return true;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        if (lower.startsWith("minmax(") && lower.endsWith(")")) {
            String[] parts = token.substring(7, token.length() - 1).split(",");
            if (parts.length != 2) {
                return false;
            }
            String min = parts[0].strip();
            String max = parts[1].strip();
            if (min.equals("0") || min.equals("auto")) {
                min = "0px";
            }
            float minFixed = resolveGridTrackLength(min, fontSize, rootFontSizePx);
            boolean minPercent = isPercentToken(min);
            if (max.endsWith("fr")) {
                try {
                    // Negatives maxFixed kennzeichnet einen fr-Anteil.
                    tracks.add(new RenderStyle.GridTrack(RenderStyle.GridTrack.Type.MINMAX,
                            0, 0, minFixed,
                            -Float.parseFloat(max.substring(0, max.length() - 2)),
                            minPercent, false));
                    return true;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
            tracks.add(new RenderStyle.GridTrack(RenderStyle.GridTrack.Type.MINMAX,
                    0, 0, minFixed, resolveGridTrackLength(max, fontSize, rootFontSizePx),
                    minPercent, isPercentToken(max)));
            return true;
        }
        if (lower.startsWith("repeat(") && lower.endsWith(")")) {
            int comma = token.indexOf(',');
            if (comma < 0) {
                return false;
            }
            int count;
            try {
                count = Integer.parseInt(token.substring(7, comma).strip());
            } catch (NumberFormatException ignored) {
                // auto-fit/auto-fill: ein Satz Tracks (Approximation).
                count = 1;
            }
            if (count <= 0 || count > 64) {
                return false;
            }
            String inner = token.substring(comma + 1, token.length() - 1);
            java.util.List<String> innerParts = splitTopLevelCommas(inner);
            if (innerParts.isEmpty()) {
                return false;
            }
            java.util.List<RenderStyle.GridTrack> innerTracks = new ArrayList<>();
            for (String partList : innerParts) {
                for (String rawPart : splitTopLevelWhitespace(partList)) {
                    String part = rawPart.replaceAll("^,|,$", "").strip();
                    if (part.isEmpty()) {
                        continue;
                    }
                    if (!expandGridTrack(innerTracks, part, fontSize, rootFontSizePx)) {
                        return false;
                    }
                }
            }
            if (innerTracks.isEmpty()) {
                return false;
            }
            for (int index = 0; index < count; index++) {
                tracks.addAll(innerTracks);
            }
            return true;
        }
        if (lower.startsWith("fit-content(") && lower.endsWith(")")) {
            String inner = token.substring(12, token.length() - 1);
            tracks.add(new RenderStyle.GridTrack(RenderStyle.GridTrack.Type.MINMAX,
                    0, 0, 0, -resolveLength(inner, fontSize, rootFontSizePx, 0),
                    false, false));
            return true;
        }
        try {
            if (lower.equals("0")) {
                tracks.add(new RenderStyle.GridTrack(RenderStyle.GridTrack.Type.FIXED,
                        0, 0, 0, 0, false, false));
                return true;
            }
            if (lower.endsWith("%")) {
                tracks.add(new RenderStyle.GridTrack(RenderStyle.GridTrack.Type.PERCENT,
                        0, 0, Float.parseFloat(token.substring(0, token.length() - 1)), 0,
                        false, false));
                return true;
            }
            tracks.add(new RenderStyle.GridTrack(RenderStyle.GridTrack.Type.FIXED,
                    resolveLength(token, fontSize, rootFontSizePx, 0), 0, 0, 0,
                    false, false));
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean isPercentToken(String token) {
        return token.endsWith("%");
    }

    private float resolveGridTrackLength(String token, float fontSize,
                                         float rootFontSizePx) {
        if (isPercentToken(token)) {
            return Float.parseFloat(token.substring(0, token.length() - 1));
        }
        return resolveLength(token, fontSize, rootFontSizePx, 0);
    }

    private RenderStyle.TextShadow parseTextShadow(String value, float fontSize) {
        if (value == null || value.isBlank() || value.equals("none")) {
            return null;
        }
        CssColor color = null;
        float offsetX = 0;
        float offsetY = 0;
        int offsetCount = 0;
        for (String token : value.strip().split("\\s+")) {
            String lower = token.toLowerCase(Locale.ROOT);
            CssColor candidate = CssColor.parse(lower);
            if (candidate != null && !"transparent".equals(lower) || lower.equals("currentcolor")) {
                color = CssColor.parse(lower);
                continue;
            }
            if (offsetCount < 2 && (lower.equals("0") || lower.endsWith("px")
                    || lower.endsWith("em") || lower.endsWith("rem"))) {
                float offset = resolveLength(lower, fontSize, 16f, 0);
                if (offsetCount == 0) {
                    offsetX = offset;
                } else {
                    offsetY = offset;
                }
                offsetCount++;
            }
        }
        if (color == null) {
            color = CssColor.rgb(0);
        }
        return offsetCount == 0 ? null : new RenderStyle.TextShadow(color, offsetX, offsetY);
    }

    private static String[][] parseGridAreas(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        java.util.List<String> rows = new ArrayList<>();
        int columnCount = -1;
        int offset = 0;
        while (offset < value.length()) {
            int start = value.indexOf('"', offset);
            char quote = '"';
            if (start < 0) {
                start = value.indexOf('\'', offset);
                quote = '\'';
            }
            if (start < 0) {
                break;
            }
            int end = value.indexOf(quote, start + 1);
            if (end < 0) {
                return null;
            }
            String row = value.substring(start + 1, end).strip();
            if (!row.isEmpty()) {
                String[] cells = row.split("\\s+");
                if (columnCount < 0) {
                    columnCount = cells.length;
                } else if (cells.length != columnCount) {
                    return null;
                }
                rows.add(row);
            }
            offset = end + 1;
        }
        if (rows.isEmpty()) {
            return null;
        }
        String[][] areas = new String[rows.size()][];
        for (int row = 0; row < rows.size(); row++) {
            areas[row] = rows.get(row).split("\\s+");
        }
        return areas;
    }

    private static RenderStyle.GridLine parseGridLine(String value) {
        if (value == null || value.isBlank() || value.equals("auto")) {
            return RenderStyle.GridLine.AUTO;
        }
        String stripped = value.strip();
        if (stripped.startsWith("span ")) {
            try {
                return new RenderStyle.GridLine(0,
                        Integer.parseInt(stripped.substring(5).strip()), null);
            } catch (NumberFormatException ignored) {
                return RenderStyle.GridLine.AUTO;
            }
        }
        try {
            return new RenderStyle.GridLine(Integer.parseInt(stripped), 0, null);
        } catch (NumberFormatException ignored) {
            // Benannter Linien- bzw. Bereichsname (z. B. "mediaLeft").
            if (stripped.matches("[-_a-zA-Z][-_a-zA-Z0-9]*")) {
                return new RenderStyle.GridLine(0, 0, stripped);
            }
            return RenderStyle.GridLine.AUTO;
        }
    }

    private static RenderOffset[] parseTransformOrigin(String value,
                                                       float fontSize, float rootFontSizePx) {
        List<String> tokens = new java.util.ArrayList<>(java.util.List.of(value.trim().split("\\s+")));
        if (tokens.size() < 1 || tokens.size() > 2) {
            return null;
        }
        String xToken = tokens.get(0);
        String yToken = tokens.size() == 2 ? tokens.get(1) : null;
        if (yToken == null) {
            yToken = switch (xToken) {
                case "left", "right", "top", "bottom" -> xToken.equals("top")
                        || xToken.equals("bottom") ? "center" : xToken;
                default -> "center";
            };
            xToken = switch (xToken) {
                case "top", "bottom" -> "center";
                case "left", "right", "center" -> xToken;
                default -> xToken;
            };
        }
        RenderOffset x = parseOriginAxis(xToken, fontSize, rootFontSizePx);
        RenderOffset y = parseOriginAxis(yToken, fontSize, rootFontSizePx);
        if (x == null || y == null) {
            return null;
        }
        return new RenderOffset[] {x, y};
    }

    private static RenderOffset parseOriginAxis(String token,
                                                float fontSize, float rootFontSizePx) {
        return switch (token) {
            case "left", "top" -> new RenderOffset(0, RenderOffset.Unit.PERCENT);
            case "right", "bottom" -> new RenderOffset(100, RenderOffset.Unit.PERCENT);
            case "center" -> new RenderOffset(50, RenderOffset.Unit.PERCENT);
            default -> {
                RenderOffset parsed = resolveRenderOffset(token, fontSize, rootFontSizePx);
                yield parsed;
            }
        };
    }

    private static RenderOffset resolveRenderOffset(String value,
                                                    float fontSize, float rootFontSizePx) {
        if (value == null) {
            return null;
        }
        if (value.equals("0")) {
            return new RenderOffset(0, RenderOffset.Unit.PX);
        }
        try {
            ParsedLength parsed = parseLength(value);
            return switch (parsed.unit()) {
                case "%" -> new RenderOffset(parsed.value(), RenderOffset.Unit.PERCENT);
                case "rem" -> new RenderOffset(parsed.value(), RenderOffset.Unit.REM);
                case "vw" -> new RenderOffset(parsed.value(), RenderOffset.Unit.VW);
                case "vh" -> new RenderOffset(parsed.value(), RenderOffset.Unit.VH);
                case "em" -> new RenderOffset(parsed.value() * fontSize, RenderOffset.Unit.PX);
                default -> new RenderOffset(parsed.value(), RenderOffset.Unit.PX);
            };
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String transformText(String text, RenderStyle.TextTransform transform) {
        return switch (transform) {
            case UPPERCASE -> text.toUpperCase(Locale.ROOT);
            case LOWERCASE -> text.toLowerCase(Locale.ROOT);
            case CAPITALIZE -> capitalize(text);
            case NONE -> text;
        };
    }

    private static String capitalize(String text) {
        StringBuilder result = new StringBuilder(text.length());
        boolean wordStart = true;
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            if (Character.isLetterOrDigit(codePoint)) {
                result.appendCodePoint(wordStart ? Character.toTitleCase(codePoint) : codePoint);
                wordStart = false;
            } else {
                result.appendCodePoint(codePoint);
                wordStart = true;
            }
            index += Character.charCount(codePoint);
        }
        return result.toString();
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
            case "inherit" -> inherited;
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
        return resolveEdges(declarations, prefix, emBase, defaults, suffix, null);
    }

    private BoxEdges resolveEdges(Map<String, String> declarations,
                                  String prefix,
                                  float emBase,
                                  BoxEdges defaults,
                                  String suffix,
                                  BoxEdges inheritSource) {
        float top = resolveEdgeSide(declarations, prefix + "-top" + suffix,
                defaults.top(), emBase, inheritSource);
        float right = resolveEdgeSide(declarations, prefix + "-right" + suffix,
                defaults.right(), emBase, inheritSource);
        float bottom = resolveEdgeSide(declarations, prefix + "-bottom" + suffix,
                defaults.bottom(), emBase, inheritSource);
        float left = resolveEdgeSide(declarations, prefix + "-left" + suffix,
                defaults.left(), emBase, inheritSource);
        return new BoxEdges(top, right, bottom, left);
    }

    private float resolveEdgeSide(Map<String, String> declarations, String key,
                                  float fallback, float emBase, BoxEdges inheritSource) {
        String value = declarations.get(key);
        if ("inherit".equals(value) && inheritSource != null) {
            return switch (key.substring(key.lastIndexOf('-') + 1)) {
                case "top" -> inheritSource.top();
                case "right" -> inheritSource.right();
                case "bottom" -> inheritSource.bottom();
                case "left" -> inheritSource.left();
                default -> fallback;
            };
        }
        return resolveLength(value, emBase, rootFontSizePx, fallback);
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
        if (parsed != null) {
            return parsed;
        }
        if ("canvastext".equals(value) || "highlighttext".equals(value)
                || "buttontext".equals(value) || "fieldtext".equals(value)) {
            return currentColor;
        }
        if ("canvas".equals(value) || "field".equals(value)
                || "buttonface".equals(value) || "highlight".equals(value)) {
            return CssColor.rgb(0xffffff);
        }
        if ("linktext".equals(value)) {
            return CssColor.rgb(0x0000ee);
        }
        return currentColor;
    }

    private static BoxBorders resolveBorderStyles(Map<String, String> declarations) {
        return new BoxBorders(
                "solid".equals(declarations.get("border-top-style")),
                "solid".equals(declarations.get("border-right-style")),
                "solid".equals(declarations.get("border-bottom-style")),
                "solid".equals(declarations.get("border-left-style")));
    }

    private java.util.List<BoxShadow> parseBoxShadows(String value,
                                                      float emBase, float remBase,
                                                      CssColor currentColor) {
        if (value == null || value.isBlank() || "none".equalsIgnoreCase(value.strip())) {
            return java.util.List.of();
        }
        java.util.List<BoxShadow> shadows = new ArrayList<>();
        for (String layer : splitTopLevel(value, ',')) {
            BoxShadow shadow = parseBoxShadowLayer(layer.strip(), emBase, remBase, currentColor);
            if (shadow == null) {
                return java.util.List.of();
            }
            shadows.add(shadow);
        }
        return List.copyOf(shadows);
    }

    private BoxShadow parseBoxShadowLayer(String value, float emBase, float remBase,
                                          CssColor currentColor) {
        List<String> tokens = java.util.Arrays.stream(value.split("\\s+"))
                .filter(token -> !token.isBlank()).toList();
        if (tokens.size() < 2 || tokens.size() > 6) {
            return null;
        }
        boolean inset = false;
        CssColor color = null;
        List<String> lengths = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);
            boolean last = index == tokens.size() - 1;
            if (token.equalsIgnoreCase("inset") && !inset
                    && (index == 0 || last)) {
                inset = true;
            } else if (isShadowLength(token) || token.equals("0")) {
                lengths.add(token);
            } else {
                CssColor parsed = CssColor.parse(token);
                if (color != null || parsed == null) {
                    return null;
                }
                color = parsed;
            }
        }
        if (lengths.size() < 2 || lengths.size() > 4) {
            return null;
        }
        float x = resolveLength(lengths.get(0), emBase, remBase, 0);
        float y = resolveLength(lengths.get(1), emBase, remBase, 0);
        float blur = lengths.size() >= 3 ? resolveLength(lengths.get(2), emBase, remBase, 0) : 0;
        float spread = lengths.size() >= 4 ? resolveLength(lengths.get(3), emBase, remBase, 0) : 0;
        return new BoxShadow(inset, x, y, blur, spread,
                color == null ? currentColor : color);
    }

    private static java.util.List<String> splitTopLevel(String source, char separator) {
        java.util.List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean quoted = false;
        char quote = 0;
        int start = 0;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (quoted) {
                if (current == quote && (index == 0 || source.charAt(index - 1) != '\\')) {
                    quoted = false;
                }
            } else if (current == '\'' || current == '"') {
                quoted = true;
                quote = current;
            } else if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
            } else if (current == separator && depth == 0) {
                parts.add(source.substring(start, index));
                start = index + 1;
            }
        }
        parts.add(source.substring(start));
        return parts;
    }

    private static boolean isShadowLength(String token) {
        return token.matches("-?\\d+(\\.\\d+)?(?:px|em|rem|vw|vh)?");
    }

    private CornerRadii resolveCornerRadii(Map<String, String> declarations,
                                           float emBase, float remBase) {
        return new CornerRadii(
                resolveCornerRadius(declarations.get("border-top-left-radius"), emBase, remBase),
                resolveCornerRadius(declarations.get("border-top-right-radius"), emBase, remBase),
                resolveCornerRadius(declarations.get("border-bottom-right-radius"), emBase, remBase),
                resolveCornerRadius(declarations.get("border-bottom-left-radius"), emBase, remBase));
    }

    private float resolveCornerRadius(String value, float emBase, float remBase) {
        if (value == null) {
            return 0;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.endsWith("%")) {
            // Prozentradien werden vom Painter auf die halbe Boxkante begrenzt.
            return Float.POSITIVE_INFINITY;
        }
        return Math.max(0, resolveLength(normalized, emBase, remBase, 0));
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
                // ch hängt von der Nullglyphenbreite ab; im Renderbaum steht dafür nur em zur Verfügung.
                case "ch" -> parsed.value() * emBase * 0.5f;
                case "rem" -> parsed.value() * remBase;
                case "vw" -> parsed.value() * viewportWidth / 100f;
                case "vh" -> parsed.value() * viewportHeight / 100f;
                case "%" -> parsed.value() * emBase / 100f;
                default -> parsed.value();
            };
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private Float evaluateMathFunction(String value, float emBase) {
        boolean minimum = value.startsWith("min(");
        String args = value.substring(4, value.length() - 1);
        Float result = null;
        for (String arg : splitTopLevelCommas(args)) {
            Float parsed = evaluateMathArg(arg, emBase);
            if (parsed == null) {
                return null;
            }
            result = result == null ? parsed
                    : minimum ? Math.min(result, parsed) : Math.max(result, parsed);
        }
        return result;
    }

    private static List<String> splitTopLevelWhitespace(String source) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
            } else if (Character.isWhitespace(current) && depth == 0) {
                if (index > start) {
                    parts.add(source.substring(start, index));
                }
                start = index + 1;
            }
        }
        if (start < source.length()) {
            parts.add(source.substring(start));
        }
        return parts;
    }

    private static List<String> splitTopLevelCommas(String source) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
            } else if (current == ',' && depth == 0) {
                parts.add(source.substring(start, index));
                start = index + 1;
            }
        }
        parts.add(source.substring(start));
        return parts;
    }

    private Float evaluateMathArg(String arg, float emBase) {
        String normalized = arg.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        float result = 0;
        int offset = 0;
        boolean subtract = false;
        while (offset < normalized.length()) {
            while (offset < normalized.length() && normalized.charAt(offset) == ' ') {
                offset++;
            }
            if (offset < normalized.length() && (normalized.charAt(offset) == '+'
                    || normalized.charAt(offset) == '-')) {
                subtract = normalized.charAt(offset) == '-';
                offset++;
            }
            while (offset < normalized.length() && normalized.charAt(offset) == ' ') {
                offset++;
            }
            int end = offset;
            while (end < normalized.length()) {
                char current = normalized.charAt(end);
                if (current == '+' || current == '-') {
                    break;
                }
                end++;
            }
            String term = normalized.substring(offset, end).strip();
            if (term.isEmpty()) {
                return null;
            }
            float value = resolveLength(term, emBase, rootFontSizePx, Float.NaN);
            if (Float.isNaN(value)) {
                return null;
            }
            result += subtract ? -value : value;
            offset = end;
        }
        return result;
    }

    private RenderLength resolveDimension(String value, float emBase) {
        if (value == null || "auto".equals(value)) {
            return RenderLength.AUTO;
        }
        if ("unset".equals(value)) {
            return RenderLength.AUTO; // width/height erben nicht: unset = initial = auto
        }
        if ("fit-content".equals(value)) {
            return new RenderLength(0, RenderLength.Unit.MAX_CONTENT);
        }
        if ("max-content".equals(value)) {
            return new RenderLength(0, RenderLength.Unit.MAX_CONTENT);
        }
        if ("min-content".equals(value)) {
            return new RenderLength(0, RenderLength.Unit.MIN_CONTENT);
        }
        String math = value.toLowerCase(Locale.ROOT).strip();
        if (math.startsWith("min(") || math.startsWith("max(")) {
            Float evaluated = evaluateMathFunction(math, emBase);
            if (evaluated != null) {
                return new RenderLength(Math.max(0, evaluated), RenderLength.Unit.PX);
            }
        }
        if (math.startsWith("clamp(") && math.endsWith(")")) {
            List<String> args = splitTopLevelCommas(math.substring(6, math.length() - 1));
            if (args.size() == 3) {
                Float min = evaluateMathArg(args.get(0), emBase);
                Float preferred = evaluateMathArg(args.get(1), emBase);
                Float max = evaluateMathArg(args.get(2), emBase);
                if (min != null && preferred != null && max != null) {
                    float clamped = Math.min(max, Math.max(min, preferred));
                    return new RenderLength(Math.max(0, clamped), RenderLength.Unit.PX);
                }
            }
        }
        if (math.startsWith("calc(") && math.endsWith(")")) {
            // %-haltige Ausdrücke bleiben als PERCENT + px-Offset erhalten.
            java.util.regex.Matcher percentCalc = java.util.regex.Pattern.compile(
                    "calc\\(\\s*([0-9]*\\.?[0-9]+)%\\s*([+-])\\s*"
                            + "([0-9]*\\.?[0-9]+)px\\s*\\)",
                    java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value);
            if (percentCalc.matches()) {
                float offset = Float.parseFloat(percentCalc.group(3));
                if ("-".equals(percentCalc.group(2))) {
                    offset = -offset;
                }
                return new RenderLength(Float.parseFloat(percentCalc.group(1)),
                        RenderLength.Unit.PERCENT, offset);
            }
            Float evaluated = evaluateMathArg(math.substring(5, math.length() - 1), emBase);
            if (evaluated != null) {
                return new RenderLength(Math.max(0, evaluated), RenderLength.Unit.PX);
            }
        }
        if ("0".equals(value)) {
            return new RenderLength(0, RenderLength.Unit.PX);
        }
        try {
            java.util.regex.Matcher calc = java.util.regex.Pattern.compile(
                    "calc\\(\\s*([0-9]*\\.?[0-9]+)%\\s*([+-])\\s*"
                            + "([0-9]*\\.?[0-9]+)px\\s*\\)",
                    java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value);
            if (calc.matches()) {
                float offset = Float.parseFloat(calc.group(3));
                if ("-".equals(calc.group(2))) offset = -offset;
                return new RenderLength(Float.parseFloat(calc.group(1)),
                        RenderLength.Unit.PERCENT, offset);
            }
            if (value.endsWith("%")) {
                return new RenderLength(Float.parseFloat(value.substring(0, value.length() - 1)),
                        RenderLength.Unit.PERCENT);
            }
            ParsedLength parsed = parseLength(value);
            return switch (parsed.unit()) {
                case "em" -> new RenderLength(parsed.value() * emBase, RenderLength.Unit.PX);
                // ch hängt von der Nullglyphenbreite ab; im Renderbaum steht dafür nur em zur Verfügung.
                case "ch" -> new RenderLength(parsed.value() * emBase * 0.5f, RenderLength.Unit.PX);
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

    private static float parseAspectRatio(String value) {
        if (value == null || value.isBlank() || value.equals("auto")) return Float.NaN;
        String[] parts = value.split("/", -1);
        try {
            float numerator = Float.parseFloat(parts[0].strip());
            float denominator = parts.length == 1 ? 1 : Float.parseFloat(parts[1].strip());
            float ratio = numerator / denominator;
            return parts.length <= 2 && Float.isFinite(ratio) && ratio > 0
                    ? ratio : Float.NaN;
        } catch (NumberFormatException invalid) {
            return Float.NaN;
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
        if (value.toLowerCase(Locale.ROOT).startsWith("linear-gradient(")) {
            return value;
        }
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
        for (String unit : List.of("rem", "px", "em", "vw", "vh", "dvh", "svh", "lvh", "ch", "%")) {
            if (normalized.endsWith(unit)) {
                float number = Float.parseFloat(
                        normalized.substring(0, normalized.length() - unit.length()));
                // dvh/svh/lvh werden wie vh gegen die Viewport-Höhe aufgelöst.
                return new ParsedLength(number,
                        unit.equals("dvh") || unit.equals("svh") || unit.equals("lvh") ? "vh" : unit);
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
        return style.withDisplay(display);
    }
}

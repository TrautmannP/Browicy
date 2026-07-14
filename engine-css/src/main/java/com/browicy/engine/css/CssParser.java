package com.browicy.engine.css;

import com.browicy.engine.render.CssColor;
import com.browicy.engine.selectors.SelectorParseException;
import com.browicy.engine.selectors.SelectorParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class CssParser {

    private static final SelectorParser SELECTOR_PARSER = new SelectorParser();
    private static final Pattern RULE = Pattern.compile("([^{}]+)\\{([^{}]*)}");
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final String LENGTH_UNIT = "(?:px|em|rem|vw|vh)";
    private static final Pattern POSITIVE_LENGTH = Pattern.compile(
            "(?:(?:\\d+(?:\\.\\d+)?|\\.\\d+)" + LENGTH_UNIT + "|0)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARGIN_LENGTH = Pattern.compile(
            "(?:(?:-?(?:\\d+(?:\\.\\d+)?|\\.\\d+)" + LENGTH_UNIT + "|0)|auto)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DIMENSION = Pattern.compile(
            "(?:(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:px|em|rem|vw|vh|%)|0|auto)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MAX_DIMENSION = Pattern.compile(
            "(?:(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:px|em|rem|vw|vh|%)|0|none)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern POSITION_OFFSET = Pattern.compile(
            "(?:-?(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:px|em|rem|vw|vh|%)|0|auto)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FONT_SIZE = Pattern.compile(
            "(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:px|em|rem|vw|vh)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FONT_WEIGHT = Pattern.compile("[1-9]00");
    private static final Pattern LINE_HEIGHT = Pattern.compile(
            "(?:normal|(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:(?:px|em|rem|vw|vh|%)?))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FONT_SHORTHAND = Pattern.compile(
            "^(.*?)(" + FONT_SIZE.pattern() + ")(?:\\s*/\\s*(" + LINE_HEIGHT.pattern()
                    + "))?\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final List<String> SIDES = List.of("top", "right", "bottom", "left");

    public List<CssRule> parse(String css) {
        return parse(css, 0);
    }

    List<String> ruleSources(String css) {
        List<String> sources = new ArrayList<>();
        if (css == null || css.isBlank()) {
            return sources;
        }
        String source = COMMENTS.matcher(css).replaceAll("");
        var matcher = RULE.matcher(source);
        while (matcher.find()) {
            sources.add(matcher.group().strip());
        }
        return List.copyOf(sources);
    }

    List<CssRule> parse(String css, long sourceOrderStart) {
        List<CssRule> rules = new ArrayList<>();
        if (css == null || css.isBlank()) {
            return rules;
        }

        String source = COMMENTS.matcher(css).replaceAll("");
        var matcher = RULE.matcher(source);
        long sourceOrder = sourceOrderStart;
        while (matcher.find()) {
            Map<String, String> declarations = parseDeclarations(matcher.group(2));
            if (declarations.isEmpty()) {
                continue;
            }

            String selectorSource = recoverSelectorPrelude(matcher.group(1));
            try {
                for (var selector : SELECTOR_PARSER.parse(selectorSource).selectors()) {
                    rules.add(new CssRule(selector, declarations, sourceOrder));
                }
            } catch (SelectorParseException ignored) {
            }
            sourceOrder++;
        }
        return rules;
    }

    private static String recoverSelectorPrelude(String source) {
        String selector = source.strip();
        if (isSupportedSelectorList(selector)) {
            return selector;
        }
        int lastSemicolon = selector.lastIndexOf(';');
        if (lastSemicolon < 0) {
            return selector;
        }
        String recovered = selector.substring(lastSemicolon + 1).strip();
        int lastLineBreak = Math.max(recovered.lastIndexOf('\n'), recovered.lastIndexOf('\r'));
        if (lastLineBreak >= 0) {
            String lastLine = recovered.substring(lastLineBreak + 1).strip();
            if (isSupportedSelectorList(lastLine)) {
                return lastLine;
            }
        }
        return recovered;
    }

    private static boolean isSupportedSelectorList(String source) {
        if (source.isEmpty()) {
            return false;
        }
        try {
            SELECTOR_PARSER.parse(source);
            return true;
        } catch (SelectorParseException ignored) {
            return false;
        }
    }

    public Map<String, String> parseDeclarations(String source) {
        Map<String, String> declarations = new LinkedHashMap<>();
        if (source == null || source.isBlank()) {
            return declarations;
        }
        source = COMMENTS.matcher(source).replaceAll("");
        for (String declaration : source.split(";")) {
            int separator = declaration.indexOf(':');
            if (separator < 1) {
                continue;
            }
            String property = declaration.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = declaration.substring(separator + 1).trim().toLowerCase(Locale.ROOT);
            parseDeclaration(declarations, property, value);
        }
        return declarations;
    }

    public boolean supports(String property, String value) {
        if (property == null || property.isBlank() || value == null || value.isBlank()) {
            return false;
        }
        return !parseDeclarations(property + ":" + value).isEmpty();
    }

    public boolean supportsProperty(String property) {
        if (property == null) {
            return false;
        }
        String normalized = property.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "color", "background", "background-color" -> supports(normalized, "black");
            case "font-size" -> supports(normalized, "16px");
            case "font", "font-family" -> supports(normalized, "16px sans-serif");
            case "line-height" -> supports(normalized, "normal");
            case "font-weight" -> supports(normalized, "normal");
            case "font-style" -> supports(normalized, "normal");
            case "display" -> supports(normalized, "block");
            case "position" -> supports(normalized, "static");
            case "float" -> supports(normalized, "none");
            case "clear" -> supports(normalized, "none");
            case "top", "right", "bottom", "left" -> supports(normalized, "auto");
            case "width", "height", "min-width", "min-height" -> supports(normalized, "auto");
            case "max-width", "max-height" -> supports(normalized, "none");
            case "box-sizing" -> supports(normalized, "content-box");
            case "text-align" -> supports(normalized, "left");
            case "overflow" -> supports(normalized, "visible");
            case "vertical-align" -> supports(normalized, "baseline");
            case "border-collapse" -> supports(normalized, "separate");
            case "margin", "margin-top", "margin-right", "margin-bottom", "margin-left",
                 "padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
                 "border", "border-width", "border-top-width", "border-right-width",
                 "border-bottom-width", "border-left-width" -> supports(normalized, "0");
            case "border-color", "border-top-color", "border-right-color",
                 "border-bottom-color", "border-left-color" -> supports(normalized, "black");
            case "border-style", "border-top-style", "border-right-style",
                 "border-bottom-style", "border-left-style" -> supports(normalized, "solid");
            default -> false;
        };
    }

    private static void parseDeclaration(Map<String, String> target, String property, String value) {
        switch (property) {
            case "color", "background-color" -> putColor(target, property, value);
            case "background" -> putColor(target, "background-color", value);
            case "font-size" -> putIfMatches(target, property, value, FONT_SIZE);
            case "font-family" -> {
                if (!value.isBlank()) target.put(property, value);
            }
            case "line-height" -> putIfMatches(target, property, value, LINE_HEIGHT);
            case "font" -> expandFont(target, value);
            case "font-weight" -> {
                if (value.equals("normal") || value.equals("bold") || value.equals("bolder")
                        || value.equals("lighter") || FONT_WEIGHT.matcher(value).matches()) {
                    target.put(property, value);
                }
            }
            case "font-style" -> {
                if (value.equals("normal") || value.equals("italic") || value.equals("oblique")) {
                    target.put(property, value);
                }
            }
            case "display" -> {
                if (value.equals("block") || value.equals("inline")
                        || value.equals("inline-block") || value.equals("none")
                        || value.equals("table") || value.equals("inline-table")
                        || value.equals("table-row-group") || value.equals("table-header-group")
                        || value.equals("table-footer-group") || value.equals("table-row")
                        || value.equals("table-cell") || value.equals("table-column-group")
                        || value.equals("table-column") || value.equals("table-caption")) {
                    target.put(property, value);
                }
            }
            case "border-collapse" -> {
                if (value.equals("separate") || value.equals("collapse")) {
                    target.put(property, value);
                }
            }
            case "position" -> {
                if (value.equals("static") || value.equals("relative")
                        || value.equals("absolute")) {
                    target.put(property, value);
                }
            }
            case "float" -> {
                if (value.equals("none") || value.equals("left") || value.equals("right")) {
                    target.put(property, value);
                }
            }
            case "clear" -> {
                if (value.equals("none") || value.equals("left") || value.equals("right")
                        || value.equals("both")) {
                    target.put(property, value);
                }
            }
            case "top", "right", "bottom", "left" ->
                    putIfMatches(target, property, value, POSITION_OFFSET);
            case "width", "height", "min-width", "min-height" ->
                    putIfMatches(target, property, value, DIMENSION);
            case "max-width", "max-height" ->
                    putIfMatches(target, property, value, MAX_DIMENSION);
            case "box-sizing" -> {
                if (value.equals("content-box") || value.equals("border-box")) {
                    target.put(property, value);
                }
            }
            case "overflow" -> {
                if (value.equals("visible") || value.equals("hidden") || value.equals("auto")
                        || value.equals("scroll")) {
                    target.put(property, value);
                }
            }
            case "vertical-align" -> {
                if (value.equals("baseline") || value.equals("top") || value.equals("middle")
                        || value.equals("bottom") || value.equals("text-top")
                        || value.equals("text-bottom")) {
                    target.put(property, value);
                }
            }
            case "text-align" -> {
                if (value.equals("left") || value.equals("center") || value.equals("right")) {
                    target.put(property, value);
                }
            }
            case "margin", "padding" -> expandLengths(target, property, value,
                    property.equals("margin") ? MARGIN_LENGTH : POSITIVE_LENGTH, "");
            case "border-width" -> expandLengths(target, "border", value, POSITIVE_LENGTH, "-width");
            case "border-color" -> expandColors(target, value);
            case "border-style" -> expandBorderStyles(target, value);
            case "border" -> expandBorder(target, null, value);
            default -> parseLonghand(target, property, value);
        }
    }

    private static void expandFont(Map<String, String> target, String value) {
        var matcher = FONT_SHORTHAND.matcher(value.strip());
        if (!matcher.matches()) return;
        String prefix = matcher.group(1).strip();
        String fontStyle = "normal";
        String fontWeight = "normal";
        if (!prefix.isEmpty()) {
            for (String token : prefix.split("\\s+")) {
                if (token.equals("italic") || token.equals("oblique")) {
                    fontStyle = token;
                } else if (token.equals("normal")) {
                    fontStyle = "normal";
                    fontWeight = "normal";
                } else if (token.equals("bold") || token.equals("bolder")
                        || token.equals("lighter") || FONT_WEIGHT.matcher(token).matches()) {
                    fontWeight = token;
                } else {
                    return;
                }
            }
        }
        target.put("font-style", fontStyle);
        target.put("font-weight", fontWeight);
        target.put("font-size", matcher.group(2));
        target.put("line-height", matcher.group(3) == null ? "normal" : matcher.group(3));
        target.put("font-family", matcher.group(4).strip());
    }

    private static void parseLonghand(Map<String, String> target, String property, String value) {
        for (String side : SIDES) {
            if (property.equals("margin-" + side)) {
                putIfMatches(target, property, value, MARGIN_LENGTH);
                return;
            }
            if (property.equals("padding-" + side) || property.equals("border-" + side + "-width")) {
                putIfMatches(target, property, value, POSITIVE_LENGTH);
                return;
            }
            if (property.equals("border-" + side + "-color")) {
                putColor(target, property, value);
                return;
            }
            if (property.equals("border-" + side + "-style")) {
                if (value.equals("none") || value.equals("solid")) {
                    target.put(property, value);
                }
                return;
            }
            if (property.equals("border-" + side)) {
                expandBorder(target, side, value);
                return;
            }
        }
    }

    private static void expandLengths(Map<String, String> target,
                                      String prefix,
                                      String value,
                                      Pattern accepted,
                                      String suffix) {
        String[] values = splitBoxValues(value);
        if (values == null) {
            return;
        }
        for (String entry : values) {
            if (!accepted.matcher(entry).matches()) {
                return;
            }
        }
        String[] expanded = expandFour(values);
        for (int index = 0; index < SIDES.size(); index++) {
            target.put(prefix + "-" + SIDES.get(index) + suffix, expanded[index]);
        }
    }

    private static void expandColors(Map<String, String> target, String value) {
        String[] values = splitBoxValues(value);
        if (values == null) {
            return;
        }
        for (String entry : values) {
            if (!CssColor.isSupported(entry)) {
                return;
            }
        }
        String[] expanded = expandFour(values);
        for (int index = 0; index < SIDES.size(); index++) {
            target.put("border-" + SIDES.get(index) + "-color", expanded[index]);
        }
    }

    private static void expandBorderStyles(Map<String, String> target, String value) {
        String[] values = splitBoxValues(value);
        if (values == null) {
            return;
        }
        for (String entry : values) {
            if (!entry.equals("none") && !entry.equals("solid")) {
                return;
            }
        }
        String[] expanded = expandFour(values);
        for (int index = 0; index < SIDES.size(); index++) {
            target.put("border-" + SIDES.get(index) + "-style", expanded[index]);
        }
    }

    private static void expandBorder(Map<String, String> target, String side, String value) {
        String width = null;
        String style = null;
        String color = null;
        for (String token : value.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            if (POSITIVE_LENGTH.matcher(token).matches() && width == null) {
                width = token;
            } else if ((token.equals("none") || token.equals("solid")) && style == null) {
                style = token;
            } else if (CssColor.isSupported(token) && color == null) {
                color = token;
            } else {
                return;
            }
        }
        if (width == null && style == null && color == null) {
            return;
        }
        if (style == null) {
            style = "none";
        }
        if (width == null) {
            width = style.equals("none") ? "0" : "1px";
        }
        List<String> targetSides = side == null ? SIDES : List.of(side);
        for (String targetSide : targetSides) {
            target.put("border-" + targetSide + "-width", width);
            target.put("border-" + targetSide + "-style", style);
            if (color != null) {
                target.put("border-" + targetSide + "-color", color);
            }
        }
    }

    private static String[] splitBoxValues(String value) {
        String[] values = value.strip().split("\\s+");
        return values.length >= 1 && values.length <= 4 ? values : null;
    }

    private static String[] expandFour(String[] values) {
        return switch (values.length) {
            case 1 -> new String[]{values[0], values[0], values[0], values[0]};
            case 2 -> new String[]{values[0], values[1], values[0], values[1]};
            case 3 -> new String[]{values[0], values[1], values[2], values[1]};
            case 4 -> values;
            default -> throw new IllegalArgumentException("Expected one to four values");
        };
    }

    private static void putColor(Map<String, String> target, String property, String value) {
        if (CssColor.isSupported(value)) {
            target.put(property, value);
        }
    }

    private static void putIfMatches(Map<String, String> target,
                                     String property,
                                     String value,
                                     Pattern pattern) {
        if (pattern.matcher(value).matches()) {
            target.put(property, value);
        }
    }
}

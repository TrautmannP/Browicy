package com.browicy.engine.css;

import com.browicy.engine.render.CssColor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Fehlertoleranter Parser für einfache Elementselektoren und Render-Stile. */
public final class CssParser {

    private static final String ELEMENT_SELECTOR = "[a-zA-Z][a-zA-Z0-9-]*";
    private static final Pattern RULE = Pattern.compile(
            "(" + ELEMENT_SELECTOR + "(?:\\s*,\\s*" + ELEMENT_SELECTOR + ")*)\\s*\\{([^{}]*)}");
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LENGTH = Pattern.compile(
            "(?:-?(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:px|em)|0)", Pattern.CASE_INSENSITIVE);
    private static final Pattern POSITIVE_LENGTH = Pattern.compile(
            "(?:(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:px|em)|0)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FONT_SIZE = Pattern.compile(
            "(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:px|em)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FONT_WEIGHT = Pattern.compile("[1-9]00");
    private static final List<String> SIDES = List.of("top", "right", "bottom", "left");

    public List<CssRule> parse(String css) {
        List<CssRule> rules = new ArrayList<>();
        if (css == null || css.isBlank()) {
            return rules;
        }

        String source = COMMENTS.matcher(css).replaceAll("");
        var matcher = RULE.matcher(source);
        while (matcher.find()) {
            Map<String, String> declarations = parseDeclarations(matcher.group(2));
            if (!declarations.isEmpty()) {
                for (String selector : matcher.group(1).split(",")) {
                    rules.add(new CssRule(selector.trim().toLowerCase(Locale.ROOT), declarations));
                }
            }
        }
        return rules;
    }

    /** Parst und expandiert eine Deklarationsliste, auch aus einem style-Attribut. */
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

    private static void parseDeclaration(Map<String, String> target, String property, String value) {
        switch (property) {
            case "color", "background-color" -> putColor(target, property, value);
            case "background" -> putColor(target, "background-color", value);
            case "font-size" -> putIfMatches(target, property, value, FONT_SIZE);
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
                if (value.equals("block") || value.equals("inline") || value.equals("none")) {
                    target.put(property, value);
                }
            }
            case "margin", "padding" -> expandLengths(target, property, value,
                    property.equals("margin") ? LENGTH : POSITIVE_LENGTH, "");
            case "border-width" -> expandLengths(target, "border", value, POSITIVE_LENGTH, "-width");
            case "border-color" -> expandColors(target, value);
            case "border-style" -> expandBorderStyles(target, value);
            case "border" -> expandBorder(target, null, value);
            default -> parseLonghand(target, property, value);
        }
    }

    private static void parseLonghand(Map<String, String> target, String property, String value) {
        for (String side : SIDES) {
            if (property.equals("margin-" + side)) {
                putIfMatches(target, property, value, LENGTH);
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

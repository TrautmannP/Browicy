package com.browicy.engine.css;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Fehlertoleranter Parser für Elementselektoren, color und font-size. */
public final class CssParser {

    private static final String ELEMENT_SELECTOR = "[a-zA-Z][a-zA-Z0-9-]*";
    private static final Pattern RULE = Pattern.compile(
            "(" + ELEMENT_SELECTOR + "(?:\\s*,\\s*" + ELEMENT_SELECTOR + ")*)\\s*\\{([^{}]*)}");
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{3}(?:[0-9a-fA-F]{3})?|[a-zA-Z]+");
    private static final Pattern FONT_SIZE = Pattern.compile("(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:px|em)", Pattern.CASE_INSENSITIVE);

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

    /** Parst eine Deklarationsliste, wie sie auch im style-Attribut vorkommt. */
    public Map<String, String> parseDeclarations(String source) {
        Map<String, String> declarations = new LinkedHashMap<>();
        if (source == null || source.isBlank()) return declarations;
        source = COMMENTS.matcher(source).replaceAll("");
        for (String declaration : source.split(";")) {
            int separator = declaration.indexOf(':');
            if (separator < 1) continue;
            String property = declaration.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = declaration.substring(separator + 1).trim();
            if ("color".equals(property) && COLOR.matcher(value).matches()) {
                declarations.put(property, value);
            } else if ("font-size".equals(property) && FONT_SIZE.matcher(value).matches()) {
                declarations.put(property, value.toLowerCase(Locale.ROOT));
            }
        }
        return declarations;
    }
}

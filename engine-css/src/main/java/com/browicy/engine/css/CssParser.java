package com.browicy.engine.css;

import com.browicy.engine.render.CssColor;
import com.browicy.engine.selectors.SelectorParseException;
import com.browicy.engine.selectors.SelectorParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.Pattern;

public final class CssParser {

    private static final SelectorParser SELECTOR_PARSER = new SelectorParser();
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern FONT_FACE = Pattern.compile(
            "@font-face\\s*\\{([^}]*)}", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FONT_FORMAT = Pattern.compile(
            "^\\s*format\\(\\s*(['\"]?)(.*?)\\1\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final String LENGTH_UNIT = "(?:px|em|rem|vw|vh|dvh|svh|lvh|ch|lh|%)";
    private static final Pattern POSITIVE_LENGTH = Pattern.compile(
            "(?:(?:\\d+(?:\\.\\d+)?|\\.\\d+)" + LENGTH_UNIT + "|0)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RADIUS_LENGTH = Pattern.compile(
            "(?:(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:" + LENGTH_UNIT + "|%)|0)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LENGTH_OR_ZERO = Pattern.compile(
            "(?:-?(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:px|em|rem|vw|vh)|0)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MARGIN_LENGTH = Pattern.compile(
            "(?:(?:-?(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:px|em|rem|vw|vh|%)|0)|auto)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DIMENSION = Pattern.compile(
            "(?:(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:px|em|rem|vw|vh|dvh|svh|lvh|ch|lh|%)|0|auto|"
                    + "calc\\(\\s*(?:\\d+(?:\\.\\d+)?|\\.\\d+)%\\s*[+-]\\s*"
                    + "(?:\\d+(?:\\.\\d+)?|\\.\\d+)px\\s*\\))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MAX_DIMENSION = Pattern.compile(
            "(?:(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:px|em|rem|vw|vh|dvh|svh|lvh|ch|lh|%)|0|none)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern POSITION_OFFSET = Pattern.compile(
            "(?:-?(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:px|em|rem|vw|vh|%)|0|auto)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FONT_SIZE = Pattern.compile(
            "(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:px|em|rem|vw|vh|%)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FONT_WEIGHT = Pattern.compile("[1-9]00");
    private static final Pattern INTEGER = Pattern.compile("-?\\d+");
    private static final Pattern LETTER_SPACING = Pattern.compile(
            "[-+]?[0-9]*\\.?[0-9]+(px|em|rem)?");
    private static final Pattern GRADIENT_ANGLE = Pattern.compile(
            "[-+]?[0-9]*\\.?[0-9]+(deg|turn|rad|grad)");
    private static final Pattern GRADIENT_POSITION = Pattern.compile(
            "[-+]?[0-9]*\\.?[0-9]+(%|px|em|rem)");
    private static final Pattern NON_NEGATIVE_NUMBER = Pattern.compile(
            "(?:\\d+(?:\\.\\d+)?|\\.\\d+)");
    private static final Pattern ASPECT_RATIO = Pattern.compile(
            "(?:auto|(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:\\s*/\\s*"
                    + "(?:\\d+(?:\\.\\d+)?|\\.\\d+))?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LINE_HEIGHT = Pattern.compile(
            "(?:normal|inherit|(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:(?:px|em|rem|vw|vh|%|ch|lh)?))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BACKGROUND_LENGTH = Pattern.compile(
            "(?:(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:px|em|rem|vw|vh|%)|0)",
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
        collectRuleSources(COMMENTS.matcher(css).replaceAll(""), sources);
        return List.copyOf(sources);
    }

    List<CssFontFace> fontFaces(String css) {
        if (css == null || css.isBlank()) return List.of();
        List<CssFontFace> result = new ArrayList<>();
        var matcher = FONT_FACE.matcher(COMMENTS.matcher(css).replaceAll(""));
        while (matcher.find()) {
            String family = null;
            String src = null;
            int weight = 400;
            boolean italic = false;
            for (String declaration : matcher.group(1).split(";")) {
                int separator = declaration.indexOf(':');
                if (separator < 1) continue;
                String property = declaration.substring(0, separator).strip()
                        .toLowerCase(Locale.ROOT);
                String value = declaration.substring(separator + 1).strip();
                switch (property) {
                    case "font-family" -> family = unquote(value);
                    case "src" -> src = value;
                    case "font-weight" -> weight = parseFontFaceWeight(value);
                    case "font-style" -> italic = !value.equalsIgnoreCase("normal");
                    default -> { }
                }
            }
            List<CssFontFace.Source> sources = new ArrayList<>();
            if (src != null) {
                var urlTokens = com.browicy.engine.render.CssUrl.tokens(src);
                for (int index = 0; index < urlTokens.size(); index++) {
                    var token = urlTokens.get(index);
                    int next = index + 1 < urlTokens.size()
                            ? urlTokens.get(index + 1).start() : src.length();
                    var formatMatcher = FONT_FORMAT.matcher(src.substring(token.end(), next));
                    sources.add(new CssFontFace.Source(
                            token.source(), formatMatcher.find()
                                    ? formatMatcher.group(2).toLowerCase(Locale.ROOT) : ""));
                }
            }
            if (family != null && !family.isBlank() && !sources.isEmpty()) {
                result.add(new CssFontFace(family, sources, weight, italic));
            }
        }
        return List.copyOf(result);
    }

    private static String unquote(String value) {
        String result = value.strip();
        if (result.length() >= 2 && (result.startsWith("\"") && result.endsWith("\"")
                || result.startsWith("'") && result.endsWith("'"))) {
            return result.substring(1, result.length() - 1);
        }
        return result;
    }

    private static int parseFontFaceWeight(String value) {
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.equals("bold")) return 700;
        try {
            int parsed = Integer.parseInt(normalized);
            return parsed >= 100 && parsed <= 900 ? parsed : 400;
        } catch (NumberFormatException ignored) {
            return 400;
        }
    }

    List<CssRule> parse(String css, long sourceOrderStart) {
        return parseSheet(css, sourceOrderStart).rules();
    }

    ParsedSheet parseSheet(String css, long sourceOrderStart) {
        List<CssRule> rules = new ArrayList<>();
        List<CssKeyframes> keyframes = new ArrayList<>();
        if (css == null || css.isBlank()) {
            return new ParsedSheet(rules, keyframes);
        }

        long[] sourceOrder = {sourceOrderStart};
        parseRuleList(COMMENTS.matcher(css).replaceAll(""), MediaCondition.ALL,
                sourceOrder, rules, keyframes);
        return new ParsedSheet(rules, keyframes);
    }

    record ParsedSheet(List<CssRule> rules, List<CssKeyframes> keyframes) {
    }

    private CssKeyframes parseKeyframes(String name, String body) {
        if (name.isEmpty()) {
            return null;
        }
        List<CssKeyframes.Block> blocks = new ArrayList<>();
        int offset = 0;
        while (offset < body.length()) {
            int open = body.indexOf('{', offset);
            if (open < 0) {
                break;
            }
            int close = body.indexOf('}', open + 1);
            if (close < 0) {
                return null;
            }
            String selector = body.substring(offset, open).strip().toLowerCase(Locale.ROOT);
            if (selector.equals("from") || selector.equals("to")
                    || selector.matches("[0-9]*\\.?[0-9]+%")) {
                Map<String, String> declarations =
                        parseDeclarationBlock(body.substring(open + 1, close)).declarations();
                blocks.add(new CssKeyframes.Block(selector, declarations));
            }
            offset = close + 1;
        }
        return blocks.isEmpty() ? null : new CssKeyframes(name, blocks);
    }

    private void parseRuleList(String source, MediaCondition condition,
                               long[] sourceOrder, List<CssRule> rules,
                               List<CssKeyframes> keyframes) {
        int offset = 0;
        while (offset < source.length()) {
            int open = source.indexOf('{', offset);
            if (open < 0) return;
            int semicolon = source.indexOf(';', offset);
            if (semicolon >= 0 && semicolon < open
                    && isNamespaceStatement(source.substring(offset, semicolon + 1))) {
                offset = semicolon + 1;
                continue;
            }
            String prelude = source.substring(offset, open).strip();
            String preludeLower = prelude.toLowerCase(Locale.ROOT);
            if (preludeLower.startsWith("@custom-media")
                    || preludeLower.startsWith("@custom-selector")) {
                int statementEnd = source.indexOf(';', offset);
                if (statementEnd >= 0 && (open < 0 || statementEnd < open)) {
                    offset = statementEnd + 1;
                    continue;
                }
            }
            if (preludeLower.startsWith("@layer")) {
                int statementEnd = source.indexOf(';', offset);
                if (statementEnd >= 0 && (open < 0 || statementEnd < open)) {
                    offset = statementEnd + 1;
                    continue;
                }
            }
            boolean media = preludeLower.startsWith("@media");
            boolean supports = preludeLower.startsWith("@supports");
            boolean keyframesAtRule = preludeLower.startsWith("@keyframes");
            boolean container = preludeLower.startsWith("@container");
            boolean layer = preludeLower.startsWith("@layer");
            boolean nestedAtRule = media || supports || keyframesAtRule
                    || container || layer;
            int close = nestedAtRule ? matchingBrace(source, open)
                    : source.indexOf('}', open + 1);
            if (close < 0) {
                int nested = source.indexOf('{', open + 1);
                if (!nestedAtRule && nested >= 0) {
                    offset = selectorStart(source, nested, open + 1);
                    continue;
                }
                return;
            }
            int nested = source.indexOf('{', open + 1);
            if (!nestedAtRule && nested >= 0 && nested < close) {
                offset = selectorStart(source, nested, open + 1);
                continue;
            }
            String body = source.substring(open + 1, close);
            if (media) {
                String query = prelude.substring(6).strip();
                parseRuleList(body, condition.and(new MediaCondition(query)),
                        sourceOrder, rules, keyframes);
                offset = close + 1;
                continue;
            }
            if (supports) {
                String conditionSource = prelude.substring(9).strip();
                if (evaluateSupportsCondition(conditionSource)) {
                    parseRuleList(body, condition, sourceOrder, rules, keyframes);
                }
                offset = close + 1;
                continue;
            }
            if (container || layer) {
                // Container- und Layer-Blöcke halten ihre Regeln; Queries werden
                // als immer-wahr angenähert, Layer als ungruppiert.
                parseRuleList(body, condition, sourceOrder, rules, keyframes);
                offset = close + 1;
                continue;
            }
            if (keyframesAtRule) {
                String name = prelude.substring("@keyframes".length()).strip();
                CssKeyframes parsed = parseKeyframes(name, body);
                if (parsed != null) {
                    keyframes.add(parsed);
                }
                offset = close + 1;
                continue;
            }
            if (prelude.startsWith("@")) {
                offset = close + 1;
                continue;
            }
            ParsedDeclarationBlock declarationBlock = parseDeclarationBlock(body);
            Map<String, String> declarations = declarationBlock.declarations();
            if (declarations.isEmpty()) {
                offset = close + 1;
                continue;
            }
            String selectorSource = recoverSelectorPrelude(prelude);
            try {
                for (var selector : SELECTOR_PARSER.parse(selectorSource).selectors()) {
                    rules.add(new CssRule(selector, declarations, sourceOrder[0], condition,
                            declarationBlock.importantProperties()));
                }
            } catch (SelectorParseException ignored) {
            }
            sourceOrder[0]++;
            offset = close + 1;
        }
    }

    private static int selectorStart(String source, int open, int lowerBound) {
        int line = Math.max(source.lastIndexOf('\n', open), source.lastIndexOf('\r', open));
        int semicolon = source.lastIndexOf(';', open);
        return Math.max(lowerBound, Math.max(line, semicolon) + 1);
    }

    private static void collectRuleSources(String source, List<String> result) {
        int offset = 0;
        while (offset < source.length()) {
            int open = source.indexOf('{', offset);
            int semicolon = source.indexOf(';', offset);
            if (open < 0) {
                if (semicolon >= 0
                        && isNamespaceStatement(source.substring(offset, semicolon + 1))) {
                    result.add(source.substring(offset, semicolon + 1).strip());
                }
                return;
            }
            if (semicolon >= 0 && semicolon < open
                    && isNamespaceStatement(source.substring(offset, semicolon + 1))) {
                result.add(source.substring(offset, semicolon + 1).strip());
                offset = semicolon + 1;
                continue;
            }
            int close = matchingBrace(source, open);
            if (close < 0) return;
            String prelude = source.substring(offset, open).strip();
            String body = source.substring(open + 1, close);
            String preludeLower = prelude.toLowerCase(Locale.ROOT);
            if (preludeLower.startsWith("@media") || preludeLower.startsWith("@supports")) {
                collectRuleSources(body, result);
            } else {
                result.add(source.substring(offset, close + 1).strip());
            }
            offset = close + 1;
        }
    }

    private boolean evaluateSupportsCondition(String condition) {
        String text = condition.strip();
        if (text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("not ")) {
            return !evaluateSupportsCondition(text.substring(4));
        }
        int depth = 0;
        boolean quoted = false;
        char quote = 0;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (quoted) {
                if (current == quote && (index == 0 || text.charAt(index - 1) != '\\')) {
                    quoted = false;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quoted = true;
                quote = current;
            } else if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
            } else if (depth == 0 && index > 0
                    && Character.isWhitespace(text.charAt(index - 1))) {
                if (text.regionMatches(true, index, "and ", 0, 4)) {
                    return evaluateSupportsCondition(text.substring(0, index))
                            && evaluateSupportsCondition(text.substring(index + 4));
                }
                if (text.regionMatches(true, index, "or ", 0, 3)) {
                    return evaluateSupportsCondition(text.substring(0, index))
                            || evaluateSupportsCondition(text.substring(index + 3));
                }
            }
        }
        text = text.strip();
        if (text.startsWith("(") && text.endsWith(")")) {
            String declaration = text.substring(1, text.length() - 1).strip();
            int colon = declaration.indexOf(':');
            if (colon <= 0) {
                return false;
            }
            String property = declaration.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = declaration.substring(colon + 1).strip()
                    .replaceFirst("(?is)\\s*!important\\s*$", "").strip();
            return supports(property, value);
        }
        if (lower.startsWith("selector(") && text.endsWith(")")) {
            String selectorSource = text.substring(9, text.length() - 1).strip();
            try {
                SELECTOR_PARSER.parse(selectorSource);
                return true;
            } catch (SelectorParseException ignored) {
                return false;
            }
        }
        return false;
    }

    private static boolean isNamespaceStatement(String statement) {
        return statement.strip().toLowerCase(Locale.ROOT).startsWith("@namespace");
    }

    private static int matchingBrace(String source, int open) {
        int depth = 0;
        char quote = 0;
        for (int index = open; index < source.length(); index++) {
            char current = source.charAt(index);
            if (quote != 0) {
                if (current == quote && (index == 0 || source.charAt(index - 1) != '\\')) quote = 0;
            } else if (current == '\'' || current == '"') {
                quote = current;
            } else if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return index;
            }
        }
        return -1;
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
        return new LinkedHashMap<>(parseDeclarationBlock(source).declarations());
    }

    ParsedDeclarationBlock parseDeclarationBlock(String source) {
        Map<String, String> declarations = new LinkedHashMap<>();
        Set<String> importantProperties = new LinkedHashSet<>();
        if (source == null || source.isBlank()) {
            return new ParsedDeclarationBlock(declarations, importantProperties);
        }
        source = COMMENTS.matcher(source).replaceAll("");
        for (String declaration : splitTopLevel(source, ';')) {
            int separator = declaration.indexOf(':');
            if (separator < 1) {
                continue;
            }
            String propertySource = declaration.substring(0, separator).trim();
            String property = propertySource.startsWith("--")
                    ? propertySource : propertySource.toLowerCase(Locale.ROOT);
            String rawValue = declaration.substring(separator + 1).trim();
            boolean important = rawValue.matches("(?is).*?\\s*!important\\s*$");
            if (important) {
                rawValue = rawValue.replaceFirst("(?is)\\s*!important\\s*$", "").strip();
            }
            String value = rawValue.toLowerCase(Locale.ROOT);
            Map<String, String> parsed = new LinkedHashMap<>();
            if (property.startsWith("--")) {
                if (!rawValue.isEmpty()) parsed.put(property, rawValue);
            } else if (containsVarFunction(rawValue) && supportsProperty(property)) {
                parsed.put(property, rawValue);
            } else if (property.equals("content")) {
                putContent(parsed, rawValue);
            } else if (property.equals("background")) {
                putBackground(parsed, rawValue);
            } else if (property.equals("background-image")) {
                putBackgroundImage(parsed, rawValue);
            } else if (property.equals("animation")) {
                putAnimationShorthand(parsed, rawValue);
            } else if (property.equals("transition")) {
                putTransitionShorthand(parsed, rawValue);
            } else if (property.equals("animation-name")
                    || property.equals("animation-duration")
                    || property.equals("animation-timing-function")
                    || property.equals("animation-delay")
                    || property.equals("animation-iteration-count")
                    || property.equals("animation-direction")
                    || property.equals("animation-fill-mode")) {
                putAnimationLonghand(parsed, property, rawValue);
            } else if (property.equals("transition-property")
                    || property.equals("transition-duration")
                    || property.equals("transition-timing-function")
                    || property.equals("transition-delay")) {
                putTransitionLonghand(parsed, property, rawValue);
            } else {
                parseDeclaration(parsed, property, value);
            }
            for (Map.Entry<String, String> entry : parsed.entrySet()) {
                if (important || !importantProperties.contains(entry.getKey())) {
                    declarations.put(entry.getKey(), entry.getValue());
                    if (important) importantProperties.add(entry.getKey());
                    else importantProperties.remove(entry.getKey());
                }
            }
        }
        return new ParsedDeclarationBlock(declarations, importantProperties);
    }

    record ParsedDeclarationBlock(Map<String, String> declarations,
                                  Set<String> importantProperties) {
        ParsedDeclarationBlock {
            declarations = java.util.Collections.unmodifiableMap(
                    new LinkedHashMap<>(declarations));
            importantProperties = java.util.Collections.unmodifiableSet(
                    new LinkedHashSet<>(importantProperties));
        }
    }

    private static void putContent(Map<String, String> target, String value) {
        String stripped = value.strip();
        String lower = stripped.toLowerCase(Locale.ROOT);
        if (lower.equals("normal") || lower.equals("none")
                || lower.equals("unset") || lower.equals("inherit")) {
            target.put("content", lower);
        } else if (isQuotedContent(stripped) || isAttrContent(stripped)) {
            target.put("content", stripped);
        }
    }

    private static boolean isQuotedContent(String value) {
        if (value.length() < 2 || value.charAt(0) != value.charAt(value.length() - 1)
                || value.charAt(0) != '\'' && value.charAt(0) != '"') return false;
        int backslashes = 0;
        for (int index = value.length() - 2; index >= 0 && value.charAt(index) == '\\'; index--) {
            backslashes++;
        }
        return backslashes % 2 == 0;
    }

    private static boolean isAttrContent(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("attr(") || !value.endsWith(")")) return false;
        String name = value.substring(5, value.length() - 1).strip();
        return name.matches("[A-Za-z_][A-Za-z0-9_-]*");
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
            case "background-image" -> supports(normalized, "url(example.png)");
            case "background-repeat" -> supports(normalized, "repeat");
            case "background-position" -> supports(normalized, "left top");
            case "background-size" -> supports(normalized, "100% auto");
            case "font-size" -> supports(normalized, "16px");
            case "font", "font-family" -> supports(normalized, "16px sans-serif");
            case "line-height" -> supports(normalized, "normal");
            case "content" -> supports(normalized, "\"text\"");
            case "font-weight" -> supports(normalized, "normal");
            case "font-style" -> supports(normalized, "normal");
            case "display" -> supports(normalized, "block");
            case "flex-direction" -> supports(normalized, "row");
            case "flex-wrap" -> supports(normalized, "wrap");
            case "justify-content" -> supports(normalized, "flex-start");
            case "align-items" -> supports(normalized, "stretch");
            case "place-content" -> supports(normalized, "center");
            case "text-shadow" -> supports(normalized, "none");
            case "shape-rendering" -> supports(normalized, "crispedges");
            case "filter" -> supports(normalized, "none");
            case "color-scheme" -> supports(normalized, "light");
            case "isolation" -> supports(normalized, "isolate");
            case "mix-blend-mode" -> supports(normalized, "normal");
            case "resize" -> supports(normalized, "vertical");
            case "scroll-margin-top" -> supports(normalized, "0");
            case "table-layout" -> supports(normalized, "fixed");
            case "backdrop-filter" -> supports(normalized, "none");
            case "fill-opacity", "stop-opacity" -> supports(normalized, "1");
            case "mask-image" -> supports(normalized, "none");
            case "text-indent" -> supports(normalized, "0");
            case "contain" -> supports(normalized, "layout");
            case "font-stretch" -> supports(normalized, "normal");
            case "stop-color" -> supports(normalized, "#fff");
            case "touch-action" -> supports(normalized, "manipulation");
            case "clip" -> supports(normalized, "rect(1px,1px,1px,1px)");
            case "mask-size" -> supports(normalized, "75%");
            case "place-self", "justify-self" -> supports(normalized, "start");
            case "animation-play-state" -> supports(normalized, "paused");
            case "justify-items" -> supports(normalized, "start");
            case "mask-position" -> supports(normalized, "50%");
            case "mask-repeat" -> supports(normalized, "no-repeat");
            case "stroke-dasharray" -> supports(normalized, "3 3");
            case "text-rendering" -> supports(normalized, "optimizelegibility");
            case "text-underline-offset" -> supports(normalized, "2px");
            case "backface-visibility" -> supports(normalized, "hidden");
            case "background-attachment" -> supports(normalized, "fixed");
            case "caret-color" -> supports(normalized, "auto");
            case "-webkit-text-decoration-color" -> supports(normalized, "blue");
            case "border-end-end-radius", "border-end-start-radius",
                 "border-start-end-radius", "border-start-start-radius" ->
                    supports(normalized, "4px");
            case "container", "container-type" -> supports(normalized, "inline-size");
            case "font-variant", "font-variant-ligatures", "font-variant-numeric" ->
                    supports(normalized, "normal");
            case "grid", "grid-auto-columns", "grid-auto-rows" ->
                    supports(normalized, "auto");
            case "mask" -> supports(normalized, "none");
            case "place-items" -> supports(normalized, "center");
            case "scrollbar-color" -> supports(normalized, "auto");
            case "break-after", "break-inside" -> supports(normalized, "auto");
            case "perspective" -> supports(normalized, "1000px");
            case "text-size-adjust" -> supports(normalized, "100%");
            case "align-self" -> supports(normalized, "stretch");
            case "align-content" -> supports(normalized, "stretch");
            case "order" -> supports(normalized, "0");
            case "gap", "row-gap", "column-gap" -> supports(normalized, "1px");
            case "flex", "flex-grow", "flex-shrink" -> supports(normalized, "1");
            case "flex-basis" -> supports(normalized, "auto");
            case "aspect-ratio" -> supports(normalized, "16 / 9");
            case "grid-template-columns", "grid-template-rows" ->
                    supports(normalized, "1fr");
            case "grid-template-areas" -> supports(normalized, "\"a\"");
            case "grid-template" -> supports(normalized, "\"a\" 1fr / 1fr");
            case "grid-area" -> supports(normalized, "auto");
            case "grid-row", "grid-column", "grid-row-start", "grid-row-end",
                 "grid-column-start", "grid-column-end" -> supports(normalized, "auto");
            case "grid-auto-flow" -> supports(normalized, "row");
            case "grid-gap" -> supports(normalized, "1px");
            case "object-fit" -> supports(normalized, "cover");
            case "opacity" -> supports(normalized, "0.5");
            case "fill" -> supports(normalized, "black");
            case "position" -> supports(normalized, "static");
            case "z-index" -> supports(normalized, "1");
            case "cursor" -> supports(normalized, "pointer");
            case "float" -> supports(normalized, "none");
            case "clear" -> supports(normalized, "none");
            case "top", "right", "bottom", "left" -> supports(normalized, "auto");
            case "width", "height", "min-width", "min-height" -> supports(normalized, "auto");
            case "max-width", "max-height" -> supports(normalized, "none");
            case "box-sizing" -> supports(normalized, "content-box");
            case "text-align" -> supports(normalized, "left");
            case "text-transform" -> supports(normalized, "uppercase");
            case "white-space" -> supports(normalized, "normal");
            case "text-decoration", "text-decoration-line" -> supports(normalized, "underline");
            case "text-decoration-color" -> supports(normalized, "black");
            case "-webkit-text-decoration", "-webkit-text-decoration-color" ->
                    supports(normalized, "underline");
            case "-webkit-text-fill-color", "-webkit-tap-highlight-color" ->
                    supports(normalized, "black");
            case "-webkit-user-select", "-webkit-font-smoothing", "-moz-osx-font-smoothing",
                 "-webkit-overflow-scrolling", "-webkit-backdrop-filter", "-webkit-line-clamp",
                 "-webkit-box-orient", "-webkit-appearance", "-webkit-hyphens",
                 "-moz-text-size-adjust", "-webkit-text-size-adjust" ->
                    supports(normalized, "none");
            case "list-style", "list-style-type" -> supports(normalized, "disc");
            case "overflow", "overflow-x", "overflow-y" -> supports(normalized, "visible");
            case "vertical-align" -> supports(normalized, "baseline");
            case "border-collapse" -> supports(normalized, "separate");
            case "border-radius" -> supports(normalized, "4px");
            case "box-shadow" -> supports(normalized, "0 1px 2px black");
            case "border-top-left-radius", "border-top-right-radius",
                 "border-bottom-right-radius", "border-bottom-left-radius" ->
                    supports(normalized, "4px");
            case "outline" -> supports(normalized, "2px solid black");
            case "outline-width" -> supports(normalized, "2px");
            case "outline-offset" -> supports(normalized, "2px");
            case "letter-spacing" -> supports(normalized, "0");
            case "word-break" -> supports(normalized, "break-all");
            case "appearance" -> supports(normalized, "none");
            case "text-overflow" -> supports(normalized, "ellipsis");
            case "overflow-wrap", "word-wrap" -> supports(normalized, "break-word");
            case "user-select" -> supports(normalized, "none");
            case "stroke" -> supports(normalized, "black");
            case "stroke-width" -> supports(normalized, "1px");
            case "scrollbar-width" -> supports(normalized, "auto");
            case "animation" -> supports(normalized, "1s linear none");
            case "animation-name" -> supports(normalized, "fade-in");
            case "animation-duration" -> supports(normalized, "1s");
            case "animation-timing-function" -> supports(normalized, "linear");
            case "animation-delay" -> supports(normalized, "0s");
            case "animation-iteration-count" -> supports(normalized, "infinite");
            case "animation-direction" -> supports(normalized, "normal");
            case "animation-fill-mode" -> supports(normalized, "none");
            case "transition" -> supports(normalized, "all 80ms ease-out");
            case "transition-property" -> supports(normalized, "all");
            case "transition-duration" -> supports(normalized, "80ms");
            case "transition-timing-function" -> supports(normalized, "ease-out");
            case "transition-delay" -> supports(normalized, "0s");
            case "clip-path" -> supports(normalized, "none");
            case "text-wrap" -> supports(normalized, "balance");
            case "tab-size" -> supports(normalized, "8");
            case "direction" -> supports(normalized, "ltr");
            case "background-clip" -> supports(normalized, "padding-box");
            case "object-position" -> supports(normalized, "center");
            case "flex-flow" -> supports(normalized, "row wrap");
            case "transform" -> supports(normalized, "none");
            case "transform-origin" -> supports(normalized, "50% 50%");
            case "visibility" -> supports(normalized, "visible");
            case "pointer-events" -> supports(normalized, "auto");
            case "outline-color" -> supports(normalized, "black");
            case "outline-style" -> supports(normalized, "solid");
            case "margin", "margin-top", "margin-right", "margin-bottom", "margin-left",
                 "margin-inline", "margin-inline-start", "margin-inline-end",
                 "margin-block", "margin-block-start", "margin-block-end",
                 "padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
                 "padding-inline", "padding-inline-start", "padding-inline-end",
                 "padding-block", "padding-block-start", "padding-block-end",
                 "border", "border-width", "border-top-width", "border-right-width",
                 "border-bottom-width", "border-left-width",
                 "border-inline", "border-inline-start", "border-inline-end",
                 "border-block", "border-block-start", "border-block-end" ->
                    supports(normalized, "0");
            case "inset", "inset-inline", "inset-inline-start", "inset-inline-end",
                 "inset-block", "inset-block-start", "inset-block-end",
                 "inline-size", "block-size", "min-inline-size", "min-block-size",
                 "max-inline-size", "max-block-size" -> supports(normalized, "auto");
            case "border-top", "border-right", "border-bottom", "border-left" ->
                    supports(normalized, "1px solid black");
            case "border-color", "border-top-color", "border-right-color",
                 "border-bottom-color", "border-left-color" -> supports(normalized, "black");
            case "border-style", "border-top-style", "border-right-style",
                 "border-bottom-style", "border-left-style" -> supports(normalized, "solid");
            default -> false;
        };
    }

    private static void parseDeclaration(Map<String, String> target, String property, String value) {
        switch (property) {
            case "content" -> {
                if (value.equals("normal") || value.equals("none")
                        || value.equals("unset") || value.equals("inherit")
                        || isQuotedContent(value) || isAttrContent(value)) {
                    target.put(property, value);
                }
            }
            case "color" -> {
                if (value.equals("inherit")) target.put(property, value);
                else putColor(target, property, value);
            }
            case "background-color" -> {
                if (value.equals("initial")) {
                    target.put(property, "transparent");
                } else if (SYSTEM_COLORS.contains(value)) {
                    target.put(property, value);
                } else {
                    putColor(target, property, value);
                }
            }
            case "background" -> putBackground(target, value);
            case "background-repeat" -> {
                String first = null;
                boolean valid = true;
                for (String part : splitTopLevel(value, ',')) {
                    String candidate = part.strip();
                    if (!candidate.equals("repeat") && !candidate.equals("repeat-x")
                            && !candidate.equals("repeat-y") && !candidate.equals("no-repeat")) {
                        valid = false;
                        break;
                    }
                    if (first == null) {
                        first = candidate;
                    }
                }
                if (valid && first != null) {
                    target.put(property, first);
                }
            }
            case "background-position" -> putBackgroundPosition(target, value);
            case "background-size" -> putBackgroundSize(target, value);
            case "font-size" -> {
                if (value.equals("inherit")) {
                    target.put(property, value);
                } else {
                    putIfMatches(target, property, value, FONT_SIZE);
                }
            }
            case "word-break" -> {
                if (value.equals("normal") || value.equals("break-all")
                        || value.equals("break-word") || value.equals("keep-all")) {
                    target.put(property, value);
                }
            }
            case "appearance", "-webkit-appearance" -> {
                if (value.equals("none") || value.equals("auto") || value.equals("textfield")
                        || value.equals("button")) {
                    target.put(property, value);
                }
            }
            case "font-family" -> {
                if (!value.isBlank()) target.put(property, value);
            }
            case "line-height" -> putIfMatches(target, property, value, LINE_HEIGHT);
            case "font" -> expandFont(target, value);
            case "font-weight" -> {
                if (value.equals("normal") || value.equals("bold") || value.equals("bolder")
                        || value.equals("lighter") || value.equals("inherit")
                        || FONT_WEIGHT.matcher(value).matches()) {
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
                        || value.equals("flex") || value.equals("inline-flex")
                        || value.equals("grid") || value.equals("inline-grid")
                        || value.equals("table") || value.equals("inline-table")
                        || value.equals("table-row-group") || value.equals("table-header-group")
                        || value.equals("table-footer-group") || value.equals("table-row")
                        || value.equals("table-cell") || value.equals("table-column-group")
                        || value.equals("table-column") || value.equals("table-caption")
                        || value.equals("inherit") || value.equals("-webkit-box")
                        || value.equals("contents")) {
                    target.put(property, value);
                }
            }
            case "grid-template-columns", "grid-template-rows" ->
                    putGridTrackList(target, property, value);
            case "grid-template-areas" -> {
                if (isGridAreasValue(value)) {
                    target.put(property, value);
                }
            }
            case "grid-template" -> putGridTemplate(target, value);
            case "grid-area" -> putGridArea(target, value);
            case "grid-row", "grid-column" -> putGridLineShorthand(target, property, value);
            case "grid-row-start", "grid-row-end", "grid-column-start", "grid-column-end" ->
                    putGridLine(target, property, value);
            case "grid-auto-flow" -> {
                if (value.equals("row") || value.equals("column")
                        || value.equals("row dense") || value.equals("column dense")) {
                    target.put(property, value);
                }
            }
            case "grid-gap" -> expandGap(target, value);
            case "grid-row-gap" -> putIfMatches(target, "row-gap", value, POSITIVE_LENGTH);
            case "grid-column-gap" -> putIfMatches(target, "column-gap", value, POSITIVE_LENGTH);
            case "flex-flow" -> {
                String[] tokens = value.split("\\s+");
                if (tokens.length < 1 || tokens.length > 2) {
                    break;
                }
                String direction = null;
                String wrap = null;
                for (String token : tokens) {
                    if (token.equals("row") || token.equals("row-reverse")
                            || token.equals("column") || token.equals("column-reverse")) {
                        direction = token;
                    } else if (token.equals("nowrap") || token.equals("wrap")
                            || token.equals("wrap-reverse")) {
                        wrap = token;
                    } else {
                        direction = null;
                        wrap = null;
                        break;
                    }
                }
                if (direction != null) {
                    target.put("flex-direction", direction);
                }
                if (wrap != null) {
                    target.put("flex-wrap", wrap);
                }
            }
            case "flex-direction" -> {
                if (value.equals("row") || value.equals("row-reverse")
                        || value.equals("column") || value.equals("column-reverse")) {
                    target.put(property, value);
                }
            }
            case "flex-wrap" -> {
                if (value.equals("nowrap") || value.equals("wrap")
                        || value.equals("wrap-reverse")) {
                    target.put(property, value);
                }
            }
            case "justify-content" -> {
                if (value.equals("flex-start") || value.equals("center")
                        || value.equals("flex-end") || value.equals("space-between")
                        || value.equals("space-around") || value.equals("space-evenly")
                        || value.equals("start") || value.equals("end")
                        || value.equals("left") || value.equals("right")
                        || value.equals("stretch")) {
                    target.put(property, value);
                }
            }
            case "align-items" -> {
                if (value.equals("stretch") || value.equals("flex-start")
                        || value.equals("center") || value.equals("flex-end")
                        || value.equals("baseline") || value.equals("start")
                        || value.equals("end")) {
                    target.put(property, value);
                }
            }
            case "align-self", "justify-self" -> {
                if (isAlignSelfValue(value)) {
                    target.put(property, value);
                }
            }
            case "align-content" -> {
                if (value.equals("flex-start") || value.equals("flex-end")
                        || value.equals("center") || value.equals("space-between")
                        || value.equals("space-around") || value.equals("space-evenly")
                        || value.equals("stretch") || value.equals("normal")) {
                    target.put(property, value);
                }
            }
            case "order" -> {
                if (value.equals("inherit")) {
                    target.put(property, "0");
                } else if (INTEGER.matcher(value).matches()) {
                    target.put(property, value);
                }
            }
            case "gap" -> expandGap(target, value);
            case "row-gap", "column-gap" ->
                    putIfMatches(target, property, value, POSITIVE_LENGTH);
            case "flex-grow" -> putIfMatches(target, property, value, NON_NEGATIVE_NUMBER);
            case "flex-shrink" -> putIfMatches(target, property, value, NON_NEGATIVE_NUMBER);
            case "flex-basis" -> putIfMatches(target, property, value, DIMENSION);
            case "flex" -> expandFlex(target, value);
            case "opacity" -> putUnitInterval(target, property, value);
            case "animation" -> putAnimationShorthand(target, value);
            case "animation-name", "animation-duration", "animation-timing-function",
                 "animation-delay", "animation-iteration-count", "animation-direction",
                 "animation-fill-mode" -> putAnimationLonghand(target, property, value);
            case "transition" -> putTransitionShorthand(target, value);
            case "transition-property", "transition-duration", "transition-timing-function",
                 "transition-delay" -> putTransitionLonghand(target, property, value);
            case "clip-path" -> {
                if (value.equals("none") || isClipPathFunction(value)) {
                    target.put(property, value);
                }
            }
            case "text-wrap" -> {
                if (value.equals("wrap") || value.equals("nowrap") || value.equals("balance")
                        || value.equals("pretty")) {
                    target.put(property, value);
                }
            }
            case "tab-size" -> {
                if (INTEGER.matcher(value).matches() || DIMENSION.matcher(value).matches()) {
                    target.put(property, value);
                }
            }
            case "direction" -> {
                if (value.equals("ltr") || value.equals("rtl")) {
                    target.put(property, value);
                }
            }
            case "background-clip" -> {
                if (value.equals("border-box") || value.equals("padding-box")
                        || value.equals("content-box") || value.equals("text")) {
                    target.put(property, value);
                }
            }
            case "object-position" -> {
                if (isObjectPosition(value)) {
                    target.put(property, value);
                }
            }
            case "user-select" -> {
                if (value.equals("none") || value.equals("all") || value.equals("text")
                        || value.equals("auto") || value.equals("contain")
                        || value.equals("unset") || value.equals("inherit")) {
                    target.put(property, value);
                }
            }
            case "stroke", "stroke-color" -> putColor(target, property, value);
            case "stroke-width" -> putIfMatches(target, property, value, POSITIVE_LENGTH);
            case "scrollbar-width" -> {
                if (value.equals("none") || value.equals("auto") || value.equals("thin")) {
                    target.put(property, value);
                }
            }
            case "aspect-ratio" -> {
                if (value.equals("unset") || value.equals("inherit")) {
                    target.put(property, value);
                } else {
                    putIfMatches(target, property, value, ASPECT_RATIO);
                }
            }
            case "object-fit" -> {
                if (value.equals("fill") || value.equals("contain") || value.equals("cover")
                        || value.equals("none") || value.equals("scale-down")) {
                    target.put(property, value);
                }
            }
            case "fill" -> {
                if (value.equals("currentcolor") || value.equals("none")
                        || CssColor.isSupported(value)) target.put(property, value);
            }
            case "border-collapse" -> {
                if (value.equals("separate") || value.equals("collapse")) {
                    target.put(property, value);
                }
            }
            case "position" -> {
                if (value.equals("static") || value.equals("relative")
                        || value.equals("absolute") || value.equals("sticky")
                        || value.equals("fixed")) {
                    target.put(property, value);
                }
            }
            case "z-index" -> {
                if (value.equals("auto") || INTEGER.matcher(value).matches()) {
                    target.put(property, value);
                }
            }
            case "cursor" -> {
                if (value.equals("default") || value.equals("auto")
                        || value.equals("pointer") || value.equals("text")
                        || value.equals("crosshair") || value.equals("help")
                        || value.equals("move") || value.equals("grab")
                        || value.equals("grabbing") || value.equals("not-allowed")
                        || value.equals("wait") || value.equals("progress")
                        || value.equals("zoom-in") || value.equals("zoom-out")
                        || value.equals("cell") || value.equals("alias")
                        || value.equals("copy") || value.equals("no-drop")
                        || value.equals("context-menu") || value.equals("vertical-text")
                        || value.equals("all-scroll") || value.equals("col-resize")
                        || value.equals("row-resize") || value.equals("ns-resize")
                        || value.equals("ew-resize") || value.equals("n-resize")
                        || value.equals("s-resize") || value.equals("e-resize")
                        || value.equals("w-resize") || value.equals("ne-resize")
                        || value.equals("nw-resize") || value.equals("se-resize")
                        || value.equals("sw-resize") || value.equals("nwse-resize")
                        || value.equals("nesw-resize")) {
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
            case "top", "right", "bottom", "left" -> {
                if (value.equals("unset")) {
                    target.put(property, "auto");
                } else if (isMathFunctionValue(value)) {
                    target.put(property, value);
                } else {
                    putIfMatches(target, property, value, POSITION_OFFSET);
                }
            }
            case "width", "height", "min-width", "min-height" -> {
                if (value.equals("max-content") || value.equals("min-content")
                        || value.equals("fit-content") || value.equals("unset")
                        || value.equals("inherit") || value.startsWith("clamp(")
                        || isMathFunctionValue(value)) {
                    target.put(property, value);
                } else {
                    putIfMatches(target, property, value, DIMENSION);
                }
            }
            case "max-width", "max-height" -> {
                if (value.equals("max-content") || value.equals("min-content")
                        || value.equals("fit-content") || value.equals("unset")
                        || value.equals("inherit")
                        || isMathFunctionValue(value)) {
                    target.put(property, value);
                } else {
                    putIfMatches(target, property, value, MAX_DIMENSION);
                }
            }
            case "box-sizing" -> {
                if (value.equals("content-box") || value.equals("border-box")
                        || value.equals("initial") || value.equals("inherit")) {
                    target.put(property, value);
                }
            }
            case "overflow", "overflow-x", "overflow-y" -> {
                if (isOverflowValue(value)) {
                    target.put(property, value);
                }
            }
            case "vertical-align" -> {
                if (value.equals("baseline") || value.equals("top") || value.equals("middle")
                        || value.equals("bottom") || value.equals("text-top")
                        || value.equals("text-bottom")
                        || POSITION_OFFSET.matcher(value).matches()) {
                    target.put(property, value);
                }
            }
            case "text-align" -> {
                if (value.equals("left") || value.equals("center") || value.equals("right")) {
                    target.put(property, value);
                }
            }
            case "visibility" -> {
                if (value.equals("visible") || value.equals("hidden")
                        || value.equals("collapse")) {
                    target.put(property, value);
                }
            }
            case "pointer-events" -> {
                if (value.equals("auto") || value.equals("none")
                        || value.equals("all") || value.equals("visible")
                        || value.equals("painted") || value.equals("fill")
                        || value.equals("stroke")) {
                    target.put(property, value);
                }
            }
            case "white-space" -> {
                if (value.equals("normal") || value.equals("nowrap") || value.equals("pre")
                        || value.equals("pre-wrap") || value.equals("pre-line")
                        || value.equals("break-spaces") || value.equals("unset")
                        || value.equals("inherit")) {
                    target.put(property, value);
                }
            }
            case "letter-spacing" -> {
                if (value.equals("normal") || value.equals("inherit")
                        || value.equals("unset")) {
                    target.put(property, value);
                } else if (LETTER_SPACING.matcher(value).matches()) {
                    target.put(property, value);
                }
            }
            case "text-overflow" -> {
                if (value.equals("clip") || value.equals("ellipsis")
                        || value.equals("inherit") || value.equals("unset")
                        || value.equals("revert")) {
                    target.put(property, value);
                }
            }
            case "overflow-wrap", "word-wrap" -> {
                if (value.equals("normal") || value.equals("break-word")
                        || value.equals("anywhere")) {
                    target.put(property, value);
                }
            }
            case "text-transform" -> {
                if (value.equals("none") || value.equals("uppercase")
                        || value.equals("lowercase") || value.equals("capitalize")) {
                    target.put(property, value);
                }
            }
            case "text-decoration", "text-decoration-line" -> {
                if (value.equals("none") || value.equals("underline")
                        || value.equals("line-through") || value.equals("overline")
                        || value.equals("inherit")) {
                    target.put("text-decoration-line", value);
                } else if (property.equals("text-decoration")) {
                    expandTextDecoration(target, value);
                }
            }
            case "text-decoration-color" -> putColor(target, property, value);
            case "list-style", "list-style-type" -> {
                String type = listStyleType(value);
                if (type != null) target.put("list-style-type", type);
            }
            case "margin", "padding" -> {
                if (value.equals("unset")) {
                    target.put(property, "0");
                } else if (value.equals("inherit")) {
                    target.put(property, "inherit");
                } else {
                    expandLengths(target, property, value,
                            property.equals("margin") ? MARGIN_LENGTH : POSITIVE_LENGTH, "");
                }
            }
            case "clip" -> {
                if (value.equals("auto") || value.equals("inherit")
                        || value.matches("rect\\s*\\([^)]+\\)")) {
                    target.put(property, value);
                }
            }
            case "mask-size" -> {
                if (value.equals("auto") || value.equals("cover")
                        || value.equals("contain")
                        || DIMENSION.matcher(value).matches()
                        || POSITIVE_LENGTH.matcher(value).matches()) {
                    target.put(property, value);
                }
            }
            case "place-self" -> {
                String[] tokens = value.split("\\s+");
                if (tokens.length < 1 || tokens.length > 2
                        || !isAlignSelfValue(tokens[0])
                        || tokens.length == 2 && !isAlignSelfValue(tokens[1])) {
                    break;
                }
                target.put("align-self", tokens[0]);
                target.put("justify-self", tokens.length == 1 ? tokens[0] : tokens[1]);
            }
            case "place-content" -> {
                String[] tokens = value.split("\\s+");
                if (tokens.length < 1 || tokens.length > 2) {
                    break;
                }
                if (!tokens[0].equals("flex-start") && !tokens[0].equals("flex-end")
                        && !tokens[0].equals("center") && !tokens[0].equals("space-between")
                        && !tokens[0].equals("space-around") && !tokens[0].equals("space-evenly")
                        && !tokens[0].equals("stretch") && !tokens[0].equals("normal")
                        && !tokens[0].equals("start") && !tokens[0].equals("end")) {
                    break;
                }
                if (tokens.length == 2 && !tokens[1].equals("flex-start")
                        && !tokens[1].equals("flex-end") && !tokens[1].equals("center")
                        && !tokens[1].equals("space-between") && !tokens[1].equals("space-around")
                        && !tokens[1].equals("space-evenly") && !tokens[1].equals("start")
                        && !tokens[1].equals("end") && !tokens[1].equals("left")
                        && !tokens[1].equals("right")) {
                    break;
                }
                target.put("align-content", tokens[0]);
                target.put("justify-content", tokens.length == 1 ? tokens[0] : tokens[1]);
            }
            case "text-shadow" -> {
                if (value.equals("none") || isTextShadow(value)) {
                    target.put(property, value);
                }
            }
            case "shape-rendering" -> {
                if (value.equals("auto") || value.equals("crispedges")
                        || value.equals("geometricprecision")
                        || value.equals("optimizespeed")
                        || value.equals("optimizequality")) {
                    target.put(property, value);
                }
            }
            case "filter", "backdrop-filter" -> {
                if (value.equals("none") || isFilterFunctionList(value)) {
                    target.put(property, value);
                }
            }
            case "fill-opacity", "stop-opacity", "flood-opacity",
                 "stroke-opacity" -> putUnitInterval(target, property, value);
            case "mask-image" -> {
                if (value.equals("none") || value.startsWith("linear-gradient(")
                        || com.browicy.engine.render.CssUrl.parseSingle(value) != null) {
                    target.put(property, value);
                }
            }
            case "text-indent" -> putIfMatches(target, property, value, POSITION_OFFSET);
            case "contain" -> {
                if (value.equals("none") || value.equals("strict")
                        || value.equals("content") || value.equals("layout")
                        || value.equals("paint") || value.equals("size")
                        || value.equals("layout paint") || value.equals("layout style")
                        || value.equals("layout paint size")) {
                    target.put(property, value);
                }
            }
            case "font-stretch" -> {
                if (value.equals("unset") || value.equals("normal")
                        || value.equals("condensed") || value.equals("expanded")
                        || value.equals("semi-condensed") || value.equals("semi-expanded")
                        || value.equals("extra-condensed") || value.equals("extra-expanded")
                        || value.equals("ultra-condensed") || value.equals("ultra-expanded")
                        || value.matches("[0-9]*\\.?[0-9]+%")) {
                    target.put(property, value);
                }
            }
            case "stop-color", "flood-color", "lighting-color" -> {
                if (isColorValue(value)) {
                    target.put(property, value);
                }
            }
            case "touch-action" -> {
                if (value.equals("auto") || value.equals("none")
                        || value.equals("manipulation") || value.equals("pan-x")
                        || value.equals("pan-y") || value.equals("pan-left")
                        || value.equals("pan-right") || value.equals("pan-up")
                        || value.equals("pan-down") || value.equals("pinch-zoom")) {
                    target.put(property, value);
                }
            }
            case "animation-play-state" -> {
                if (value.equals("running") || value.equals("paused")) {
                    target.put(property, value);
                }
            }
            case "justify-items" -> {
                if (value.equals("auto") || value.equals("normal")
                        || value.equals("stretch") || value.equals("start")
                        || value.equals("end") || value.equals("center")
                        || value.equals("left") || value.equals("right")
                        || value.equals("self-start") || value.equals("self-end")) {
                    target.put(property, value);
                }
            }
            case "mask-position" -> {
                if (value.equals("left") || value.equals("center")
                        || value.equals("right") || value.equals("top")
                        || value.equals("bottom")
                        || BACKGROUND_LENGTH.matcher(value).matches()) {
                    target.put(property, value);
                }
            }
            case "mask-repeat" -> {
                if (value.equals("repeat") || value.equals("no-repeat")
                        || value.equals("repeat-x") || value.equals("repeat-y")) {
                    target.put(property, value);
                }
            }
            case "stroke-dasharray" -> {
                boolean valid = true;
                for (String token : value.strip().split("\\s+")) {
                    if (!NON_NEGATIVE_NUMBER.matcher(token).matches()
                            && !token.matches("[0-9]*\\.?[0-9]+px")) {
                        valid = false;
                        break;
                    }
                }
                if (valid && !value.isBlank()) {
                    target.put(property, value);
                }
            }
            case "text-rendering" -> {
                if (value.equals("auto") || value.equals("optimizespeed")
                        || value.equals("optimizelegibility")
                        || value.equals("geometricprecision")) {
                    target.put(property, value);
                }
            }
            case "text-underline-offset" -> {
                if (value.equals("auto") || POSITION_OFFSET.matcher(value).matches()) {
                    target.put(property, value);
                }
            }
            case "backface-visibility" -> {
                if (value.equals("visible") || value.equals("hidden")) {
                    target.put(property, value);
                }
            }
            case "background-attachment" -> {
                if (value.equals("scroll") || value.equals("fixed")
                        || value.equals("local")) {
                    target.put(property, value);
                }
            }
            case "caret-color" -> {
                if (isColorValue(value) || value.equals("auto")
                        || value.equals("transparent")) {
                    target.put(property, value);
                }
            }
            case "-ms-overflow-style" -> {
                if (value.equals("auto") || value.equals("scrollbar")
                        || value.equals("none") || value.equals("scroll")) {
                    target.put("overflow", value.equals("scrollbar") ? "auto" : value);
                }
            }
            case "-webkit-text-decoration-color" -> {
                if (isColorValue(value)) {
                    target.put("text-decoration-color", value);
                }
            }
            case "border-end-end-radius", "border-end-start-radius",
                 "border-start-end-radius", "border-start-start-radius" -> {
                if (RADIUS_LENGTH.matcher(value).matches()) {
                    target.put("border-radius", value);
                }
            }
            case "container-type" -> {
                if (value.equals("normal") || value.equals("inline-size")
                        || value.equals("size")) {
                    target.put(property, value);
                }
            }
            case "container" -> {
                if (value.isBlank()) {
                    break;
                }
                target.put(property, value);
            }
            case "font-variant" -> target.put(property, value);
            case "font-variant-ligatures" -> {
                if (value.equals("normal") || value.equals("none")
                        || value.equals("contextual") || value.equals("common-ligatures")) {
                    target.put(property, value);
                }
            }
            case "font-variant-numeric" -> {
                if (value.equals("normal") || value.equals("lining-nums")
                        || value.equals("tabular-nums") || value.equals("oldstyle-nums")
                        || value.equals("lining-nums tabular-nums")) {
                    target.put(property, value);
                }
            }
            case "grid" -> {
                if (value.equals("none") || value.contains("/")) {
                    target.put(property, value);
                }
            }
            case "grid-auto-columns", "grid-auto-rows" -> {
                if (value.equals("auto") || value.equals("min-content")
                        || value.equals("max-content")
                        || GRID_TRACK.matcher(value).matches()) {
                    target.put(property, value);
                }
            }
            case "mask" -> {
                if (value.equals("none") || value.startsWith("url(")
                        || value.startsWith("linear-gradient(")
                        || value.startsWith("radial-gradient(")) {
                    target.put(property, value);
                }
            }
            case "place-items" -> {
                String[] tokens = value.split("\\s+");
                if (tokens.length < 1 || tokens.length > 2) {
                    break;
                }
                if (!isAlignSelfValue(tokens[0])
                        || tokens.length == 2 && !isAlignSelfValue(tokens[1])) {
                    break;
                }
                target.put("align-items", tokens[0]);
                target.put("justify-items", tokens.length == 1 ? tokens[0] : tokens[1]);
            }
            case "scrollbar-color" -> {
                if (value.equals("auto") || isColorValue(value)
                        || value.contains("transparent")) {
                    target.put(property, value);
                }
            }
            case "color-scheme" -> {
                if (value.equals("normal") || value.equals("light")
                        || value.equals("dark") || value.equals("light dark")
                        || value.equals("only light") || value.equals("only dark")) {
                    target.put(property, value);
                }
            }
            case "isolation" -> {
                if (value.equals("auto") || value.equals("isolate")) {
                    target.put(property, value);
                }
            }
            case "mix-blend-mode" -> {
                if (value.equals("normal") || value.equals("multiply")
                        || value.equals("screen") || value.equals("overlay")
                        || value.equals("darken") || value.equals("lighten")
                        || value.equals("color-dodge") || value.equals("color-burn")
                        || value.equals("hard-light") || value.equals("soft-light")
                        || value.equals("difference") || value.equals("exclusion")
                        || value.equals("hue") || value.equals("saturation")
                        || value.equals("color") || value.equals("luminosity")
                        || value.equals("plus-lighter") || value.equals("plus-darker")) {
                    target.put(property, value);
                }
            }
            case "resize" -> {
                if (value.equals("none") || value.equals("both")
                        || value.equals("horizontal") || value.equals("vertical")
                        || value.equals("block") || value.equals("inline")) {
                    target.put(property, value);
                }
            }
            case "scroll-margin", "scroll-margin-top", "scroll-margin-right",
                 "scroll-margin-bottom", "scroll-margin-left",
                 "scroll-margin-block", "scroll-margin-inline",
                 "scroll-margin-block-start", "scroll-margin-block-end",
                 "scroll-margin-inline-start", "scroll-margin-inline-end" ->
                    putIfMatches(target, property, value, POSITION_OFFSET);
            case "table-layout" -> {
                if (value.equals("auto") || value.equals("fixed")) {
                    target.put(property, value);
                }
            }
            case "margin-top", "margin-right", "margin-bottom", "margin-left" -> {
                if (value.equals("inherit")) {
                    target.put(property, "inherit");
                } else {
                    putIfMatches(target, property, value, MARGIN_LENGTH);
                }
            }
            case "padding-top", "padding-right", "padding-bottom", "padding-left" -> {
                if (value.equals("inherit")) {
                    target.put(property, "inherit");
                } else {
                    putIfMatches(target, property, value, POSITIVE_LENGTH);
                }
            }
            case "margin-inline", "margin-inline-start", "margin-inline-end",
                 "margin-block", "margin-block-start", "margin-block-end" ->
                    putLogicalLengths(target, property, value, "margin", MARGIN_LENGTH);
            case "padding-inline", "padding-inline-start", "padding-inline-end",
                 "padding-block", "padding-block-start", "padding-block-end" ->
                    putLogicalLengths(target, property, value, "padding", POSITIVE_LENGTH);
            case "inset", "inset-inline", "inset-inline-start", "inset-inline-end",
                 "inset-block", "inset-block-start", "inset-block-end" ->
                    putLogicalOffsets(target, property, value);
            case "border-inline", "border-inline-start", "border-inline-end",
                 "border-block", "border-block-start", "border-block-end" ->
                    putLogicalBorders(target, property, value);
            case "inline-size" -> putIfMatches(target, "width", value, DIMENSION);
            case "block-size" -> putIfMatches(target, "height", value, DIMENSION);
            case "min-inline-size", "min-block-size" -> putIfMatches(target,
                    property.equals("min-inline-size") ? "min-width" : "min-height",
                    value, DIMENSION);
            case "max-inline-size", "max-block-size" -> putIfMatches(target,
                    property.equals("max-inline-size") ? "max-width" : "max-height",
                    value, MAX_DIMENSION);
            case "border-width" -> expandLengths(target, "border", value, POSITIVE_LENGTH, "-width");
            case "border-color" -> {
                if (SYSTEM_COLORS.contains(value)) {
                    target.put(property, value);
                } else {
                    expandColors(target, value);
                }
            }
            case "border-style" -> expandBorderStyles(target, value);
            case "border" -> expandBorder(target, null, value);
            case "border-radius" -> {
                if (value.equals("inherit") || value.equals("unset")) {
                    target.put(property, value);
                } else {
                    putBorderRadius(target, value);
                }
            }
            case "box-shadow" -> {
                if (isBoxShadowValue(value)) {
                    target.put(property, value);
                }
            }
            case "border-top-left-radius", "border-top-right-radius",
                 "border-bottom-right-radius", "border-bottom-left-radius" -> {
                if (value.equals("inherit") || value.equals("unset")) {
                    target.put(property, value);
                } else {
                    putIfMatches(target, property, value, RADIUS_LENGTH);
                }
            }
            case "outline" -> expandOutline(target, value);
            case "outline-width" -> putIfMatches(target, property, value, POSITIVE_LENGTH);
            case "outline-offset" -> {
                if (value.equals("unset") || value.equals("inherit")) {
                    target.put(property, "0");
                } else if (LENGTH_OR_ZERO.matcher(value).matches()
                        || value.matches("-?[0-9]+(px)?")) {
                    target.put(property, value);
                }
            }
            case "outline-color" -> putColor(target, property, value);
            case "outline-style" -> {
                if (value.equals("none") || value.equals("solid")
                        || value.equals("dotted") || value.equals("dashed")
                        || value.equals("double")) target.put(property, value);
            }
            case "transform" -> {
                String normalized = normalizeTransform(value);
                if (normalized != null) {
                    target.put(property, normalized);
                }
            }
            case "transform-origin" -> {
                if (isTransformOriginValue(value)) {
                    target.put(property, value.trim());
                }
            }
            default -> {
                if (ACCEPT_ANY_VALUES.contains(property)) {
                    target.put(property, value);
                } else if (property.startsWith("-webkit-") || property.startsWith("-moz-")
                        || property.startsWith("-ms-")) {
                    putPrefixed(target, property, value);
                } else {
                    parseLonghand(target, property, value);
                }
            }
        }
    }

    private static final Pattern MATH_ARG = Pattern.compile(
            "\\s*[-+]?[0-9]*\\.?[0-9]+(px|em|rem|vw|vh|%)?"
                    + "(\\s*[+-]\\s*[-+]?[0-9]*\\.?[0-9]+(px|em|rem|vw|vh|%)?)?\\s*");

    private static boolean isMathFunctionValue(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).strip();
        if (normalized.startsWith("calc(") && normalized.endsWith(")")) {
            return !normalized.substring(5, normalized.length() - 1).isEmpty();
        }
        if (!(normalized.startsWith("min(") || normalized.startsWith("max("))
                || !normalized.endsWith(")")) {
            return false;
        }
        String args = normalized.substring(4, normalized.length() - 1);
        if (args.isEmpty()) {
            return false;
        }
        for (String arg : splitTopLevel(args, ',')) {
            if (!MATH_ARG.matcher(arg).matches()) {
                return false;
            }
        }
        return true;
    }

    private static final Pattern TRANSFORM_FUNCTION =
            Pattern.compile("([a-zA-Z]+)\\(([^)]*)\\)");
    private static final Pattern TRANSFORM_OFFSET =
            Pattern.compile("[-+]?[0-9]*\\.?[0-9]+(px|rem|em|vw|vh|%)?");
    private static final Pattern TRANSFORM_NUMBER =
            Pattern.compile("[-+]?[0-9]*\\.?[0-9]+");
    private static final Pattern TRANSFORM_ANGLE =
            Pattern.compile("[-+]?[0-9]*\\.?[0-9]+(deg|rad|turn|grad)?");

    private static String normalizeTransform(String value) {
        String trimmed = value.trim();
        if (trimmed.equals("none")) {
            return "none";
        }
        Matcher matcher = TRANSFORM_FUNCTION.matcher(trimmed);
        StringBuilder normalized = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() != lastEnd
                    && !trimmed.substring(lastEnd, matcher.start()).isBlank()) {
                return null;
            }
            String name = matcher.group(1);
            String args = matcher.group(2).trim();
            if (!isKnownTransformFunction(name, args)) {
                return null;
            }
            if (!normalized.isEmpty()) {
                normalized.append(' ');
            }
            normalized.append(name.toLowerCase(Locale.ROOT)).append('(').append(args).append(')');
            lastEnd = matcher.end();
        }
        return normalized.isEmpty() ? null : normalized.toString();
    }

    private static boolean isKnownTransformFunction(String name, String args) {
        List<String> parts = new ArrayList<>();
        for (String part : args.split(",", -1)) {
            parts.add(part.trim());
        }
        String lowerName = name.toLowerCase(Locale.ROOT);
        return switch (lowerName) {
            case "translate" -> parts.size() >= 1 && parts.size() <= 2
                    && parts.stream().allMatch(CssParser::isTransformOffset);
            case "translatex", "translatey" -> parts.size() == 1
                    && isTransformOffset(parts.get(0));
            case "rotate" -> parts.size() == 1 && TRANSFORM_ANGLE.matcher(parts.get(0)).matches();
            case "scale" -> parts.size() >= 1 && parts.size() <= 2
                    && parts.stream().allMatch(CssParser::isTransformNumber);
            case "scalex", "scaley" -> parts.size() == 1 && isTransformNumber(parts.get(0));
            case "matrix" -> parts.size() == 6
                    && parts.stream().allMatch(CssParser::isTransformNumber);
            default -> false;
        };
    }

    private static boolean isTransformOffset(String value) {
        if (!TRANSFORM_OFFSET.matcher(value).matches()) {
            return false;
        }
        if (value.endsWith("%") || value.endsWith("px") || value.endsWith("rem")
                || value.endsWith("em") || value.endsWith("vw") || value.endsWith("vh")) {
            return true;
        }
        try {
            return Float.parseFloat(value) == 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isTransformNumber(String value) {
        return TRANSFORM_NUMBER.matcher(value).matches();
    }

    private static boolean isTransformOriginValue(String value) {
        String[] parts = value.trim().split("\\s+");
        if (parts.length < 1 || parts.length > 2) {
            return false;
        }
        for (String part : parts) {
            boolean keyword = part.equals("left") || part.equals("right")
                    || part.equals("top") || part.equals("bottom")
                    || part.equals("center");
            if (!keyword && !isTransformOffset(part)) {
                return false;
            }
        }
        return true;
    }

    private static List<String> logicalSides(String property) {
        return switch (property) {
            case "padding-inline", "margin-inline", "border-inline",
                 "inset-inline" -> List.of("left", "right");
            case "padding-inline-start", "margin-inline-start", "border-inline-start",
                 "inset-inline-start" -> List.of("left");
            case "padding-inline-end", "margin-inline-end", "border-inline-end",
                 "inset-inline-end" -> List.of("right");
            case "padding-block", "margin-block", "border-block",
                 "inset-block" -> List.of("top", "bottom");
            case "padding-block-start", "margin-block-start", "border-block-start",
                 "inset-block-start" -> List.of("top");
            default -> List.of("bottom"); // *-block-end
        };
    }

    private static void putPrefixed(Map<String, String> target, String property, String value) {
        switch (property) {
            case "-webkit-text-decoration" -> target.put("text-decoration-line", value);
            case "-webkit-text-decoration-color" ->
                    putColor(target, "text-decoration-color", value);
            case "-webkit-text-fill-color", "-webkit-tap-highlight-color",
                 "-webkit-user-select", "-webkit-font-smoothing", "-moz-osx-font-smoothing",
                 "-webkit-overflow-scrolling", "-webkit-backdrop-filter", "-webkit-line-clamp",
                 "-webkit-box-orient", "-webkit-appearance", "-webkit-hyphens",
                 "-moz-text-size-adjust", "-webkit-text-size-adjust" ->
                    target.put(property, value);
            default -> { /* unbekannte Präfix-Property ignorieren */ }
        }
    }

    private static void putLogicalLengths(Map<String, String> target, String property,
                                          String value, String prefix, Pattern pattern) {
        List<String> sides = logicalSides(property);
        String[] parts = value.trim().split("\\s+");
        if (parts.length < 1 || parts.length > sides.size() || parts.length > 2) {
            return;
        }
        for (String part : parts) {
            if (!pattern.matcher(part).matches()) {
                return;
            }
        }
        for (int index = 0; index < sides.size(); index++) {
            target.put(prefix + "-" + sides.get(index),
                    parts[Math.min(index, parts.length - 1)]);
        }
    }

    private static void putLogicalOffsets(Map<String, String> target, String property,
                                          String value) {
        List<String> sides = property.equals("inset")
                ? List.of("top", "right", "bottom", "left") : logicalSides(property);
        String[] parts = value.trim().split("\\s+");
        if (parts.length < 1 || parts.length > sides.size() || parts.length > 4) {
            return;
        }
        for (String part : parts) {
            if (!POSITION_OFFSET.matcher(part).matches()) {
                return;
            }
        }
        for (int index = 0; index < sides.size(); index++) {
            int source = switch (parts.length) {
                case 1 -> 0;
                case 2 -> index % 2;
                case 3 -> index == 2 ? 2 : index % 2;
                default -> index;
            };
            target.put(sides.get(index), parts[source]);
        }
    }

    private static void putLogicalBorders(Map<String, String> target, String property,
                                          String value) {
        for (String side : logicalSides(property)) {
            expandBorder(target, side, value);
        }
    }

    private static void putBorderRadius(Map<String, String> target, String value) {
        String horizontal = value;
        int slash = value.indexOf('/');
        if (slash >= 0) {
            horizontal = value.substring(0, slash).strip();
        }
        String[] parts = horizontal.trim().split("\\s+");
        if (parts.length < 1 || parts.length > 4) {
            return;
        }
        for (String part : parts) {
            if (!RADIUS_LENGTH.matcher(part).matches()) {
                return;
            }
        }
        String topLeft;
        String topRight;
        String bottomRight;
        String bottomLeft;
        switch (parts.length) {
            case 1 -> {
                topLeft = parts[0];
                topRight = parts[0];
                bottomRight = parts[0];
                bottomLeft = parts[0];
            }
            case 2 -> {
                topLeft = parts[0];
                bottomRight = parts[0];
                topRight = parts[1];
                bottomLeft = parts[1];
            }
            case 3 -> {
                topLeft = parts[0];
                topRight = parts[1];
                bottomLeft = parts[1];
                bottomRight = parts[2];
            }
            default -> {
                topLeft = parts[0];
                topRight = parts[1];
                bottomRight = parts[2];
                bottomLeft = parts[3];
            }
        }
        target.put("border-top-left-radius", topLeft);
        target.put("border-top-right-radius", topRight);
        target.put("border-bottom-right-radius", bottomRight);
        target.put("border-bottom-left-radius", bottomLeft);
    }

    private static void putBackgroundImage(Map<String, String> target, String value) {
        String stripped = value.strip();
        if (stripped.equalsIgnoreCase("none")) {
            target.put("background-image", "none");
            return;
        }
        if (isGradientFunction(stripped) || com.browicy.engine.render.CssUrl.parseSingle(stripped) != null) {
            target.put("background-image", stripped);
        }
    }

    private static String extractTopLevelGradient(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        int start = lower.indexOf("linear-gradient(");
        if (start < 0) {
            start = lower.indexOf("radial-gradient(");
        }
        if (start < 0) {
            return null;
        }
        int depth = 0;
        for (int index = start; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
                if (depth == 0) {
                    String candidate = value.substring(start, index + 1);
                    return isGradientFunction(candidate) ? candidate : null;
                }
            }
        }
        return null;
    }

    private static boolean isGradientFunction(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("linear-gradient(") && !normalized.startsWith("radial-gradient(")) {
            return false;
        }
        int nameLength = normalized.startsWith("radial-gradient(") ? "radial-gradient(".length()
                : "linear-gradient(".length();
        String body = value.substring(nameLength, value.length() - 1);
        if (body.isEmpty()) {
            return false;
        }
        for (String part : splitTopLevel(body, ',')) {
            if (!isGradientStop(part.strip())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isGradientStop(String part) {
        if (part.isEmpty()) {
            return false;
        }
        String lower = part.toLowerCase(Locale.ROOT);
        if (lower.equals("to top") || lower.equals("to bottom") || lower.equals("to left")
                || lower.equals("to right") || lower.equals("to top left")
                || lower.equals("to top right") || lower.equals("to bottom left")
                || lower.equals("to bottom right")) {
            return true;
        }
        if (GRADIENT_ANGLE.matcher(part).matches()) {
            return true;
        }
        if (lower.matches("[-+]?[0-9.]+% [-+]?[0-9.]+% at [-+]?[0-9.]+% [-+]?[0-9.]+%")
                || lower.matches("[-+]?[0-9.]+% at [-+]?[0-9.]+% [-+]?[0-9.]+%")
                || lower.matches("at [-+]?[0-9.]+(px|%)? [-+]?[0-9.]+(px|%)")
                || lower.matches("(circle|ellipse) at [-+]?[0-9.]+(px|%)? [-+]?[0-9.]+(px|%)")
                || lower.equals("ellipse") || lower.equals("circle")
                || lower.equals("closest-side") || lower.equals("closest-corner")
                || lower.equals("farthest-side") || lower.equals("farthest-corner")) {
            return true;
        }
        String[] tokens = part.split("\\s+");
        if (tokens.length < 1 || tokens.length > 3) {
            return false;
        }
        if (!isColorValue(tokens[0])) {
            return false;
        }
        if (tokens.length == 1) {
            return true;
        }
        if (!GRADIENT_POSITION.matcher(tokens[1]).matches()) {
            return false;
        }
        return tokens.length == 2 || GRADIENT_POSITION.matcher(tokens[2]).matches();
    }

    private static void putBackground(Map<String, String> target, String value) {
        String stripped = value.strip();
        if (stripped.equals("inherit")) {
            target.put("background-color", "transparent");
            target.put("background-image", "none");
            return;
        }
        List<com.browicy.engine.render.CssUrl.Token> urls =
                com.browicy.engine.render.CssUrl.tokens(stripped);
        if (urls.size() > 1) return;

        String withoutUrl = stripped;
        String image = "none";
        if (!urls.isEmpty()) {
            var token = urls.getFirst();
            image = stripped.substring(token.start(), token.end());
            withoutUrl = (stripped.substring(0, token.start())
                    + " " + stripped.substring(token.end())).strip();
        } else {
            String gradient = extractTopLevelGradient(stripped);
            if (gradient != null) {
                image = gradient;
                withoutUrl = (stripped.substring(0, stripped.indexOf(gradient))
                        + " " + stripped.substring(stripped.indexOf(gradient) + gradient.length()))
                        .replace(',', ' ').strip();
            }
        }

        int slash = topLevelSlash(withoutUrl);
        String beforeSlash = slash < 0 ? withoutUrl : withoutUrl.substring(0, slash).strip();
        String afterSlash = slash < 0 ? "" : withoutUrl.substring(slash + 1).strip();
        List<String> position = new ArrayList<>();
        String repeat = "repeat";
        String color = "transparent";

        for (String token : beforeSlash.split("\\s+")) {
            if (token.isBlank()) continue;
            String normalized = token.toLowerCase(Locale.ROOT);
            if (isBackgroundRepeat(normalized)) repeat = normalized;
            else if (isColorValue(normalized)) color = normalized;
            else if (normalized.equals("linktext")) color = "#0000ee";
            else position.add(normalized);
        }

        List<String> size = new ArrayList<>();
        if (!afterSlash.isEmpty()) {
            for (String token : afterSlash.split("\\s+")) {
                if (token.isBlank()) continue;
                String normalized = token.toLowerCase(Locale.ROOT);
                if (size.size() < 2 && isBackgroundSizeToken(normalized)) size.add(normalized);
                else if (isBackgroundRepeat(normalized)) repeat = normalized;
                else if (isColorValue(normalized)) color = normalized;
                else return;
            }
            if (size.isEmpty()) return;
        }

        Map<String, String> parsedPosition = new LinkedHashMap<>();
        if (!position.isEmpty() && !putBackgroundPosition(parsedPosition,
                String.join(" ", position))) return;
        Map<String, String> parsedSize = new LinkedHashMap<>();
        if (!size.isEmpty() && !putBackgroundSize(parsedSize, String.join(" ", size))) return;

        target.put("background-color", color);
        target.put("background-image", image);
        target.put("background-repeat", repeat);
        target.put("background-position-x", "left");
        target.put("background-position-y", "top");
        target.put("background-position-x-offset", "0");
        target.put("background-position-y-offset", "0");
        target.put("background-size-x", "auto");
        target.put("background-size-y", "auto");
        target.putAll(parsedPosition);
        target.putAll(parsedSize);
    }

    private static int topLevelSlash(String value) {
        char quote = 0;
        int parentheses = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (quote != 0) {
                if (current == quote && (index == 0 || value.charAt(index - 1) != '\\')) quote = 0;
            } else if (current == '\'' || current == '"') quote = current;
            else if (current == '(') parentheses++;
            else if (current == ')') parentheses--;
            else if (current == '/' && parentheses == 0) return index;
        }
        return -1;
    }

    private static boolean isBackgroundRepeat(String value) {
        return value.equals("repeat") || value.equals("repeat-x")
                || value.equals("repeat-y") || value.equals("no-repeat");
    }

    private static boolean isBoxShadowValue(String value) {
        String normalized = value.strip();
        if (normalized.equalsIgnoreCase("none")) {
            return true;
        }
        for (String layer : splitTopLevel(normalized, ',')) {
            if (!isBoxShadowLayer(layer.strip())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBoxShadowLayer(String value) {
        if (value.isEmpty()) {
            return false;
        }
        boolean inset = false;
        int lengths = 0;
        int colors = 0;
        String[] tokens = value.split("\\s+");
        for (int index = 0; index < tokens.length; index++) {
            String token = tokens[index];
            if (token.isBlank()) {
                continue;
            }
            boolean last = index == tokens.length - 1;
            if (token.equalsIgnoreCase("inset") && !inset
                    && (index == 0 || last)) {
                inset = true;
            } else if (POSITIVE_LENGTH.matcher(token).matches() || "0".equals(token)
                    || isNegativeLength(token)) {
                lengths++;
                if (lengths > 4) {
                    return false;
                }
            } else if (isColorValue(token) || containsVarFunction(token)) {
                colors++;
                if (colors > 1 || lengths < 2) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return lengths >= 2;
    }

    private static boolean isNegativeLength(String token) {
        return token.matches("-?\\d+(\\.\\d+)?(?:px|em|rem|vw|vh)?");
    }

    public static java.util.List<String> splitTopLevel(String source, char separator) {
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

    private static boolean isBackgroundSizeToken(String value) {
        return value.equals("auto") || value.equals("cover") || value.equals("contain")
                || BACKGROUND_LENGTH.matcher(value).matches();
    }

    private static boolean containsVarFunction(String value) {
        return value.toLowerCase(Locale.ROOT).contains("var(");
    }

    private static void expandFlex(Map<String, String> target, String value) {
        String normalized = value.strip();
        if (normalized.equals("none")) {
            target.put("flex-grow", "0");
            target.put("flex-shrink", "0");
            target.put("flex-basis", "auto");
            return;
        }
        if (normalized.equals("auto")) normalized = "1 1 auto";
        else if (normalized.equals("initial") || normalized.equals("unset")
                || normalized.equals("inherit")) normalized = "0 1 auto";
        String[] tokens = normalized.split("\\s+");
        if (tokens.length == 1) {
            if (NON_NEGATIVE_NUMBER.matcher(tokens[0]).matches()) {
                target.put("flex-grow", tokens[0]);
                target.put("flex-shrink", "1");
                target.put("flex-basis", "0%");
                return;
            }
            if (tokens[0].equals("auto") || DIMENSION.matcher(tokens[0]).matches()) {
                target.put("flex-grow", "0");
                target.put("flex-shrink", "1");
                target.put("flex-basis", tokens[0]);
                return;
            }
            return;
        }
        if (tokens.length > 3 || !NON_NEGATIVE_NUMBER.matcher(tokens[0]).matches()) return;
        if (NON_NEGATIVE_NUMBER.matcher(tokens[1]).matches()) {
            String basis = tokens.length == 3 ? tokens[2] : "0%";
            if (!isFlexBasis(basis)) return;
            target.put("flex-grow", tokens[0]);
            target.put("flex-shrink", tokens[1]);
            target.put("flex-basis", basis);
            return;
        }
        if (tokens.length == 2 && (tokens[1].equals("auto")
                || isFlexBasis(tokens[1]))) {
            target.put("flex-grow", tokens[0]);
            target.put("flex-shrink", "1");
            target.put("flex-basis", tokens[1]);
        }
    }

    private static boolean isFlexBasis(String value) {
        return value.equals("auto") || value.equals("max-content")
                || value.equals("min-content") || value.equals("fit-content")
                || value.equals("content") || DIMENSION.matcher(value).matches();
    }

    private static void expandGap(Map<String, String> target, String value) {
        String[] tokens = value.strip().split("\\s+");
        if (tokens.length < 1 || tokens.length > 2
                || !POSITIVE_LENGTH.matcher(tokens[0]).matches()
                || tokens.length == 2 && !POSITIVE_LENGTH.matcher(tokens[1]).matches()) {
            return;
        }
        target.put("row-gap", tokens[0]);
        target.put("column-gap", tokens.length == 1 ? tokens[0] : tokens[1]);
    }

    private static final Pattern TIME_VALUE =
            Pattern.compile("[-+]?[0-9]*\\.?[0-9]+(ms|s)");
    private static final Pattern ITERATION_COUNT = Pattern.compile(
            "[0-9]*\\.?[0-9]+");

    private static boolean isTimingFunction(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.equals("ease") || normalized.equals("linear")
                || normalized.equals("ease-in") || normalized.equals("ease-out")
                || normalized.equals("ease-in-out") || normalized.equals("step-start")
                || normalized.equals("step-end")) {
            return true;
        }
        return normalized.startsWith("cubic-bezier(") && normalized.endsWith(")")
                || normalized.startsWith("steps(") && normalized.endsWith(")");
    }

    private static boolean isAnimationNameToken(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (TIME_VALUE.matcher(normalized).matches()
                || isTimingFunction(normalized) || ITERATION_COUNT.matcher(normalized).matches()
                || normalized.equals("infinite") || normalized.equals("normal")
                || normalized.equals("reverse") || normalized.equals("alternate")
                || normalized.equals("alternate-reverse") || normalized.equals("forwards")
                || normalized.equals("backwards") || normalized.equals("both")
                || normalized.equals("none") || normalized.equals("running")
                || normalized.equals("paused")) {
            return false;
        }
        return value.matches("[-_a-zA-Z][-_a-zA-Z0-9]*");
    }

    private static void putAnimationShorthand(Map<String, String> target, String value) {
        List<String> names = new ArrayList<>();
        List<String> durations = new ArrayList<>();
        List<String> timings = new ArrayList<>();
        List<String> delays = new ArrayList<>();
        List<String> iterations = new ArrayList<>();
        List<String> directions = new ArrayList<>();
        List<String> fillModes = new ArrayList<>();
        for (String layer : splitTopLevel(value, ',')) {
            String currentName = null;
            String duration = null;
            String timing = "ease";
            String delay = null;
            String iteration = null;
            String direction = null;
            String fillMode = null;
            for (String token : layer.strip().split("\\s+")) {
                if (token.isBlank()) {
                    continue;
                }
                String normalized = token.toLowerCase(Locale.ROOT);
                if (TIME_VALUE.matcher(normalized).matches()) {
                    if (duration == null && delay == null) {
                        duration = token;
                    } else if (delay == null) {
                        delay = token;
                    } else {
                        return;
                    }
                } else if (isTimingFunction(normalized)) {
                    if (timing.equals("ease") && duration != null) {
                        timing = token;
                    } else if (timing.equals("ease")) {
                        timing = token;
                    } else {
                        return;
                    }
                } else if (normalized.equals("infinite")) {
                    if (iteration == null) {
                        iteration = token;
                    } else {
                        return;
                    }
                } else if (ITERATION_COUNT.matcher(normalized).matches()
                        && !normalized.matches(".*[a-z].*")) {
                    if (iteration == null) {
                        iteration = token;
                    } else {
                        return;
                    }
                } else if (normalized.equals("forwards") || normalized.equals("backwards")
                        || normalized.equals("both")) {
                    if (fillMode == null) {
                        fillMode = token;
                    } else {
                        return;
                    }
                } else if (normalized.equals("reverse") || normalized.equals("alternate")
                        || normalized.equals("alternate-reverse")) {
                    if (direction == null) {
                        direction = token;
                    } else {
                        return;
                    }
                } else if (normalized.equals("none")) {
                    if (currentName == null) {
                        currentName = "none";
                    } else {
                        return;
                    }
                } else if (isAnimationNameToken(token)) {
                    if (currentName == null) {
                        currentName = token;
                    } else {
                        return;
                    }
                } else {
                    return;
                }
            }
            if (currentName == null) {
                return;
            }
            names.add(currentName);
            durations.add(duration == null ? "0s" : duration);
            timings.add(timing);
            delays.add(delay == null ? "0s" : delay);
            iterations.add(iteration == null ? "1" : iteration);
            directions.add(direction == null ? "normal" : direction);
            fillModes.add(fillMode == null ? "none" : fillMode);
        }
        target.put("animation-name", String.join(",", names));
        target.put("animation-duration", String.join(",", durations));
        target.put("animation-timing-function", String.join(",", timings));
        target.put("animation-delay", String.join(",", delays));
        target.put("animation-iteration-count", String.join(",", iterations));
        target.put("animation-direction", String.join(",", directions));
        target.put("animation-fill-mode", String.join(",", fillModes));
    }

    private static void putAnimationLonghand(Map<String, String> target,
                                             String property, String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (property.equals("animation-name")) {
            for (String name : splitTopLevel(value, ',')) {
                if (!name.strip().equals("none") && !isAnimationNameToken(name.strip())) {
                    return;
                }
            }
            target.put(property, value);
        } else if (property.equals("animation-duration")
                || property.equals("animation-delay")) {
            for (String part : splitTopLevel(value, ',')) {
                if (!TIME_VALUE.matcher(part.strip()).matches()) {
                    return;
                }
            }
            target.put(property, value);
        } else if (property.equals("animation-iteration-count")) {
            for (String part : splitTopLevel(value, ',')) {
                String token = part.strip().toLowerCase(Locale.ROOT);
                if (!token.equals("infinite") && !ITERATION_COUNT.matcher(token).matches()) {
                    return;
                }
            }
            target.put(property, value);
        } else if (property.equals("animation-timing-function")) {
            for (String part : splitTopLevel(value, ',')) {
                if (!isTimingFunction(part.strip())) {
                    return;
                }
            }
            target.put(property, value);
        } else {
            // direction/fill-mode: normale Werte reichen
            target.put(property, value);
        }
    }

    private static void putTransitionShorthand(Map<String, String> target, String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.equals("none")) {
            target.put("transition-property", "none");
            target.put("transition-duration", "0s");
            return;
        }
        List<String> properties = new ArrayList<>();
        List<String> durations = new ArrayList<>();
        List<String> timings = new ArrayList<>();
        List<String> delays = new ArrayList<>();
        for (String layer : splitTopLevel(value, ',')) {
            String property = null;
            String duration = null;
            String timing = "ease";
            String delay = null;
            for (String token : layer.strip().split("\\s+")) {
                if (token.isBlank()) {
                    continue;
                }
                String tokenLower = token.toLowerCase(Locale.ROOT);
                if (TIME_VALUE.matcher(tokenLower).matches() || tokenLower.equals("0")) {
                    if (duration == null && delay == null) {
                        duration = tokenLower.equals("0") ? "0s" : token;
                    } else if (delay == null) {
                        delay = tokenLower.equals("0") ? "0s" : token;
                    } else {
                        return;
                    }
                } else if (isTimingFunction(tokenLower)) {
                    if (timing.equals("ease")) {
                        timing = token;
                    } else {
                        return;
                    }
                } else if (property == null) {
                    property = token;
                } else {
                    return;
                }
            }
            if (property == null) {
                return;
            }
            properties.add(property);
            durations.add(duration == null ? "0s" : duration);
            timings.add(timing);
            delays.add(delay == null ? "0s" : delay);
        }
        target.put("transition-property", String.join(",", properties));
        target.put("transition-duration", String.join(",", durations));
        target.put("transition-timing-function", String.join(",", timings));
        target.put("transition-delay", String.join(",", delays));
    }

    private static void putTransitionLonghand(Map<String, String> target,
                                              String property, String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (property.equals("transition-duration")
                || property.equals("transition-delay")) {
            for (String part : splitTopLevel(value, ',')) {
                if (!TIME_VALUE.matcher(part.strip()).matches()) {
                    return;
                }
            }
            target.put(property, value);
        } else if (property.equals("transition-timing-function")) {
            for (String part : splitTopLevel(value, ',')) {
                if (!isTimingFunction(part.strip())) {
                    return;
                }
            }
            target.put(property, value);
        } else {
            target.put(property, value);
        }
    }

    private static boolean isListStyleType(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "disc", "circle", "square", "decimal", "decimal-leading-zero",
                 "lower-alpha", "upper-alpha", "lower-roman", "upper-roman", "none" -> true;
            default -> false;
        };
    }

    private static boolean isOverflowValue(String value) {
        String[] tokens = value.split("\\s+");
        if (tokens.length > 2) {
            return false;
        }
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (!token.equals("visible") && !token.equals("hidden") && !token.equals("scroll")
                    && !token.equals("auto") && !token.equals("clip")) {
                return false;
            }
        }
        return !value.isBlank();
    }

    private static boolean isObjectPosition(String value) {
        String[] tokens = value.split("\\s+");
        if (tokens.length < 1 || tokens.length > 4) {
            return false;
        }
        int keywords = 0;
        for (String token : tokens) {
            String normalized = token.toLowerCase(Locale.ROOT);
            if (normalized.equals("left") || normalized.equals("right")
                    || normalized.equals("top") || normalized.equals("bottom")
                    || normalized.equals("center")) {
                keywords++;
            } else if (!BACKGROUND_LENGTH.matcher(token).matches()) {
                return false;
            }
        }
        return keywords > 0 || tokens.length >= 1 && BACKGROUND_LENGTH.matcher(
                tokens[tokens.length - 1]).matches() && BACKGROUND_LENGTH.matcher(
                        tokens[0]).matches();
    }

    private static final Pattern GRID_TRACK = Pattern.compile(
            "auto|min-content|max-content|0|[0-9]*\\.?[0-9]+(px|em|rem|vw|vh|%|fr)"
                    + "|minmax\\([^)]*\\)|repeat\\([^)]*\\)|fit-content\\([^)]*\\)");

    private static boolean isGridTrackList(String value) {
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.equals("none")) {
            return true;
        }
        for (String rawToken : splitTopLevelWhitespace(normalized)) {
            String token = rawToken.replaceAll("^,|,$", "").strip();
            if (token.isEmpty()) {
                continue;
            }
            String lower = token.toLowerCase(Locale.ROOT);
            boolean basic = GRID_TRACK.matcher(lower).matches();
            boolean function = lower.startsWith("minmax(") || lower.startsWith("repeat(")
                    || lower.startsWith("fit-content(");
            if (!basic && !function) {
                return false;
            }
        }
        return true;
    }

    private static void putGridTrackList(Map<String, String> target, String property,
                                         String value) {
        if (isGridTrackList(value)) {
            target.put(property, value.replaceAll("\\s+", " ").strip());
        }
    }

    private static boolean isGridAreasValue(String value) {
        int columns = -1;
        int rowCount = 0;
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
                return false;
            }
            String row = value.substring(start + 1, end).strip();
            int count = row.isEmpty() ? 0 : row.split("\\s+").length;
            if (count == 0) {
                return false;
            }
            if (columns < 0) {
                columns = count;
            } else if (columns != count) {
                return false;
            }
            rowCount++;
            offset = end + 1;
        }
        return columns > 0 && rowCount <= 64;
    }

    private static void putGridTemplate(Map<String, String> target, String value) {
        String normalized = value.strip();
        if (normalized.equals("none")) {
            return;
        }
        int slash = topLevelSlash(normalized);
        String beforeSlash = slash < 0 ? normalized : normalized.substring(0, slash).strip();
        String afterSlash = slash < 0 ? "" : normalized.substring(slash + 1).strip();
        StringBuilder areas = new StringBuilder();
        StringBuilder rows = new StringBuilder();
        int offset = 0;
        while (offset < beforeSlash.length()) {
            int start = beforeSlash.indexOf('"', offset);
            char quote = '"';
            if (start < 0) {
                start = beforeSlash.indexOf('\'', offset);
                quote = '\'';
            }
            int end = start < 0 ? -1 : beforeSlash.indexOf(quote, start + 1);
            if (start >= 0 && end >= 0) {
                for (String token : beforeSlash.substring(offset, start).split("\\s+")) {
                    if (!token.isEmpty() && !isGridRowToken(token)) {
                        return;
                    }
                    if (!token.isEmpty()) {
                        rows.append(' ').append(token);
                    }
                }
                if (areas.length() > 0) {
                    areas.append(' ');
                }
                areas.append(beforeSlash, start, end + 1);
                offset = end + 1;
            } else {
                for (String token : beforeSlash.substring(offset).split("\\s+")) {
                    if (!token.isEmpty() && !isGridRowToken(token)) {
                        return;
                    }
                    if (!token.isEmpty()) {
                        rows.append(' ').append(token);
                    }
                }
                offset = beforeSlash.length();
            }
        }
        if (areas.isEmpty()) {
            return;
        }
        target.put("grid-template-areas", areas.toString().strip());
        if (!rows.isEmpty()) {
            target.put("grid-template-rows", rows.toString().strip());
        }
        if (!afterSlash.isEmpty()) {
            if (!isGridTrackList(afterSlash)) {
                return;
            }
            target.put("grid-template-columns", afterSlash.replaceAll("\\s+", " ").strip());
        }
    }

    private static boolean isGridRowToken(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return GRID_TRACK.matcher(lower).matches() || lower.startsWith("minmax(")
                || lower.startsWith("repeat(") || lower.startsWith("fit-content(");
    }

    private static void putGridArea(Map<String, String> target, String value) {
        String normalized = value.strip();
        if (normalized.contains("/")) {
            String[] parts = normalized.split("/");
            if (parts.length < 2 || parts.length > 4) {
                return;
            }
            for (String part : parts) {
                if (!isGridLineValue(part.strip())) {
                    return;
                }
            }
            target.put("grid-row-start", parts[0].strip());
            target.put("grid-column-start", parts[1].strip());
            if (parts.length >= 3) {
                target.put("grid-row-end", parts[2].strip());
            }
            if (parts.length == 4) {
                target.put("grid-column-end", parts[3].strip());
            }
            return;
        }
        String[] tokens = normalized.split("\\s+");
        if (tokens.length == 1) {
            if (tokens[0].matches("[-_a-zA-Z][-_a-zA-Z0-9]*")) {
                target.put("grid-area", tokens[0]);
            }
            return;
        }
        if (tokens.length != 4) {
            return;
        }
        for (String token : tokens) {
            if (!isGridLineValue(token)) {
                return;
            }
        }
        target.put("grid-row-start", tokens[0]);
        target.put("grid-column-start", tokens[1]);
        target.put("grid-row-end", tokens[2]);
        target.put("grid-column-end", tokens[3]);
    }

    private static void putGridLineShorthand(Map<String, String> target, String property,
                                             String value) {
        String[] parts = value.strip().split("/");
        if (parts.length < 1 || parts.length > 2) {
            return;
        }
        if (!isGridLineValue(parts[0].strip())) {
            return;
        }
        target.put(property + "-start", parts[0].strip());
        if (parts.length == 2) {
            if (!isGridLineValue(parts[1].strip())) {
                target.remove(property + "-start");
                return;
            }
            target.put(property + "-end", parts[1].strip());
        }
    }

    private static void putGridLine(Map<String, String> target, String property, String value) {
        if (isGridLineValue(value)) {
            target.put(property, value);
        }
    }

    private static boolean isGridLineValue(String value) {
        if (value.equals("auto")) {
            return true;
        }
        if (value.startsWith("span ")) {
            value = value.substring(5).strip();
        }
        return value.matches("[-+]?[0-9]+");
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

    private static boolean isFilterFunctionList(String value) {
        for (String part : splitTopLevel(value, ' ')) {
            String lower = part.strip().toLowerCase(Locale.ROOT);
            if (lower.startsWith("grayscale(") || lower.startsWith("brightness(")
                    || lower.startsWith("invert(") || lower.startsWith("contrast(")
                    || lower.startsWith("opacity(") || lower.startsWith("saturate(")
                    || lower.startsWith("sepia(") || lower.startsWith("blur(")
                    || lower.startsWith("hue-rotate(")) {
                if (!lower.endsWith(")")) {
                    return false;
                }
                continue;
            }
            return false;
        }
        return true;
    }

    private static final java.util.Set<String> ACCEPT_ANY_VALUES = java.util.Set.of(
            "align-left", "background-blend-mode", "break-after", "break-inside",
            "field-sizing", "forced-color-adjust", "hyphens", "interpolate-size",
            "line-break", "offset", "perspective", "scroll-behavior", "scrollbar-gutter",
            "stroke-linecap", "text-anchor", "text-size-adjust", "transform-style");

    private static boolean isAlignSelfValue(String value) {
        return value.equals("auto") || value.equals("stretch")
                || value.equals("flex-start") || value.equals("center")
                || value.equals("flex-end") || value.equals("baseline")
                || value.equals("start") || value.equals("end")
                || value.equals("self-start") || value.equals("self-end");
    }

    private static final java.util.Set<String> SYSTEM_COLORS = java.util.Set.of(
            "canvas", "canvastext", "linktext", "visitedtext", "activetext",
            "buttonface", "buttontext", "field", "fieldtext", "highlight",
            "highlighttext", "mark", "marktext", "graytext", "accentcolor",
            "accentcolortext", "selecteditem", "selecteditemtext");

    private static boolean isTextShadow(String value) {
        String[] tokens = value.strip().split("\\s+");
        if (tokens.length < 2 || tokens.length > 4) {
            return false;
        }
        for (String token : tokens) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (DIMENSION.matcher(lower).matches() || lower.equals("0")
                    || lower.matches("[-+]?[0-9]*\\.?[0-9]+(px|em|rem)")
                    || isColorValue(lower)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean isClipPathFunction(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).strip();
        if (normalized.equals("none")) {
            return true;
        }
        for (String name : List.of("inset", "circle", "ellipse", "polygon", "path", "url")) {
            if (normalized.startsWith(name + "(") && normalized.endsWith(")")) {
                return true;
            }
        }
        return false;
    }

    private static void putUnitInterval(Map<String, String> target, String property, String value) {
        try {
            float parsed = Float.parseFloat(value);
            if (Float.isFinite(parsed)) {
                target.put(property, parsed < 0 ? "0" : parsed > 1 ? "1" : value);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private static boolean putBackgroundPosition(Map<String, String> target, String value) {
        String[] tokens = value.strip().split("\\s+");
        if (tokens.length < 1 || tokens.length > 4) return false;
        String x = null;
        String y = null;
        String xOffset = "0";
        String yOffset = "0";
        List<String> numeric = new ArrayList<>();
        for (int index = 0; index < tokens.length; index++) {
            String token = tokens[index];
            boolean hasOffset = index + 1 < tokens.length
                    && BACKGROUND_LENGTH.matcher(tokens[index + 1]).matches();
            if (token.equals("left") || token.equals("right")) {
                if (x != null) return false;
                x = token;
                if (hasOffset) xOffset = tokens[++index];
            } else if (token.equals("top") || token.equals("bottom")) {
                if (y != null) return false;
                y = token;
                if (hasOffset) yOffset = tokens[++index];
            } else if (token.equals("center")) {
                String next = index + 1 < tokens.length ? tokens[index + 1] : "";
                if (x == null && y == null
                        && (next.equals("left") || next.equals("right"))) {
                    y = "center";
                } else if (x == null) {
                    x = "center";
                } else if (y == null) y = "center";
                else return false;
            } else if (BACKGROUND_LENGTH.matcher(token).matches()) {
                numeric.add(token);
            } else {
                return false;
            }
        }
        for (String token : numeric) {
            if (x == null) {
                x = "left";
                xOffset = token;
            } else if (y == null) {
                y = "top";
                yOffset = token;
            } else return false;
        }
        target.put("background-position-x", x == null ? "center" : x);
        target.put("background-position-y", y == null ? "center" : y);
        target.put("background-position-x-offset", xOffset);
        target.put("background-position-y-offset", yOffset);
        return true;
    }

    private static boolean putBackgroundSize(Map<String, String> target, String value) {
        String[] tokens = value.strip().split("\\s+");
        if (tokens.length < 1 || tokens.length > 2
                || !isBackgroundSizeToken(tokens[0])
                || tokens.length == 2 && !isBackgroundSizeToken(tokens[1])) return false;
        target.put("background-size-x", tokens[0]);
        target.put("background-size-y", tokens.length == 1 ? "auto" : tokens[1]);
        return true;
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
            if (!isColorValue(entry)) {
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
            } else if ((token.equals("none") || token.equals("solid")
                    || token.equals("dotted") || token.equals("dashed")
                    || token.equals("double")) && style == null) {
                style = token;
            } else if ((isColorValue(token) || containsVarFunction(token)) && color == null) {
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

    private static void expandOutline(Map<String, String> target, String value) {
        String width = null;
        String style = null;
        String color = null;
        for (String token : value.split("\\s+")) {
            if (POSITIVE_LENGTH.matcher(token).matches() && width == null) {
                width = token;
            } else if ((token.equals("none") || token.equals("solid")
                    || token.equals("dotted") || token.equals("dashed")
                    || token.equals("double")) && style == null) {
                style = token;
            } else if ((isColorValue(token) || SYSTEM_COLORS.contains(token)
                    || containsVarFunction(token)) && color == null) {
                color = token;
            } else {
                return;
            }
        }
        if (width == null && style == null && color == null) return;
        target.put("outline-width", width == null ? "1px" : width);
        target.put("outline-style", style == null ? "none" : style);
        if (color != null) target.put("outline-color", color);
    }

    private static void expandTextDecoration(Map<String, String> target, String value) {
        String line = null;
        String color = null;
        for (String token : value.split("\\s+")) {
            if ((token.equals("none") || token.equals("underline")
                    || token.equals("line-through") || token.equals("overline")) && line == null) {
                line = token;
            } else if (isColorValue(token) && color == null) {
                color = token;
            } else {
                return;
            }
        }
        if (line != null) target.put("text-decoration-line", line);
        if (color != null) target.put("text-decoration-color", color);
    }

    private static String listStyleType(String value) {
        for (String token : value.split("\\s+")) {
            if (isListStyleType(token)) return token;
        }
        return null;
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
        if (isColorValue(value)) {
            target.put(property, value);
        }
    }

    private static boolean isColorValue(String value) {
        return CssColor.isSupported(value)
                || "currentcolor".equals(value.toLowerCase(Locale.ROOT));
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

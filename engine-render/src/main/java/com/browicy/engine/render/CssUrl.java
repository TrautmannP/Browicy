package com.browicy.engine.render;

import java.net.URI;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class CssUrl {

    private CssUrl() {
    }

    public static String parseSingle(String value) {
        if (value == null) return null;
        int[] cursor = {0};
        skipWhitespace(value, cursor);
        if (!startsWithIgnoreCase(value, cursor[0], "url")) return null;
        cursor[0] += 3;
        skipWhitespace(value, cursor);
        if (cursor[0] >= value.length() || value.charAt(cursor[0]++) != '(') return null;
        skipWhitespace(value, cursor);
        String source = readValue(value, cursor);
        if (source == null) return null;
        skipWhitespace(value, cursor);
        if (cursor[0] >= value.length() || value.charAt(cursor[0]++) != ')') return null;
        skipWhitespace(value, cursor);
        if (cursor[0] != value.length() || !hasSafeScheme(source)) return null;
        return source;
    }

    public static String rewrite(String css, Function<String, String> rewriter) {
        StringBuilder result = new StringBuilder(css.length());
        int start = 0;
        int i = 0;
        while (i < css.length()) {
            char current = css.charAt(i);
            if (current == '/' && i + 1 < css.length() && css.charAt(i + 1) == '*') {
                i = skipComment(css, i + 2);
                continue;
            }
            if (current == '\'' || current == '"') {
                i = skipQuoted(css, i, current);
                continue;
            }
            if (startsWithIgnoreCase(css, i, "url")
                    && (i == 0 || !isIdentifier(css.charAt(i - 1)))) {
                int end = tokenEnd(css, i);
                if (end > i) {
                    String token = css.substring(i, end);
                    String source = parseSingle(token);
                    if (source != null) {
                        String replacement = rewriter.apply(source);
                        if (replacement != null) {
                            result.append(css, start, i).append("url(\"")
                                    .append(escape(replacement)).append("\")");
                            start = end;
                        }
                    }
                    i = end;
                    continue;
                }
            }
            i++;
        }
        return result.append(css, start, css.length()).toString();
    }

    public static List<Token> tokens(String css) {
        List<Token> result = new ArrayList<>();
        int i = 0;
        while (i < css.length()) {
            char current = css.charAt(i);
            if (current == '/' && i + 1 < css.length() && css.charAt(i + 1) == '*') {
                i = skipComment(css, i + 2);
                continue;
            }
            if (current == '\'' || current == '"') {
                i = skipQuoted(css, i, current);
                continue;
            }
            if (startsWithIgnoreCase(css, i, "url")
                    && (i == 0 || !isIdentifier(css.charAt(i - 1)))) {
                int end = tokenEnd(css, i);
                if (end > i) {
                    String source = parseSingle(css.substring(i, end));
                    if (source != null) result.add(new Token(source, i, end));
                    i = end;
                    continue;
                }
            }
            i++;
        }
        return List.copyOf(result);
    }

    public record Token(String source, int start, int end) {
    }

    private static String readValue(String text, int[] cursor) {
        if (cursor[0] >= text.length()) return null;
        char quote = text.charAt(cursor[0]);
        StringBuilder value = new StringBuilder();
        if (quote == '\'' || quote == '"') {
            cursor[0]++;
            while (cursor[0] < text.length()) {
                char c = text.charAt(cursor[0]++);
                if (c == quote) return value.toString();
                if (c == '\\' && cursor[0] < text.length()) c = text.charAt(cursor[0]++);
                if (c == '\r' || c == '\n') return null;
                value.append(c);
            }
            return null;
        }
        while (cursor[0] < text.length()) {
            char c = text.charAt(cursor[0]);
            if (c == ')') break;
            if (Character.isWhitespace(c) || c == '\'' || c == '"' || c == '(' || c == '\\') {
                return null;
            }
            value.append(c);
            cursor[0]++;
        }
        return value.toString();
    }

    private static boolean hasSafeScheme(String source) {
        if (source.isBlank()) return false;
        try {
            URI uri = URI.create(source);
            if (!uri.isAbsolute()) return true;
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            return scheme.equals("http") || scheme.equals("https");
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static int tokenEnd(String text, int start) {
        int i = start + 3;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        if (i >= text.length() || text.charAt(i++) != '(') return -1;
        char quote = 0;
        for (; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') quote = c;
            else if (c == ')') return i + 1;
        }
        return -1;
    }

    private static int skipQuoted(String text, int start, char quote) {
        int i = start + 1;
        while (i < text.length()) {
            char c = text.charAt(i++);
            if (c == '\\' && i < text.length()) i++;
            else if (c == quote) break;
        }
        return i;
    }

    private static int skipComment(String text, int start) {
        int end = text.indexOf("*/", start);
        return end < 0 ? text.length() : end + 2;
    }

    private static boolean startsWithIgnoreCase(String text, int start, String expected) {
        return start + expected.length() <= text.length()
                && text.regionMatches(true, start, expected, 0, expected.length());
    }

    private static boolean isIdentifier(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }

    private static void skipWhitespace(String text, int[] cursor) {
        while (cursor[0] < text.length() && Character.isWhitespace(text.charAt(cursor[0]))) {
            cursor[0]++;
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

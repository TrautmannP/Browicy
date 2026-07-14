package com.browicy.engine.css;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A deliberately small, deterministic subset of CSS media queries used by page layout. */
public record MediaCondition(String source) {

    private static final Pattern DIMENSION = Pattern.compile(
            "\\(\\s*(min|max)-(width|height)\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)px\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    public static final MediaCondition ALL = new MediaCondition("all");

    public MediaCondition {
        source = source == null || source.isBlank() ? "all" : source.strip();
    }

    public boolean matches(float viewportWidth, float viewportHeight) {
        for (String alternative : splitTopLevel(source, ',')) {
            if (matchesAlternative(alternative.strip(), viewportWidth, viewportHeight)) {
                return true;
            }
        }
        return false;
    }

    public MediaCondition and(MediaCondition other) {
        if (this.equals(ALL)) return other;
        if (other.equals(ALL)) return this;
        return new MediaCondition("(" + source + ") and (" + other.source + ")");
    }

    private static boolean matchesAlternative(String query, float width, float height) {
        String normalized = query.toLowerCase(Locale.ROOT).strip();
        if (normalized.isEmpty() || normalized.equals("all") || normalized.equals("screen")) {
            return true;
        }
        if (normalized.startsWith("only ")) normalized = normalized.substring(5).strip();
        if (normalized.startsWith("screen and ")) normalized = normalized.substring(11).strip();
        if (normalized.startsWith("all and ")) normalized = normalized.substring(8).strip();
        if (normalized.startsWith("not ")) return false;

        Matcher matcher = DIMENSION.matcher(normalized);
        int end = 0;
        boolean found = false;
        while (matcher.find()) {
            String between = normalized.substring(end, matcher.start()).strip();
            if (!between.isEmpty() && !between.equals("and")
                    && !between.equals("(") && !between.equals(") and (")) return false;
            float actual = matcher.group(2).equals("width") ? width : height;
            float expected = Float.parseFloat(matcher.group(3));
            if (matcher.group(1).equals("min") ? actual < expected : actual > expected) {
                return false;
            }
            found = true;
            end = matcher.end();
        }
        String tail = normalized.substring(end).strip();
        return found && (tail.isEmpty() || tail.equals(")"));
    }

    private static List<String> splitTopLevel(String value, char separator) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '(') depth++;
            else if (current == ')') depth = Math.max(0, depth - 1);
            else if (current == separator && depth == 0) {
                parts.add(value.substring(start, index));
                start = index + 1;
            }
        }
        parts.add(value.substring(start));
        return parts;
    }
}

package com.browicy.conformance.comparator;

import com.browicy.conformance.model.ElementLayoutBox;
import com.browicy.conformance.model.LayoutDiff;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compares Chromium geometry and selected computed styles with Browicy output. */
public final class LayoutComparator {
    public static final float DEFAULT_TOLERANCE_PX = 1.0f;
    private static final List<String> STYLE_PROPERTIES = List.of(
            "display", "position", "margin", "padding", "border-width", "font-size", "box-sizing");
    private static final Pattern NUMBER = Pattern.compile("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)");

    public List<LayoutDiff> compare(Map<String, ElementLayoutBox> expectedChrome,
                                    Map<String, ElementLayoutBox> actualBrowicy,
                                    float tolerancePx) {
        Objects.requireNonNull(expectedChrome, "expectedChrome");
        Objects.requireNonNull(actualBrowicy, "actualBrowicy");
        if (!Float.isFinite(tolerancePx) || tolerancePx < 0) {
            throw new IllegalArgumentException("tolerancePx must be finite and non-negative");
        }

        List<LayoutDiff> diffs = new ArrayList<>();
        for (Map.Entry<String, ElementLayoutBox> entry : expectedChrome.entrySet()) {
            String selector = entry.getKey();
            ElementLayoutBox expected = entry.getValue();
            ElementLayoutBox actual = actualBrowicy.get(selector);
            if (actual == null) {
                diffs.add(new LayoutDiff(selector, "missing", expected.x(), Float.NaN,
                        Float.NaN, expected.tagName(), "missing"));
                continue;
            }
            compareGeometry(expected, actual, tolerancePx, diffs);
            compareStyles(expected, actual, tolerancePx, diffs);
        }

        Set<String> unexpected = new TreeSet<>(actualBrowicy.keySet());
        unexpected.removeAll(expectedChrome.keySet());
        for (String selector : unexpected) {
            ElementLayoutBox actual = actualBrowicy.get(selector);
            diffs.add(new LayoutDiff(selector, "unexpected", Float.NaN, actual.x(),
                    Float.NaN, "missing", actual.tagName()));
        }
        return List.copyOf(diffs);
    }

    public static String formatDiffs(List<LayoutDiff> diffs) {
        Objects.requireNonNull(diffs, "diffs");
        if (diffs.isEmpty()) return "No layout differences";
        return diffs.stream().map(LayoutDiff::format).reduce(
                (first, second) -> first + System.lineSeparator() + second).orElse("");
    }

    private static void compareGeometry(ElementLayoutBox expected,
                                         ElementLayoutBox actual,
                                         float tolerancePx,
                                         List<LayoutDiff> diffs) {
        compareNumber(expected.selector(), "x", expected.x(), actual.x(), tolerancePx, diffs);
        compareNumber(expected.selector(), "y", expected.y(), actual.y(), tolerancePx, diffs);
        compareNumber(expected.selector(), "width", expected.width(), actual.width(), tolerancePx, diffs);
        compareNumber(expected.selector(), "height", expected.height(), actual.height(), tolerancePx, diffs);
    }

    private static void compareStyles(ElementLayoutBox expected,
                                      ElementLayoutBox actual,
                                      float tolerancePx,
                                      List<LayoutDiff> diffs) {
        for (String property : STYLE_PROPERTIES) {
            String expectedValue = expected.style(property);
            String actualValue = actual.style(property);
            if (expectedValue == null && actualValue == null) continue;
            if (Objects.equals(normalize(expectedValue), normalize(actualValue))) continue;
            if (expectedValue != null && actualValue != null
                    && numericValuesEquivalent(expectedValue, actualValue, tolerancePx)) {
                continue;
            }
            diffs.add(LayoutDiff.style(expected.selector(), property,
                    String.valueOf(expectedValue), String.valueOf(actualValue)));
        }
    }

    private static void compareNumber(String selector, String property,
                                      float expected, float actual, float tolerancePx,
                                      List<LayoutDiff> diffs) {
        float delta = Math.abs(expected - actual);
        if (!Float.isFinite(expected) || !Float.isFinite(actual) || delta > tolerancePx) {
            diffs.add(new LayoutDiff(selector, property, expected, actual, delta));
        }
    }

    private static boolean numericValuesEquivalent(String expected, String actual, float tolerancePx) {
        float[] left = numbers(expected);
        float[] right = numbers(actual);
        if (left.length == 0 || left.length != right.length) return false;
        for (int index = 0; index < left.length; index++) {
            if (Math.abs(left[index] - right[index]) > tolerancePx) return false;
        }
        return true;
    }

    private static float[] numbers(String value) {
        Matcher matcher = NUMBER.matcher(value);
        float[] result = new float[8];
        int count = 0;
        while (matcher.find()) {
            if (count == result.length) result = Arrays.copyOf(result, result.length * 2);
            result[count++] = Float.parseFloat(matcher.group());
        }
        return Arrays.copyOf(result, count);
    }

    private static String normalize(String value) {
        return value == null ? null : value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}

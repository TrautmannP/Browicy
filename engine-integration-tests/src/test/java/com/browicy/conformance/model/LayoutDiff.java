package com.browicy.conformance.model;

import java.util.Objects;

/** One geometry or computed-style mismatch between Chromium and Browicy. */
public record LayoutDiff(
        String selector,
        String property,
        float expected,
        float actual,
        float delta,
        String expectedValue,
        String actualValue) {

    public LayoutDiff(String selector, String property, float expected, float actual, float delta) {
        this(selector, property, expected, actual, delta, null, null);
    }

    public LayoutDiff {
        selector = requireText(selector, "selector");
        property = requireText(property, "property");
        if (expectedValue != null && actualValue == null
                || expectedValue == null && actualValue != null) {
            throw new IllegalArgumentException("expectedValue and actualValue must be provided together");
        }
    }

    public static LayoutDiff style(String selector, String property,
                                   String expected, String actual) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(actual, "actual");
        float expectedNumber = parseNumber(expected);
        float actualNumber = parseNumber(actual);
        float difference = Float.isFinite(expectedNumber) && Float.isFinite(actualNumber)
                ? Math.abs(expectedNumber - actualNumber) : 1f;
        return new LayoutDiff(selector, "style:" + property,
                expectedNumber, actualNumber, difference, expected, actual);
    }

    public boolean isMissingElement() {
        return property.equals("missing");
    }

    public String format() {
        if (isMissingElement()) {
            return selector + ": element is missing in Browicy";
        }
        if (expectedValue != null) {
            return selector + " " + property + ": expected " + expectedValue
                    + ", actual " + actualValue + " (delta=" + formatNumber(delta) + ")";
        }
        return selector + " " + property + ": expected " + formatNumber(expected)
                + ", actual " + formatNumber(actual) + " (delta=" + formatNumber(delta) + ")";
    }

    @Override
    public String toString() {
        return format();
    }

    private static float parseNumber(String value) {
        try {
            return Float.parseFloat(value.replace("px", "").strip());
        } catch (NumberFormatException ignored) {
            return Float.NaN;
        }
    }

    private static String formatNumber(float value) {
        return Float.isFinite(value) ? Float.toString(value) : "n/a";
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

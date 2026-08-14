package com.browicy.conformance.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Immutable result and metadata for one Chrome/Browicy comparison. */
public final class LayoutComparisonReport {
    private final String testName;
    private final String url;
    private final int viewportWidth;
    private final int viewportHeight;
    private final Instant timestamp;
    private final List<LayoutDiff> diffs;
    private final boolean passed;

    public LayoutComparisonReport(String testName, String url,
                                  int viewportWidth, int viewportHeight,
                                  Instant timestamp, List<LayoutDiff> diffs) {
        this.testName = requireText(testName, "testName");
        this.url = requireText(url, "url");
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            throw new IllegalArgumentException("viewport dimensions must be positive");
        }
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.diffs = List.copyOf(Objects.requireNonNull(diffs, "diffs"));
        this.passed = this.diffs.isEmpty();
    }

    public String testName() {
        return testName;
    }

    public String url() {
        return url;
    }

    public int viewportWidth() {
        return viewportWidth;
    }

    public int viewportHeight() {
        return viewportHeight;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public List<LayoutDiff> diffs() {
        return diffs;
    }

    public boolean passed() {
        return passed;
    }

    public String format() {
        String header = testName + " [" + viewportWidth + "x" + viewportHeight + "] "
                + (passed ? "PASSED" : "FAILED") + " (" + diffs.size() + " diffs)";
        if (diffs.isEmpty()) {
            return header;
        }
        return header + System.lineSeparator()
                + diffs.stream().map(LayoutDiff::format).collect(Collectors.joining(System.lineSeparator()));
    }

    @Override
    public String toString() {
        return format();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

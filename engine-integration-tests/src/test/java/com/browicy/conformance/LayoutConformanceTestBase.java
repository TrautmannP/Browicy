package com.browicy.conformance;

import com.browicy.conformance.comparator.LayoutComparator;
import com.browicy.conformance.extractor.BrowicyLayoutExtractor;
import com.browicy.conformance.extractor.ChromeLayoutExtractor;
import com.browicy.conformance.model.ElementLayoutBox;
import com.browicy.conformance.model.LayoutComparisonReport;
import com.browicy.conformance.model.LayoutDiff;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;

/** Shared lifecycle and assertion helpers for Chromium-backed conformance tests. */
public abstract class LayoutConformanceTestBase {
    protected static ChromeLayoutExtractor chrome;
    protected static final BrowicyLayoutExtractor browicy = new BrowicyLayoutExtractor();
    protected static final LayoutComparator comparator = new LayoutComparator();

    @BeforeClass
    public static void startChromium() {
        chrome = new ChromeLayoutExtractor();
    }

    @AfterClass
    public static void stopChromium() {
        if (chrome != null) {
            chrome.close();
            chrome = null;
        }
    }

    protected final LayoutComparisonReport compare(String testName, String html,
                                                   int viewportWidth, int viewportHeight,
                                                   float tolerancePx) {
        Map<String, ElementLayoutBox> expected =
                chrome.extract(html, viewportWidth, viewportHeight);
        Map<String, ElementLayoutBox> actual =
                browicy.extract(html, viewportWidth, viewportHeight);
        List<LayoutDiff> diffs = comparator.compare(expected, actual, tolerancePx);
        LayoutComparisonReport report = new LayoutComparisonReport(
                testName, "about:blank", viewportWidth, viewportHeight, Instant.now(), diffs);
        System.out.println(report.format());
        return report;
    }

    protected final void assertConforms(String testName, String html,
                                        int viewportWidth, int viewportHeight,
                                        float tolerancePx) {
        LayoutComparisonReport report = compare(testName, html,
                viewportWidth, viewportHeight, tolerancePx);
        Assert.assertTrue(report.format(), report.passed());
    }
}

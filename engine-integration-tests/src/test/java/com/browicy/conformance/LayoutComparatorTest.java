package com.browicy.conformance;

import com.browicy.conformance.comparator.LayoutComparator;
import com.browicy.conformance.model.ElementLayoutBox;
import com.browicy.conformance.model.LayoutDiff;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LayoutComparatorTest {
    @Test
    public void appliesToleranceAndReportsMissingElements() {
        Map<String, ElementLayoutBox> chrome = new LinkedHashMap<>();
        chrome.put("#main", box("#main", 10, 20, 100, 50, "block"));
        chrome.put("#missing", box("#missing", 0, 0, 1, 1, "block"));
        Map<String, ElementLayoutBox> browicy = Map.of(
                "#main", box("#main", 10.5f, 20, 100, 50, "block"));

        List<LayoutDiff> diffs = new LayoutComparator().compare(chrome, browicy, 1.0f);

        assertEquals(1, diffs.size());
        assertTrue(diffs.getFirst().isMissingElement());
    }

    private static ElementLayoutBox box(String selector, float x, float y,
                                         float width, float height, String display) {
        return new ElementLayoutBox(selector, "div", x, y, width, height,
                Map.of("display", display, "position", "static", "margin", "0px",
                        "padding", "0px", "border-width", "0px", "font-size", "16px",
                        "box-sizing", "content-box"));
    }
}

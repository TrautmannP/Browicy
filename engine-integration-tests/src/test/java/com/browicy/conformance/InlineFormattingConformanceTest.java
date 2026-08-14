package com.browicy.conformance;

import org.junit.Test;

public final class InlineFormattingConformanceTest extends LayoutConformanceTestBase {
    @Test
    public void matchesTextRunsAndNestedInlineBoxes() {
        String html = """
                <!doctype html>
                <html><head><style>
                  html, body { margin: 0; padding: 0; }
                  #inline { width: 320px; margin: 0; font-size: 16px; line-height: 20px; }
                  #inline > span { display: inline-block; width: 160px; height: 20px;
                                   color: #c00; vertical-align: top; }
                  #inline strong { display: inline-block; width: 50px; height: 20px;
                                  font-weight: 700; }
                </style></head><body>
                  <p id="inline">A <span><strong>inline</strong> nested</span> content</p>
                </body></html>
                """;
        assertConforms("inline-formatting", html, 800, 600, 2.0f);
    }
}

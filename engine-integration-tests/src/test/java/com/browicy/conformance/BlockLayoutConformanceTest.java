package com.browicy.conformance;

import org.junit.Test;

public final class BlockLayoutConformanceTest extends LayoutConformanceTestBase {
    @Test
    public void matchesNestedBlocksAndBoxSizing() {
        String html = """
                <!doctype html>
                <html><head><style>
                  html, body { margin: 0; padding: 0; }
                  body { font-size: 16px; line-height: 20px; }
                  #main { width: 640px; padding: 20px; border: 2px solid #222;
                          box-sizing: border-box; }
                  #main > div { margin: 10px 0 12px; padding: 8px 12px;
                                 border: 1px solid #777; }
                  #main > div > p { margin: 0; padding: 4px; }
                  #content-box { width: 100px; padding: 10px; border: 2px solid #444;
                                 box-sizing: content-box; }
                </style></head><body><main id="main">
                  <div><p>Nested block</p></div>
                  <div id="content-box">Content box</div>
                </main></body></html>
                """;
        assertConforms("block-layout", html, 800, 600, 1.5f);
    }
}

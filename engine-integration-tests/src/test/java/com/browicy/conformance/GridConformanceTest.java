package com.browicy.conformance;

import org.junit.Test;

public final class GridConformanceTest extends LayoutConformanceTestBase {
    @Test
    public void matchesFractionalAndFixedTracks() {
        String html = """
                <!doctype html>
                <html><head><style>
                  html, body { margin: 0; padding: 0; }
                  #grid > div { padding: 4px; border: 1px solid #555; }
                </style></head><body>
                  <div id="grid" style="display:grid;width:500px;
                       grid-template-columns:1fr 120px;grid-template-rows:40px auto;gap:10px">
                    <div style="height:40px">A</div><div style="height:40px">B</div>
                    <div style="height:30px">C</div><div style="height:30px">D</div>
                  </div>
                </body></html>
                """;
        assertConforms("grid-layout", html, 800, 600, 1.5f);
    }
}

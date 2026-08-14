package com.browicy.conformance;

import org.junit.Test;

public final class FlexboxConformanceTest extends LayoutConformanceTestBase {
    @Test
    public void matchesRowsColumnsGapsAndFlexGrowth() {
        String html = """
                <!doctype html>
                <html><head><style>
                  html, body { margin: 0; padding: 0; }
                  #row { display: flex; width: 600px; height: 80px; gap: 10px;
                         justify-content: space-between; align-items: center; }
                  #row > .grow { flex-grow: 1; }
                  #row > div { width: 80px; height: 20px; }
                  #column { display: flex; flex-direction: column; width: 220px;
                            height: 180px; gap: 8px; align-items: stretch; }
                  #column > div { flex: 1 1 auto; }
                </style></head><body>
                  <div id="row"><div>A</div><div class="grow">B</div><div>C</div></div>
                  <div id="column"><div>One</div><div>Two</div><div>Three</div></div>
                </body></html>
                """;
        assertConforms("flexbox-layout", html, 800, 600, 1.5f);
    }
}

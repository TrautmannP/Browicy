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

    @Test
    public void matchesNamedLinesAndOverlappingAreas() {
        // Muster der sparkasse.de-Hero-Sektion: minmax(100%, ...) füllt die
        // mittlere Spur, benannte Bereiche platzieren Items überlappend.
        String html = """
                <!doctype html>
                <html><head><style>
                  html, body { margin: 0; padding: 0; }
                  #grid { display: grid; width: 600px;
                          grid-template-columns: 1fr minmax(100%, 1600px) 1fr;
                          grid-template-rows: 100px;
                          grid-template-areas: 'mediaLeft content mediaRight'; }
                  #media { grid-area: 1 / mediaLeft / auto / mediaRight;
                           background: #eee; }
                  #content { grid-area: content; background: #fff; }
                </style></head><body>
                  <div id="grid">
                    <div id="media">M</div>
                    <div id="content">C</div>
                  </div>
                </body></html>
                """;
        assertConforms("grid-named-lines", html, 800, 600, 1.5f);
    }

    @Test
    public void matchesContentsChildAsGridItem() {
        // display:contents erzeugt keine Box; seine Kinder werden direkt
        // zu Grid-Items und Margins des contents-Elements werden ignoriert.
        String html = """
                <!doctype html>
                <html><head><style>
                  html, body { margin: 0; padding: 0; }
                  #grid { display: grid; width: 400px; grid-template-rows: 40px;
                          grid-template-columns: 1fr 1fr; }
                  #a { grid-area: 1 / 1; background: #f55; }
                  #b { grid-area: 1 / 2; background: #55f; }
                </style></head><body>
                  <div id="grid">
                    <div id="a">A</div>
                    <div style="display: contents; margin: 20px">
                      <div id="b">B</div>
                    </div>
                  </div>
                </body></html>
                """;
        assertConforms("grid-contents-item", html, 800, 600, 1.5f);
    }
}

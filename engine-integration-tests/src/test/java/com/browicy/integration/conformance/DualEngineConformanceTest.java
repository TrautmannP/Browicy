package com.browicy.integration.conformance;

import com.browicy.conformance.LayoutConformanceTestBase;
import org.junit.Test;

/**
 * Dual-Engine-Conformance: dieselben HTML/CSS-Snippets laufen in Browicy und
 * in Headless-Chromium (Playwright); die Bounding Boxes kritischer Elemente
 * müssen innerhalb der Font-/Rundungs-Toleranz übereinstimmen.
 *
 * <p>Läuft über die Namenskonvention (Paket {@code conformance}): standardmäßig
 * ausgeschlossen, aktiv über {@code mvn verify -Pconformance}.</p>
 */
public final class DualEngineConformanceTest extends LayoutConformanceTestBase {

    @Test
    public void absoluteBoxWithPaddingAndBorderMatchesChromium() {
        String html = """
                <!doctype html><html><head><style>
                  html, body { margin: 0; padding: 0; }
                  #box { position: absolute; left: 25px; top: 30px; width: 120px; height: 60px;
                         padding: 5px 10px; border: 2px solid #222; }
                </style></head><body><div id="box">Inhalt</div></body></html>
                """;
        assertConforms("dual-engine-absolute-box", html, 800, 600, 1.5f);
    }

    @Test
    public void offsetAndClientBoxesMatchChromium() {
        String html = """
                <!doctype html><html><head><style>
                  html, body { margin: 0; padding: 0; }
                  #box { position: relative; left: 10px; top: 8px; width: 140px; height: 70px;
                         padding: 6px 12px; border: 3px solid #444; }
                </style></head><body><div id="box">Box</div></body></html>
                """;
        // Border-Box: 140 + 24 + 6 = 170 breit, 70 + 12 + 6 = 88 hoch.
        assertConforms("dual-engine-border-box", html, 800, 600, 1.5f);
    }

    @Test
    public void siblingAndParentChildMarginCollapsingMatchChromium() {
        String html = """
                <!doctype html><html><head><style>
                  html, body { margin: 0; padding: 0; }
                  p, #parent, #child { font-size: 16px; line-height: 20px; margin: 0; }
                  #a { margin-bottom: 20px; }
                  #b { margin-top: 30px; }
                  #parent { margin-top: 15px; }
                  #child { margin-top: 25px; margin-bottom: 10px; }
                </style></head><body>
                  <p id="a">Erster</p>
                  <p id="b">Zweiter</p>
                  <div id="parent"><div id="child">Kind</div></div>
                </body></html>
                """;
        assertConforms("dual-engine-margin-collapse", html, 800, 600, 1.5f);
    }

    @Test
    public void calcAndClampMatchChromium() {
        String html = """
                <!doctype html><html><head><style>
                  html, body { margin: 0; padding: 0; }
                  #calc { width: calc(100% - 50px); height: 20px; }
                  #clamp { width: 100px; height: clamp(100px, 50vh, 500px); }
                </style></head><body>
                  <div id="calc">calc</div>
                  <div id="clamp">clamp</div>
                </body></html>
                """;
        assertConforms("dual-engine-math-functions", html, 800, 600, 1.5f);
    }

    @Test
    public void flexWrapWithGrowMatchesChromium() {
        String html = """
                <!doctype html><html><head><style>
                  html, body { margin: 0; padding: 0; }
                  #flex { display: flex; flex-wrap: wrap; width: 450px; }
                  .item { flex: 1 1 150px; height: 20px; }
                </style></head><body>
                  <div id="flex">
                    <div id="i1" class="item">eins</div>
                    <div id="i2" class="item">zwei</div>
                    <div id="i3" class="item">drei</div>
                    <div id="i4" class="item">vier</div>
                  </div>
                </body></html>
                """;
        assertConforms("dual-engine-flex-wrap", html, 800, 600, 1.5f);
    }

    @Test
    public void baselineAlignmentKeepsTextBaselinesAligned() {
        String html = """
                <!doctype html><html><head><style>
                  html, body { margin: 0; padding: 0; }
                  #flex { display: flex; align-items: baseline; }
                  #big { font-size: 30px; line-height: 34px; flex: 0 0 120px; }
                  #small { font-size: 12px; line-height: 14px; flex: 0 0 80px; }
                </style></head><body>
                  <div id="flex">
                    <div id="big">Groß</div>
                    <div id="small">Klein</div>
                  </div>
                </body></html>
                """;
        // Die Baselines müssen in beiden Engines auf derselben Y-Koordinate liegen;
        // Ascent/Descent der Fonts differieren zwischen Chromium und AWT minimal,
        // daher eine großzügige Toleranz für die Box-Positionen.
        assertConforms("dual-engine-baseline", html, 800, 600, 3.0f);
    }
}

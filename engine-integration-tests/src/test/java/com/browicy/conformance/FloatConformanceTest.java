package com.browicy.conformance;

import org.junit.Test;

/** Float-Layout: gefloatete Slides shrink-wrappen und liegen nebeneinander. */
public final class FloatConformanceTest extends LayoutConformanceTestBase {

    @Test
    public void matchesSlickStyleFloatedSlides() {
        // Muster der wetter.de-Vorhersage-Pills: slick.css styled Slides mit
        // float:left und width:auto; sie müssen auf Inhaltsbreite schrumpfen
        // und horizontal nebeneinander liegen.
        String html = """
                <!doctype html>
                <html><head><style>
                  html, body { margin: 0; padding: 0; }
                  .slick-track { width: 900px; position: relative; overflow: hidden; }
                  .slick-slide { display: none; float: left; height: 40px;
                                  min-height: 1px; padding: 8px;
                                  box-sizing: border-box; }
                  .slick-initialized .slick-slide { display: block; }
                </style></head><body>
                  <div class="slick-track slick-initialized">
                    <div class="slick-slide">Berlin</div>
                    <div class="slick-slide">Koeln</div>
                    <div class="slick-slide">Hamburg</div>
                    <div class="slick-slide">Muenchen</div>
                  </div>
                </body></html>
                """;
        // Toleranz großzügiger wegen unterschiedlicher Font-Metriken bei den
        // Wortbreiten (Chrome Segoe UI vs. Browicy-Schrift).
        assertConforms("float-slick-slides", html, 1200, 400, 6f);
    }

    @Test
    public void matchesPercentageWidthChildInsideFloat() {
        // w-full-Kinder (width:100%) tragen zur max-content-Breite des Floats
        // nichts bei (css-sizing: Prozent wie auto bei intrinsischer Größe).
        String html = """
                <!doctype html>
                <html><head><style>
                  html, body { margin: 0; padding: 0; }
                  .float { float: left; }
                  .full { width: 100%; }
                </style></head><body>
                  <div class="float">
                    <div class="full">Kurz</div>
                  </div>
                </body></html>
                """;
        // Toleranz wegen unterschiedlicher Font-Zeilenhöhen (Chrome 18px vs.
        // Browicy 21px bei gleichem font-size).
        assertConforms("float-percent-child", html, 800, 200, 6f);
    }
}

package com.browicy.integration;

import com.browicy.engine.css.StyleApplicator;
import com.browicy.engine.dom.Document;
import com.browicy.engine.html.HtmlParser;
import com.browicy.engine.render.RenderTree;
import com.browicy.engine.render.RenderTreeBuilder;
import com.browicy.ui.render.RenderLayoutEngine;
import com.browicy.ui.render.RenderLayoutEngine.BoxFragment;
import com.browicy.ui.render.RenderLayoutEngine.LayoutResult;
import com.browicy.ui.render.RenderLayoutEngine.LineBox;
import com.browicy.ui.render.RenderLayoutEngine.TextFragment;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * End-to-End-Box-Model-Tests gegen den echten Layout-Durchlauf: Margin
 * Collapsing (Geschwister, Eltern/Kind, keine Kollaps in Flex/Grid),
 * mathematische Funktionen (calc/min/max/clamp) und Flexbox-Ausrichtung
 * (baseline, wrap mit flex-grow).
 */
public class LayoutBoxModelTest {

    private static final int VIEWPORT_WIDTH = 800;
    private static final int VIEWPORT_HEIGHT = 600;

    @Test
    public void siblingMarginsCollapseToTheLargerValue() {
        String html = """
                <html><head><style>
                  html, body { margin: 0; padding: 0; }
                  p { margin: 0; }
                </style></head><body>
                  <p id="first" style="margin-bottom: 20px">Erster Absatz</p>
                  <p id="second" style="margin-top: 30px">Zweiter Absatz</p>
                </body></html>
                """;
        LayoutResult layout = layout(html);
        BoxFragment first = boxById(layout, "first");
        BoxFragment second = boxById(layout, "second");
        // 20px und 30px kollabieren auf max(20, 30) = 30px.
        assertEquals(30f, second.y() - (first.y() + first.height()), 0.01f);
    }

    @Test
    public void childMarginCollapsesWithParentWithoutBorderOrPadding() {
        String html = """
                <html><head><style>
                  html, body { margin: 0; padding: 0; }
                </style></head><body>
                  <div id="before" style="height: 10px"></div>
                  <div id="parent">
                    <div id="child" style="margin-top: 25px; margin-bottom: 15px">Kind</div>
                  </div>
                </body></html>
                """;
        LayoutResult layout = layout(html);
        BoxFragment before = boxById(layout, "before");
        BoxFragment parent = boxById(layout, "parent");
        BoxFragment child = boxById(layout, "child");

        // Die 25px des Kindes kollabieren mit der (0px) Margin des Elternteils:
        // der Elternteil rückt um 25px ab, das Kind sitzt an dessen Content-Kante.
        assertEquals(25f, parent.y() - (before.y() + before.height()), 0.01f);
        assertEquals(parent.y(), child.y(), 0.01f);
        // Die 15px bottom-Margin des Kindes kollabieren: Höhe des Elternteils
        // endet an der Border-Box des Kindes.
        assertEquals(parent.y() + parent.height(), child.y() + child.height(), 0.01f);
        assertEquals(parent.height(), child.height(), 0.01f);
    }

    @Test
    public void flexAndGridContainersDoNotCollapseMargins() {
        String html = """
                <html><head><style>
                  html, body { margin: 0; padding: 0; }
                  #flex { display: flex; width: 300px; }
                  #grid { display: grid; grid-template-columns: 1fr; width: 300px; }
                  .item { flex: 0 0 50px; height: 10px; }
                </style></head><body>
                  <div id="flex">
                    <div id="fa" class="item" style="margin-right: 20px"></div>
                    <div id="fb" class="item" style="margin-left: 30px"></div>
                  </div>
                  <div id="grid">
                    <div id="ga" class="item" style="margin-bottom: 20px"></div>
                    <div id="gb" class="item" style="margin-top: 30px"></div>
                  </div>
                </body></html>
                """;
        LayoutResult layout = layout(html);
        BoxFragment fa = boxById(layout, "fa");
        BoxFragment fb = boxById(layout, "fb");
        BoxFragment ga = boxById(layout, "ga");
        BoxFragment gb = boxById(layout, "gb");

        // In Flex- und Grid-Containern kollabieren Margins nicht: die Margins
        // addieren sich (20px + 30px = 50px) statt auf max(20, 30) zu schrumpfen.
        assertEquals(50f, fb.x() - (fa.x() + fa.width()), 0.01f);
        assertEquals(50f, gb.y() - (ga.y() + ga.height()), 0.01f);
    }

    @Test
    public void mathFunctionsCalcMinMaxClampResolveToPixels() {
        String html = """
                <html><head><style>
                  html, body { margin: 0; padding: 0; }
                  #calc { width: calc(100% - 50px); }
                  #clamp { height: clamp(100px, 50vh, 500px); }
                  #minimum { width: min(600px, 80vw); }
                  #maximum { width: max(200px, 30vw); }
                </style></head><body>
                  <div id="calc">calc</div>
                  <div id="clamp">clamp</div>
                  <div id="minimum">min</div>
                  <div id="maximum">max</div>
                </body></html>
                """;
        LayoutResult layout = layout(html);
        // calc(100% - 50px) = 800 - 50 = 750
        assertEquals(750f, boxById(layout, "calc").width(), 0.01f);
        // clamp(100px, 50vh, 500px): 50vh = 300, innerhalb [100, 500]
        assertEquals(300f, boxById(layout, "clamp").height(), 0.01f);
        // min(600px, 80vw) = min(600, 640) = 600
        assertEquals(600f, boxById(layout, "minimum").width(), 0.01f);
        // max(200px, 30vw) = max(200, 240) = 240
        assertEquals(240f, boxById(layout, "maximum").width(), 0.01f);
    }

    @Test
    public void flexBaselineAlignsTextBaselinesOfDifferentFontSizes() {
        String html = """
                <html><head><style>
                  html, body { margin: 0; padding: 0; }
                  #flex { display: flex; align-items: baseline; }
                </style></head><body>
                  <div id="flex">
                    <div id="big" style="font-size: 30px">Groß</div>
                    <div id="small" style="font-size: 12px">Klein</div>
                  </div>
                </body></html>
                """;
        LayoutResult layout = layout(html);
        float bigBaseline = lineBaselineFor(layout, "Groß");
        float smallBaseline = lineBaselineFor(layout, "Klein");
        assertEquals("Baselines der Textläufe müssen auf derselben Y-Koordinate liegen",
                bigBaseline, smallBaseline, 0.01f);
    }

    @Test
    public void flexWrapDistributesGrowAcrossRows() {
        String html = """
                <html><head><style>
                  html, body { margin: 0; padding: 0; }
                  #flex { display: flex; flex-wrap: wrap; width: 450px; }
                  .item { flex: 1 1 100px; height: 20px; }
                </style></head><body>
                  <div id="flex">
                    <div id="i1" class="item">eins</div>
                    <div id="i2" class="item">zwei</div>
                    <div id="i3" class="item">drei</div>
                  </div>
                </body></html>
                """;
        LayoutResult layout = layout(html);
        BoxFragment i1 = boxById(layout, "i1");
        BoxFragment i2 = boxById(layout, "i2");
        BoxFragment i3 = boxById(layout, "i3");
        // 3 × 100px Basis = 300px, 150px Überschuss -> je 50px mehr.
        assertEquals(150f, i1.width(), 0.01f);
        assertEquals(150f, i2.width(), 0.01f);
        assertEquals(150f, i3.width(), 0.01f);
        assertEquals(i1.y(), i2.y(), 0.01f);
        assertEquals(i1.y(), i3.y(), 0.01f);
    }

    @Test
    public void flexWrapBreaksItemsIntoNewRows() {
        String html = """
                <html><head><style>
                  html, body { margin: 0; padding: 0; }
                  #flex { display: flex; flex-wrap: wrap; width: 450px; }
                  .item { flex: 1 1 150px; height: 20px; }
                </style></head><body>
                  <div id="flex">
                    <div id="r1" class="item">eins</div>
                    <div id="r2" class="item">zwei</div>
                    <div id="r3" class="item">drei</div>
                    <div id="r4" class="item">vier</div>
                  </div>
                </body></html>
                """;
        LayoutResult layout = layout(html);
        BoxFragment r1 = boxById(layout, "r1");
        BoxFragment r2 = boxById(layout, "r2");
        BoxFragment r3 = boxById(layout, "r3");
        BoxFragment r4 = boxById(layout, "r4");
        // 450px / 150px = 3 pro Zeile; das vierte Element bricht um.
        assertEquals(150f, r1.width(), 0.01f);
        assertEquals(150f, r3.width(), 0.01f);
        assertEquals(r1.y(), r2.y(), 0.01f);
        assertEquals(r1.y(), r3.y(), 0.01f);
        assertTrue("viertes Element muss in die nächste Zeile brechen",
                r4.y() >= r1.y() + r1.height());
    }

    private static LayoutResult layout(String html) {
        Document document = new HtmlParser().parse(html, "about:test");
        new StyleApplicator().apply(document, VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
        RenderTree tree = new RenderTreeBuilder().build(document, VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            return new RenderLayoutEngine().layout(
                    tree, VIEWPORT_WIDTH, new Insets(0, 0, 0, 0), graphics);
        } finally {
            graphics.dispose();
        }
    }

    private static BoxFragment boxById(LayoutResult layout, String id) {
        return layout.fragments().stream()
                .filter(BoxFragment.class::isInstance)
                .map(BoxFragment.class::cast)
                .filter(fragment -> fragment.box().source() != null)
                .filter(fragment -> id.equals(fragment.box().source().getAttribute("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Kein Fragment für #" + id));
    }

    /** Baseline der Zeile, in der ein Textfragment mit dem gegebenen Text liegt. */
    private static float lineBaselineFor(LayoutResult layout, String text) {
        List<TextFragment> matches = layout.fragments().stream()
                .filter(TextFragment.class::isInstance)
                .map(TextFragment.class::cast)
                .filter(fragment -> fragment.text().strip().equals(text))
                .toList();
        assertTrue("Kein Textfragment für '" + text + "'", !matches.isEmpty());
        TextFragment wanted = matches.getFirst();
        return layout.lineBoxes().stream()
                .filter(line -> line.fragments().contains(wanted))
                .map(LineBox::baseline)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Keine Zeile für '" + text + "'"));
    }
}

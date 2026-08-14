package com.browicy.integration;

import com.browicy.engine.css.StyleApplicator;
import com.browicy.engine.css.StyleSheetRegistry;
import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.js.JsExecutionResult;
import com.browicy.engine.render.RenderTree;
import com.browicy.engine.render.RenderTreeBuilder;
import com.browicy.ui.render.RenderLayoutEngine;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifiziert, dass {@code getBoundingClientRect()}, {@code offset*} und
 * {@code client*} die echten berechneten Layout-Werte aus der
 * {@link RenderLayoutEngine} liefern statt statischer Mock-Werte.
 */
public class ElementMetricsIntegrationTest {

    private static final int VIEWPORT_WIDTH = 800;
    private static final int VIEWPORT_HEIGHT = 600;

    private static final String BOX_HTML = """
            <html><head><style>
              #box { position: absolute; left: 25px; top: 30px; width: 120px; height: 60px;
                     padding: 5px 10px; border: 2px solid black; }
            </style></head>
            <body><div id="out"></div><div id="box">Inhalt</div></body></html>
            """;

    @Test
    public void boundingClientRectMatchesAbsoluteBoxWithPaddingAndBorder() {
        try (JsLayoutHarness harness = JsLayoutHarness.open(
                BOX_HTML, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)) {
            JsExecutionResult result = harness.execute("""
                    const r = document.getElementById('box').getBoundingClientRect();
                    document.getElementById('out').textContent =
                        [r.x, r.y, r.width, r.height, r.top, r.right, r.bottom, r.left].join('|');
                    """);
            assertFalse(String.valueOf(result.errors()), result.hasErrors());

            double[] rect = parseDoubles(harness.document().getElementById("out").getTextContent());
            // width = 120 + padding 10+10 + border 2+2 = 144; height = 60 + 5+5 + 2+2 = 74
            assertRect(25, 30, 144, 74, rect);
        }
    }

    @Test
    public void boundingClientRectMatchesRenderLayoutEngineFragmentsExactly() {
        try (JsLayoutHarness harness = JsLayoutHarness.open(
                BOX_HTML, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)) {
            JsExecutionResult result = harness.execute("""
                    const r = document.getElementById('box').getBoundingClientRect();
                    document.getElementById('out').textContent =
                        [r.x, r.y, r.width, r.height, r.top, r.right, r.bottom, r.left].join('|');
                    """);
            assertFalse(String.valueOf(result.errors()), result.hasErrors());

            double[] jsRect = parseDoubles(
                    harness.document().getElementById("out").getTextContent());
            ElementBox fragment = aggregateFragment(
                    harness.document(), harness.styleSheets(), "box",
                    VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
            assertRect(fragment.left(), fragment.top(),
                    fragment.width(), fragment.height(), jsRect);
        }
    }

    @Test
    public void offsetWidthIsBorderBoxAndClientWidthIsPaddingBox() {
        try (JsLayoutHarness harness = JsLayoutHarness.open(
                BOX_HTML, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)) {
            JsExecutionResult result = harness.execute("""
                    const el = document.getElementById('box');
                    document.getElementById('out').textContent =
                        [el.offsetWidth, el.offsetHeight, el.clientWidth, el.clientHeight,
                         el.offsetLeft, el.offsetTop].join('|');
                    """);
            assertFalse(String.valueOf(result.errors()), result.hasErrors());

            double[] values = parseDoubles(
                    harness.document().getElementById("out").getTextContent());
            // offsetWidth = width + padding + border = 120 + 20 + 4 = 144
            assertEquals(144, values[0], 0.01);
            // offsetHeight = 60 + 10 + 4 = 74
            assertEquals(74, values[1], 0.01);
            // clientWidth = width + padding = 140; clientHeight = 60 + 10 = 70
            assertEquals(140, values[2], 0.01);
            assertEquals(70, values[3], 0.01);
            // offsetLeft/Top relativ zur Padding-Kante des offsetParent (body, 0/0)
            assertEquals(25, values[4], 0.01);
            assertEquals(30, values[5], 0.01);
        }
    }

    @Test
    public void displayNoneYieldsZeroMetrics() {
        String html = """
                <html><head><style>
                  #hidden { display: none; width: 50px; height: 20px; padding: 4px; border: 1px solid; }
                </style></head>
                <body><div id="out"></div><div id="hidden">versteckt</div></body></html>
                """;
        try (JsLayoutHarness harness = JsLayoutHarness.open(
                html, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)) {
            JsExecutionResult result = harness.execute("""
                    const el = document.getElementById('hidden');
                    const r = el.getBoundingClientRect();
                    document.getElementById('out').textContent =
                        [r.x, r.y, r.width, r.height, r.top, r.right, r.bottom, r.left,
                         el.offsetWidth, el.offsetHeight, el.clientWidth, el.clientHeight,
                         el.offsetLeft, el.offsetTop].join('|');
                    """);
            assertFalse(String.valueOf(result.errors()), result.hasErrors());

            double[] values = parseDoubles(
                    harness.document().getElementById("out").getTextContent());
            for (double value : values) {
                assertEquals("display:none muss alle Metriken auf 0 setzen", 0, value, 0.001);
            }
        }
    }

    @Test
    public void getClientRectsReturnsSingleAggregatedRect() {
        try (JsLayoutHarness harness = JsLayoutHarness.open(
                BOX_HTML, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)) {
            JsExecutionResult result = harness.execute("""
                    const rects = document.getElementById('box').getClientRects();
                    document.getElementById('out').textContent =
                        rects.length + '|' + (rects[0] ? rects[0].width : '') + '|' + rects[0].height;
                    """);
            assertFalse(String.valueOf(result.errors()), result.hasErrors());
            String[] parts = harness.document().getElementById("out").getTextContent().split("\\|");
            assertEquals("1", parts[0]);
            assertEquals("144", parts[1]);
            assertEquals("74", parts[2]);
        }
    }

    private static double[] parseDoubles(String text) {
        String[] parts = text.split("\\|");
        double[] values = new double[parts.length];
        for (int index = 0; index < parts.length; index++) {
            values[index] = Double.parseDouble(parts[index]);
        }
        return values;
    }

    private static void assertRect(double x, double y, double width, double height,
                                   double[] rect) {
        assertEquals(8, rect.length);
        assertEquals(x, rect[0], 0.01);
        assertEquals(y, rect[1], 0.01);
        assertEquals(width, rect[2], 0.01);
        assertEquals(height, rect[3], 0.01);
        assertEquals(y, rect[4], 0.01);          // top
        assertEquals(x + width, rect[5], 0.01);  // right
        assertEquals(y + height, rect[6], 0.01); // bottom
        assertEquals(x, rect[7], 0.01);          // left
    }

    /** Aggregierte Border-Box des Elements direkt aus den Layout-Fragmenten. */
    private static ElementBox aggregateFragment(Document document,
                                                StyleSheetRegistry styleSheets,
                                                String id,
                                                int viewportWidth,
                                                int viewportHeight) {
        Element target = document.getElementById(id);
        new StyleApplicator().apply(document, styleSheets, viewportWidth, viewportHeight);
        RenderTree tree = new RenderTreeBuilder().build(document, viewportWidth, viewportHeight);
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        RenderLayoutEngine.LayoutResult layout;
        try {
            layout = new RenderLayoutEngine().layout(
                    tree, viewportWidth, new Insets(0, 0, 0, 0), graphics);
        } finally {
            graphics.dispose();
        }
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        boolean found = false;
        for (RenderLayoutEngine.PaintFragment fragment : layout.fragments()) {
            float x = 0;
            float y = 0;
            float width = 0;
            float height = 0;
            if (fragment instanceof RenderLayoutEngine.BoxFragment box
                    && box.box().source() == target) {
                x = box.x();
                y = box.y();
                width = box.width();
                height = box.height();
            } else if (fragment instanceof RenderLayoutEngine.InlineBoxFragment inline
                    && inline.box().source() == target) {
                x = inline.x();
                y = inline.y();
                width = inline.width();
                height = inline.height();
            } else {
                continue;
            }
            found = true;
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, x + width);
            bottom = Math.max(bottom, y + height);
        }
        assertTrue("Element #" + id + " hat kein Layout-Fragment", found);
        return new ElementBox(left, top, right - left, bottom - top);
    }

    private record ElementBox(float left, float top, float width, float height) {
    }
}

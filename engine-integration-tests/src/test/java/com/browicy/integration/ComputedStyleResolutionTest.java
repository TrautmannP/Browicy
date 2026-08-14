package com.browicy.integration;

import com.browicy.engine.js.JsExecutionResult;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verifiziert, dass {@code getComputedStyle()} für dimensionale Eigenschaften
 * aufgelöste Pixelwerte (Used Values) liefert statt der Rohwerte der Kaskade:
 * {@code font-size} gegen Root-/Parent-Fontgröße, {@code width} / Margins aus
 * dem Layout.
 */
public class ComputedStyleResolutionTest {

    private static final int VIEWPORT_WIDTH = 800;
    private static final int VIEWPORT_HEIGHT = 600;

    @Test
    public void fontSizeResolvesRemAgainstRootFontSize() {
        String html = """
                <html><head><style>
                  #big { font-size: 2rem; }
                </style></head>
                <body><div id="out"></div><div id="big">Text</div></body></html>
                """;
        try (JsLayoutHarness harness = JsLayoutHarness.open(
                html, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)) {
            JsExecutionResult result = harness.execute("""
                    const el = document.getElementById('big');
                    document.getElementById('out').textContent =
                        getComputedStyle(el).fontSize + '|' +
                        getComputedStyle(el).getPropertyValue('font-size');
                    """);
            assertFalse(String.valueOf(result.errors()), result.hasErrors());
            String[] values = harness.document().getElementById("out").getTextContent().split("\\|");
            assertEquals("32px", values[0]);
            assertEquals("32px", values[1]);
        }
    }

    @Test
    public void fontSizeResolvesEmAgainstParentFontSize() {
        String html = """
                <html><head><style>
                  #parent { font-size: 20px; }
                  #child { font-size: 1.5em; }
                </style></head>
                <body><div id="out"></div>
                  <div id="parent"><div id="child">Kind</div></div>
                </body></html>
                """;
        try (JsLayoutHarness harness = JsLayoutHarness.open(
                html, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)) {
            JsExecutionResult result = harness.execute("""
                    const el = document.getElementById('child');
                    document.getElementById('out').textContent =
                        getComputedStyle(el).fontSize;
                    """);
            assertFalse(String.valueOf(result.errors()), result.hasErrors());
            assertEquals("30px", harness.document().getElementById("out").getTextContent());
        }
    }

    @Test
    public void widthResolvesPercentageAgainstContainingBlock() {
        String html = """
                <html><head><style>
                  #container { width: 800px; }
                  #half { width: 50%; }
                </style></head>
                <body><div id="out"></div>
                  <div id="container"><div id="half">Hälfte</div></div>
                </body></html>
                """;
        try (JsLayoutHarness harness = JsLayoutHarness.open(
                html, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)) {
            JsExecutionResult result = harness.execute("""
                    const el = document.getElementById('half');
                    document.getElementById('out').textContent =
                        getComputedStyle(el).width + '|' + getComputedStyle(el).height;
                    """);
            assertFalse(String.valueOf(result.errors()), result.hasErrors());
            String[] values = harness.document().getElementById("out").getTextContent().split("\\|");
            assertEquals("400px", values[0]);
            // Höhe ohne explizites height: gemessener Inhalt (>= 1px), kein Rohwert "auto".
            assertNotEquals("auto", values[1]);
            assertTrue("height war: '" + values[1] + "'", values[1].endsWith("px"));
        }
    }

    @Test
    public void autoMarginsResolveToUsedPixels() {
        String html = """
                <html><head><style>
                  #auto { width: 400px; margin: 0 auto; }
                </style></head>
                <body><div id="out"></div><div id="auto">zentriert</div></body></html>
                """;
        try (JsLayoutHarness harness = JsLayoutHarness.open(
                html, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)) {
            JsExecutionResult result = harness.execute("""
                    const cs = getComputedStyle(document.getElementById('auto'));
                    document.getElementById('out').textContent =
                        cs.marginLeft + '|' + cs.marginRight;
                    """);
            assertFalse(String.valueOf(result.errors()), result.hasErrors());
            String[] values = harness.document().getElementById("out").getTextContent().split("\\|");
            // (800 - 400) / 2 = 200px auf jeder Seite
            assertEquals("200px", values[0]);
            assertEquals("200px", values[1]);
        }
    }

    @Test
    public void paddingBorderAndOffsetsResolveToPixels() {
        String html = """
                <html><head><style>
                  #box { position: relative; left: 10px; top: 5px; width: 100px; height: 100px;
                         padding: 3px 7px; border: 2px solid black; }
                </style></head>
                <body><div id="out"></div><div id="box">Box</div></body></html>
                """;
        try (JsLayoutHarness harness = JsLayoutHarness.open(
                html, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)) {
            JsExecutionResult result = harness.execute("""
                    const cs = getComputedStyle(document.getElementById('box'));
                    document.getElementById('out').textContent = [
                        cs.paddingTop, cs.paddingLeft, cs.paddingRight, cs.paddingBottom,
                        cs.borderLeftWidth, cs.borderTopWidth,
                        cs.left, cs.top,
                        cs.width, cs.height
                    ].join('|');
                    """);
            assertFalse(String.valueOf(result.errors()), result.hasErrors());
            String[] values = harness.document().getElementById("out").getTextContent().split("\\|");
            assertEquals("3px", values[0]);   // paddingTop
            assertEquals("7px", values[1]);   // paddingLeft
            assertEquals("7px", values[2]);   // paddingRight
            assertEquals("3px", values[3]);   // paddingBottom
            assertEquals("2px", values[4]);   // borderLeftWidth
            assertEquals("2px", values[5]);   // borderTopWidth
            assertEquals("10px", values[6]);  // left (relative Versatz)
            assertEquals("5px", values[7]);   // top
            assertEquals("100px", values[8]); // width (Content-Box)
            assertEquals("100px", values[9]); // height (Content-Box)
        }
    }

    @Test
    public void absentLayoutFallsBackToCascadeValues() {
        String html = """
                <html><head><style>
                  #plain { width: 120px; position: static; }
                </style></head>
                <body><div id="out"></div>
                  <div id="holder" style="display:none"><div id="plain">unsichtbar</div></div>
                </body></html>
                """;
        try (JsLayoutHarness harness = JsLayoutHarness.open(
                html, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)) {
            JsExecutionResult result = harness.execute("""
                    const el = document.getElementById('plain');
                    document.getElementById('out').textContent =
                        getComputedStyle(el).width + '|' + getComputedStyle(el).position;
                    """);
            assertFalse(String.valueOf(result.errors()), result.hasErrors());
            String[] values = harness.document().getElementById("out").getTextContent().split("\\|");
            // Kein Layout-Fragment: Kaskade liefert den Rohwert "120px".
            assertEquals("120px", values[0]);
            assertEquals("static", values[1]);
        }
    }
}

package com.browicy.engine.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CssColorTest {

    @Test
    public void parsesRgbaPercentagesAndHexAlpha() {
        assertEquals(new CssColor(60, 64, 67, 77), CssColor.parse("rgba(60,64,67,.3)"));
        assertEquals(new CssColor(255, 128, 0, 128), CssColor.parse("rgba(100%,50%,0%,50%)"));
        assertEquals(new CssColor(0x42, 0x85, 0xf4, 0x80), CssColor.parse("#4285f480"));
        assertEquals(new CssColor(0x44, 0x88, 0xff, 0x88), CssColor.parse("#48f8"));
    }

    @Test
    public void parsesHslAndHsla() {
        assertEquals(new CssColor(0, 0, 0, 255), CssColor.parse("hsl(0,0%,0%)"));
        assertEquals(new CssColor(255, 255, 255, 255), CssColor.parse("hsl(0,0%,100%)"));
        assertEquals(new CssColor(128, 128, 128, 255), CssColor.parse("hsl(0,0%,50%)"));
        assertEquals(new CssColor(255, 0, 0, 255), CssColor.parse("hsl(0,100%,50%)"));
        assertEquals(new CssColor(0, 255, 0, 255), CssColor.parse("hsl(120,100%,50%)"));
        assertEquals(new CssColor(0, 0, 255, 255), CssColor.parse("hsl(240,100%,50%)"));
        assertEquals(new CssColor(255, 0, 0, 128), CssColor.parse("hsla(0,100%,50%,.5)"));
        assertEquals(new CssColor(0, 255, 255, 255), CssColor.parse("hsl(180 100% 50%)"));
        assertEquals(new CssColor(0, 128, 255, 77), CssColor.parse("hsl(210 100% 50% / .3)"));
        assertEquals(new CssColor(0, 0, 0, 255), CssColor.parse("HSL(0,0%,0%)"));
    }

    @Test
    public void rejectsOutOfRangeChannels() {
        assertNull(CssColor.parse("rgba(256,0,0,1)"));
        assertNull(CssColor.parse("rgba(0,0,0,1.1)"));
        assertNull(CssColor.parse("hsl(0,0%,101%)"));
        assertNull(CssColor.parse("hsl(0,-1%,50%)"));
        assertNull(CssColor.parse("hsla(0,0%,50%,1.5)"));
        assertNull(CssColor.parse("hsl(0,0%)"));
    }
}

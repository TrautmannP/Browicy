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
    public void rejectsOutOfRangeChannels() {
        assertNull(CssColor.parse("rgba(256,0,0,1)"));
        assertNull(CssColor.parse("rgba(0,0,0,1.1)"));
    }
}

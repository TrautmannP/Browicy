package com.browicy.ui.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.browicy.engine.render.RenderStyle;
import org.junit.Test;

public class FloatExclusionSpaceTest {

    private static final float EPS = 1e-4f;

    private static FloatExclusionSpace space() {
        return new FloatExclusionSpace();
    }

    private static FloatExclusionSpace.FloatRegion region(RenderStyle.FloatMode mode,
                                                          float x, float y,
                                                          float width, float height) {
        return new FloatExclusionSpace.FloatRegion(mode, x, y, width, height);
    }

    @Test
    public void emptySpaceHasFullArea() {
        FloatExclusionSpace space = space();
        assertTrue(space.isEmpty());
        FloatExclusionSpace.FloatArea area = space.floatArea(100, 400, 50);
        assertEquals(100, area.x(), EPS);
        assertEquals(400, area.width(), EPS);
    }

    @Test
    public void leftFloatNarrowsAreaOnlyWhileSpanningY() {
        FloatExclusionSpace space = space();
        space.add(region(RenderStyle.FloatMode.LEFT, 100, 0, 150, 50));
        assertFalse(space.isEmpty());
        FloatExclusionSpace.FloatArea inside = space.floatArea(100, 400, 25);
        assertEquals(250, inside.x(), EPS);
        assertEquals(250, inside.width(), EPS);
        FloatExclusionSpace.FloatArea below = space.floatArea(100, 400, 75);
        assertEquals(100, below.x(), EPS);
        assertEquals(400, below.width(), EPS);
        assertEquals(100, space.floatArea(100, 400, 50).x(), EPS);
    }

    @Test
    public void rightFloatNarrowsAreaFromRight() {
        FloatExclusionSpace space = space();
        space.add(region(RenderStyle.FloatMode.RIGHT, 400, 0, 100, 30));
        FloatExclusionSpace.FloatArea inside = space.floatArea(100, 400, 10);
        assertEquals(100, inside.x(), EPS);
        assertEquals(300, inside.width(), EPS);
    }

    @Test
    public void opposingFloatsLeaveGapBetweenThem() {
        FloatExclusionSpace space = space();
        space.add(region(RenderStyle.FloatMode.LEFT, 0, 0, 150, 40));
        space.add(region(RenderStyle.FloatMode.RIGHT, 350, 0, 150, 40));
        FloatExclusionSpace.FloatArea area = space.floatArea(0, 500, 20);
        assertEquals(150, area.x(), EPS);
        assertEquals(200, area.width(), EPS);
    }

    @Test
    public void opposingFloatsThatMeetLeaveOnePixel() {
        FloatExclusionSpace space = space();
        space.add(region(RenderStyle.FloatMode.LEFT, 0, 0, 250, 40));
        space.add(region(RenderStyle.FloatMode.RIGHT, 250, 0, 250, 40));
        FloatExclusionSpace.FloatArea area = space.floatArea(0, 500, 20);
        assertEquals(250, area.x(), EPS);
        assertEquals(1, area.width(), EPS);
    }

    @Test
    public void queryOriginIsUsedNotSpaceOrigin() {
        FloatExclusionSpace shared = space();
        shared.add(region(RenderStyle.FloatMode.LEFT, 0, 0, 50, 300));
        FloatExclusionSpace.FloatArea childArea = shared.floatArea(100, 400, 0);
        assertEquals(100, childArea.x(), EPS);
        assertEquals(400, childArea.width(), EPS);
        FloatExclusionSpace.FloatArea rootArea = shared.floatArea(0, 500, 0);
        assertEquals(50, rootArea.x(), EPS);
        assertEquals(450, rootArea.width(), EPS);
    }

    @Test
    public void newContextIsolation() {
        FloatExclusionSpace first = space();
        FloatExclusionSpace second = space();
        first.add(region(RenderStyle.FloatMode.LEFT, 0, 0, 300, 100));
        assertEquals(500, second.floatArea(0, 500, 50).width(), EPS);
        assertEquals(200, first.floatArea(0, 500, 50).width(), EPS);
    }

    @Test
    public void clearedYIgnoresNonMatchingSides() {
        FloatExclusionSpace space = space();
        space.add(region(RenderStyle.FloatMode.LEFT, 0, 0, 100, 80));
        space.add(region(RenderStyle.FloatMode.RIGHT, 400, 0, 100, 120));
        assertEquals(80, space.clearedY(10, RenderStyle.Clear.LEFT), EPS);
        assertEquals(120, space.clearedY(10, RenderStyle.Clear.RIGHT), EPS);
        assertEquals(120, space.clearedY(10, RenderStyle.Clear.BOTH), EPS);
        assertEquals(10, space.clearedY(10, RenderStyle.Clear.NONE), EPS);
        assertEquals(120, space.clearedY(30, RenderStyle.Clear.BOTH), EPS);
        assertEquals(130, space.clearedY(130, RenderStyle.Clear.BOTH), EPS);
    }

    @Test
    public void dropBelowFloatsIfNarrowSkipsWhenEmpty() {
        assertEquals(42, space().dropBelowFloatsIfNarrow(0, 500, 42, 100), EPS);
    }

    @Test
    public void dropBelowFloatsIfNarrowOnlyWhenTooNarrow() {
        FloatExclusionSpace space = space();
        space.add(region(RenderStyle.FloatMode.LEFT, 0, 0, 300, 90));
        assertEquals(10, space.dropBelowFloatsIfNarrow(0, 500, 10, 100), EPS);
        assertEquals(90, space.dropBelowFloatsIfNarrow(0, 500, 10, 300), EPS);
        assertEquals(10, space.dropBelowFloatsIfNarrow(0, 500, 10, 0), EPS);
    }

    @Test
    public void firstFitYStaysWhenContentFits() {
        FloatExclusionSpace space = space();
        space.add(region(RenderStyle.FloatMode.LEFT, 0, 0, 300, 90));
        assertEquals(10, space.firstFitY(0, 500, 10, 200), EPS);
        assertEquals(10, space.firstFitY(0, 500, 10, 0), EPS);
    }

    @Test
    public void firstFitYDropsBelowOverlappingFloats() {
        FloatExclusionSpace space = space();
        space.add(region(RenderStyle.FloatMode.LEFT, 0, 0, 250, 75));
        space.add(region(RenderStyle.FloatMode.RIGHT, 250, 0, 250, 75));
        assertEquals(75, space.firstFitY(0, 500, 0, 100), EPS);
        assertEquals(75, space.firstFitY(0, 500, 75, 100), EPS);
    }

    @Test
    public void firstFitYStopsAtFirstGapForStaggeredFloats() {
        FloatExclusionSpace space = space();
        space.add(region(RenderStyle.FloatMode.LEFT, 0, 0, 300, 40));
        space.add(region(RenderStyle.FloatMode.RIGHT, 250, 40, 250, 60));
        assertEquals(100, space.firstFitY(0, 500, 0, 300), EPS);
        assertEquals(40, space.firstFitY(0, 500, 0, 250), EPS);
        assertEquals(0, space.firstFitY(0, 500, 0, 150), EPS);
    }

    @Test
    public void lineSlotNextToSingleFloat() {
        FloatExclusionSpace space = space();
        space.add(region(RenderStyle.FloatMode.LEFT, 0, 0, 50, 75));
        space.add(region(RenderStyle.FloatMode.LEFT, 0, 75, 100, 75));
        FloatExclusionSpace.LineSlot line1 = space.lineSlot(0, 400, 0, 50, 200);
        assertEquals(0, line1.y(), EPS);
        assertEquals(50, line1.x(), EPS);
        assertEquals(350, line1.width(), EPS);
    }

    @Test
    public void lineSlotIsRectangleNotTopSlice() {
        FloatExclusionSpace space = space();
        space.add(region(RenderStyle.FloatMode.LEFT, 0, 0, 50, 75));
        space.add(region(RenderStyle.FloatMode.LEFT, 0, 75, 100, 75));
        FloatExclusionSpace.LineSlot line2 = space.lineSlot(0, 400, 50, 50, 200);
        assertEquals(50, line2.y(), EPS);
        assertEquals(100, line2.x(), EPS);
        assertEquals(300, line2.width(), EPS);
    }

    @Test
    public void lineSlotDropsBelowOverlappingFloats() {
        FloatExclusionSpace space = space();
        space.add(region(RenderStyle.FloatMode.LEFT, 0, 0, 150, 75));
        space.add(region(RenderStyle.FloatMode.RIGHT, 100, 75, 300, 75));
        FloatExclusionSpace.LineSlot slot = space.lineSlot(0, 400, 50, 50, 200);
        assertEquals(150, slot.y(), EPS);
        assertEquals(0, slot.x(), EPS);
        assertEquals(400, slot.width(), EPS);
    }

    @Test
    public void lineSlotFitsGapBetweenStaggeredLeftFloats() {
        FloatExclusionSpace space = space();
        space.add(region(RenderStyle.FloatMode.LEFT, 0, 0, 250, 75));
        space.add(region(RenderStyle.FloatMode.LEFT, 150, 75, 250, 75));
        FloatExclusionSpace.LineSlot slot = space.lineSlot(0, 400, 50, 50, 100);
        assertEquals(75, slot.y(), EPS);
        assertEquals(0, slot.x(), EPS);
        assertEquals(150, slot.width(), EPS);
    }

    @Test
    public void lineSlotTouchingFloatIsFree() {
        FloatExclusionSpace space = space();
        space.add(region(RenderStyle.FloatMode.LEFT, 0, 75, 100, 75));
        FloatExclusionSpace.LineSlot slot = space.lineSlot(0, 400, 50, 50, 200);
        assertEquals(50, slot.y(), EPS);
        assertEquals(100, slot.x(), EPS);
        assertEquals(300, slot.width(), EPS);
    }
}

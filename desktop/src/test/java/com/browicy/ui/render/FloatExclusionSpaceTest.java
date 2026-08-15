package com.browicy.ui.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.browicy.engine.render.RenderStyle;
import org.junit.Test;

/** Verhaltens-Lock der extrahierten Float-Exclusions (CSS2.1 §9.5.1).
 *  Die Semantik stammt 1:1 aus RenderLayoutEngine (Refactoring ohne Diff);
 *  diese Tests fixieren sie isoliert, ohne dass eine Seite gerendert wird.
 *  contentX/contentWidth sind Abfrageparameter (geteilte Spaces); der
 *  Test setzt den Ursprung konsistent zum Inhalt. */
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
        // Innerhalb der Float-Höhe: Kante rückt nach rechts.
        FloatExclusionSpace.FloatArea inside = space.floatArea(100, 400, 25);
        assertEquals(250, inside.x(), EPS);
        assertEquals(250, inside.width(), EPS);
        // Unterhalb des Floats: wieder volle Breite.
        FloatExclusionSpace.FloatArea below = space.floatArea(100, 400, 75);
        assertEquals(100, below.x(), EPS);
        assertEquals(400, below.width(), EPS);
        // y = Unterkante ist bereits frei (y >= bottom gilt als außerhalb).
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
        assertEquals(1, area.width(), EPS); // Mindestbreite, nie 0
    }

    @Test
    public void queryOriginIsUsedNotSpaceOrigin() {
        // Regel-7-Konstellation: ein Nicht-BFC-Kind (contentX=100, Breite 400)
        // teilt den Space seines BFC (Ursprung 0/500). Die Abfragen laufen
        // mit der Origin des aktuellen Containers, nicht des BFC.
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
        // Der zweite BFC kennt den Float des ersten nicht (Regel 5).
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
        // clear bewegt nur abwärts: von 30 unter beide Floats.
        assertEquals(120, space.clearedY(30, RenderStyle.Clear.BOTH), EPS);
        // Bereits darunter: unverändert.
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
        // 100px Inhalt passen in 200px freie Breite.
        assertEquals(10, space.dropBelowFloatsIfNarrow(0, 500, 10, 100), EPS);
        // 300px passen nicht -> unter den Float sinken.
        assertEquals(90, space.dropBelowFloatsIfNarrow(0, 500, 10, 300), EPS);
        // minimumWidth <= 0 gilt als "passt immer".
        assertEquals(10, space.dropBelowFloatsIfNarrow(0, 500, 10, 0), EPS);
    }
}

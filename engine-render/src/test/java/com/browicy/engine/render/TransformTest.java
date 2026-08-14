package com.browicy.engine.render;

import org.junit.Test;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TransformTest {

    @Test
    public void parsesTranslateRotateAndScaleFunctions() {
        Transform transform = Transform.parse(
                "translate(-50%, 10px) rotate(90deg) scale(2)", 16);
        assertEquals(3, transform.operations().size());
        assertEquals(Transform.Kind.TRANSLATE, transform.operations().get(0).kind());
        assertEquals(Transform.Kind.ROTATE, transform.operations().get(1).kind());
        assertEquals(Transform.Kind.SCALE, transform.operations().get(2).kind());
    }

    @Test
    public void rejectsUnknownFunctionsAndJunk() {
        assertNull(Transform.parse("skew(10deg)", 16));
        assertNull(Transform.parse("translate(50%) garbage", 16));
        assertNull(Transform.parse("rotate()", 16));
        assertNull(Transform.parse("matrix(1, 2)", 16));
        assertTrue(Transform.parse("none", 16).isIdentity());
    }

    @Test
    public void acceptsUnitlessZeroTranslations() {
        Transform transform = Transform.parse("translate(0)", 16);
        assertEquals(1, transform.operations().size());
        AffineTransform matrix = transform.matrix(0, 0, 40, 40, 16, 800, 600);
        assertEquals(0, matrix.getTranslateX(), 0.001);
    }

    @Test
    public void translatesPercentagesAgainstOwnSizeAboutTheOrigin() {
        Transform transform = Transform.parse("translate(50%) translateY(-50%)", 16);
        // Box bei (100, 200), 40x60; Default-Origin (50%, 50%) = (120, 230).
        // translate(50%) = +20px x; translateY(-50%) = -30px y.
        AffineTransform matrix = transform.matrix(100, 200, 40, 60, 16, 800, 600);
        Point2D point = matrix.transform(new Point2D.Double(100, 200), null);
        assertEquals(100 + 20, point.getX(), 0.01);
        assertEquals(200 - 30, point.getY(), 0.01);
    }

    @Test
    public void rotatesAroundTheCustomOrigin() {
        Transform transform = Transform.parse("rotate(90deg)", 16)
                .withOrigin(new RenderOffset(0, RenderOffset.Unit.PERCENT),
                        new RenderOffset(0, RenderOffset.Unit.PERCENT));
        // Origin = Box-Top-Left (100, 200). Punkt (100, 200) bleibt fix.
        AffineTransform matrix = transform.matrix(100, 200, 40, 40, 16, 800, 600);
        Point2D origin = matrix.transform(new Point2D.Double(100, 200), null);
        assertEquals(100, origin.getX(), 0.01);
        assertEquals(200, origin.getY(), 0.01);
        // Rechte untere Ecke (140, 240): lokal (40,40); 90 Grad (Java2D, y nach unten)
        // -> (-40, 40); absolut (60, 240).
        Point2D corner = matrix.transform(new Point2D.Double(140, 240), null);
        assertEquals(60, corner.getX(), 0.01);
        assertEquals(240, corner.getY(), 0.01);
    }
}

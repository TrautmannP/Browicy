package com.browicy.engine.js;

/**
 * Viewport-relative border-box geometry of a laid-out element plus its border
 * and padding widths, in CSS pixels. AWT-frei, damit die JS-Schicht keine
 * Desktop-Abhängigkeit bekommt; geliefert von {@link LayoutMetricsAccess}.
 *
 * <p>Nicht gerenderte Elemente ({@code display: none}, disconnected, ohne Box)
 * melden {@code rendered == false} und alle Werte 0.</p>
 */
public record LayoutElementMetrics(
        boolean rendered,
        float left,
        float top,
        float width,
        float height,
        float borderLeft,
        float borderRight,
        float borderTop,
        float borderBottom,
        float paddingLeft,
        float paddingRight,
        float paddingTop,
        float paddingBottom) {

    public static final LayoutElementMetrics ZERO =
            new LayoutElementMetrics(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public float right() {
        return left + width;
    }

    public float bottom() {
        return top + height;
    }

    /** Padding-Box-Breite (border box minus Borders), entspricht {@code clientWidth}. */
    public float clientWidth() {
        return Math.max(0, width - borderLeft - borderRight);
    }

    /** Padding-Box-Höhe (border box minus Borders), entspricht {@code clientHeight}. */
    public float clientHeight() {
        return Math.max(0, height - borderTop - borderBottom);
    }

    /** Content-Box-Breite (border box minus Borders minus Padding), Used Value von {@code width}. */
    public float contentWidth() {
        return Math.max(0, width - borderLeft - borderRight - paddingLeft - paddingRight);
    }

    /** Content-Box-Höhe (border box minus Borders minus Padding), Used Value von {@code height}. */
    public float contentHeight() {
        return Math.max(0, height - borderTop - borderBottom - paddingTop - paddingBottom);
    }
}

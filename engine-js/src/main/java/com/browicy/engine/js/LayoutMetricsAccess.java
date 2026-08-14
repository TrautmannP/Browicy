package com.browicy.engine.js;

import com.browicy.engine.dom.Element;

/**
 * Lose Kopplung zwischen den JS-Layout-APIs ({@code getBoundingClientRect},
 * {@code offset*} / {@code client*}, {@code getComputedStyle}-Used-Values) und
 * der eigentlichen Layout-Engine (desktop: {@code RenderLayoutEngine}).
 *
 * <p>Die Implementierung läuft bei Bedarf synchron einen vollständigen
 * Style-/Render-Tree-/Layout-Durchlauf gegen das aktuelle Dokument und liefert
 * die berechneten Pixelwerte. Ohne registrierte Implementierung liefert
 * {@link #DISABLED} Nullen beziehungsweise {@code null}, so wie bisher.</p>
 */
public interface LayoutMetricsAccess {

    /**
     * Viewport-relative Border-Box des Elements aus dem aktuellen Layout.
     * Nicht gerenderte Elemente liefern {@link LayoutElementMetrics#ZERO}.
     */
    LayoutElementMetrics metricsFor(Element element);

    /**
     * Aufgelöster Used Value einer dimensionalen CSS-Eigenschaft (z. B.
     * {@code "400px"} für {@code width: 50%} im 800px-Container) oder
     * {@code null}, wenn die Eigenschaft nicht aufgelöst werden kann und der
     * Rohwert der Kaskade zurückfallen soll.
     */
    String resolvedValue(Element element, String property);

    /** No-op-Zugriff: keine Layout-Metriken, keine Used-Value-Auflösung. */
    LayoutMetricsAccess DISABLED = new LayoutMetricsAccess() {
        @Override
        public LayoutElementMetrics metricsFor(Element element) {
            return LayoutElementMetrics.ZERO;
        }

        @Override
        public String resolvedValue(Element element, String property) {
            return null;
        }
    };
}

package com.browicy.engine.render;

/**
 * Unveränderlicher Sub-Record des Box-Modells: Dimensionen, Abstände, Rahmen,
 * Rundungen und grundlegendes Box-Verhalten.
 *
 * <p>Enthält alle Eigenschaften, die die geometrische Box einer
 * Render-Box beschreiben (Größen, min/max-Grenzen, Margin/Padding/Border,
 * Overflow, vertikale Ausrichtung und Border-Collapse). Das
 * Seitenverhältnis ({@code aspectRatio}) wird hier validiert: es muss
 * entweder {@code NaN} (nicht gesetzt) oder endlich und positiv sein.</p>
 */
public record BoxModelStyle(
        RenderStyle.Display display,
        RenderStyle.BoxSizing boxSizing,
        RenderLength width,
        RenderLength height,
        RenderLength minWidth,
        RenderLength maxWidth,
        RenderLength minHeight,
        RenderLength maxHeight,
        float aspectRatio,
        RenderStyle.ObjectFit objectFit,
        BoxEdges margin,
        HorizontalAutoMargins autoMargins,
        BoxEdges padding,
        BoxEdges borderWidth,
        BoxColors borderColor,
        BoxBorders borderStyle,
        CornerRadii borderRadius,
        RenderStyle.Overflow overflow,
        RenderStyle.VerticalAlign verticalAlign,
        RenderStyle.BorderCollapse borderCollapse) {

    public BoxModelStyle {
        if (!Float.isNaN(aspectRatio)
                && (!Float.isFinite(aspectRatio) || aspectRatio <= 0)) {
            throw new IllegalArgumentException("aspectRatio must be positive or NaN");
        }
    }

    /**
     * Kopie dieses Stils mit geändertem {@code display}-Wert.
     *
     * @param value neuer Anzeigetyp
     * @return neue Instanz mit aktualisiertem {@code display}
     */
    public BoxModelStyle withDisplay(RenderStyle.Display value) {
        return new BoxModelStyle(value, boxSizing, width, height, minWidth, maxWidth,
                minHeight, maxHeight, aspectRatio, objectFit, margin, autoMargins,
                padding, borderWidth, borderColor, borderStyle, borderRadius,
                overflow, verticalAlign, borderCollapse);
    }

    /**
     * Kopie dieses Stils mit geänderter {@code width}.
     *
     * @param value neue Breite
     * @return neue Instanz mit aktualisierter {@code width}
     */
    public BoxModelStyle withWidth(RenderLength value) {
        return new BoxModelStyle(display, boxSizing, value, height, minWidth, maxWidth,
                minHeight, maxHeight, aspectRatio, objectFit, margin, autoMargins,
                padding, borderWidth, borderColor, borderStyle, borderRadius,
                overflow, verticalAlign, borderCollapse);
    }

    /**
     * Kopie dieses Stils mit geänderter {@code height}.
     *
     * @param value neue Höhe
     * @return neue Instanz mit aktualisierter {@code height}
     */
    public BoxModelStyle withHeight(RenderLength value) {
        return new BoxModelStyle(display, boxSizing, width, value, minWidth, maxWidth,
                minHeight, maxHeight, aspectRatio, objectFit, margin, autoMargins,
                padding, borderWidth, borderColor, borderStyle, borderRadius,
                overflow, verticalAlign, borderCollapse);
    }
}

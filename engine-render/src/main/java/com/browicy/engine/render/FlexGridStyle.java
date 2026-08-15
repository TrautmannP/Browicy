package com.browicy.engine.render;

import java.util.List;

/**
 * Unveränderlicher Sub-Record für Flexbox- und CSS-Grid-Eigenschaften.
 *
 * <p>Enthält die Container- und Item-Eigenschaften des Flex-Layouts
 * (Richtung, Umbruch, Ausrichtung, Reihenfolge, Flex-Wachstum/-Schrumpfung,
 * Basis und Lücken) sowie die Grid-Definitionen (Spalten-/Zeilen-Tracks,
 * automatische Tracks, benannte Areas, Auto-Flow und Linien-Platzierung).
 * {@code flexGrow}, {@code flexShrink} und die Lücken müssen endlich und
 * nicht-negativ sein; alle Track-Listen werden defensiv als unveränderliche
 * Kopien übernommen.</p>
 */
public record FlexGridStyle(
        RenderStyle.FlexDirection flexDirection,
        RenderStyle.FlexWrap flexWrap,
        RenderStyle.JustifyContent justifyContent,
        RenderStyle.AlignItems alignItems,
        RenderStyle.AlignSelf alignSelf,
        RenderStyle.AlignContent alignContent,
        int order,
        float flexGrow,
        float flexShrink,
        RenderLength flexBasis,
        float rowGapPx,
        float columnGapPx,
        List<RenderStyle.GridTrack> gridTemplateColumns,
        List<RenderStyle.GridTrack> gridTemplateRows,
        List<RenderStyle.GridTrack> gridAutoColumns,
        List<RenderStyle.GridTrack> gridAutoRows,
        String[][] gridTemplateAreas,
        RenderStyle.GridAutoFlow gridAutoFlow,
        RenderStyle.GridLine gridColumnStart,
        RenderStyle.GridLine gridColumnEnd,
        RenderStyle.GridLine gridRowStart,
        RenderStyle.GridLine gridRowEnd) {

    public FlexGridStyle {
        if (!Float.isFinite(flexGrow) || flexGrow < 0) {
            throw new IllegalArgumentException("flexGrow must be a finite non-negative number");
        }
        if (!Float.isFinite(flexShrink) || flexShrink < 0) {
            throw new IllegalArgumentException("flexShrink must be a finite non-negative number");
        }
        if (!Float.isFinite(rowGapPx) || rowGapPx < 0
                || !Float.isFinite(columnGapPx) || columnGapPx < 0) {
            throw new IllegalArgumentException("flex gaps must be finite and non-negative");
        }
        gridTemplateColumns = List.copyOf(gridTemplateColumns);
        gridTemplateRows = List.copyOf(gridTemplateRows);
        gridAutoColumns = List.copyOf(gridAutoColumns);
        gridAutoRows = List.copyOf(gridAutoRows);
    }

    /**
     * Kopie dieses Stils mit geändertem {@code flexGrow}-Wert.
     *
     * @param value neuer Flex-Wachstumsfaktor
     * @return neue Instanz mit aktualisiertem {@code flexGrow}
     */
    public FlexGridStyle withFlexGrow(float value) {
        return new FlexGridStyle(flexDirection, flexWrap, justifyContent, alignItems,
                alignSelf, alignContent, order, value, flexShrink, flexBasis,
                rowGapPx, columnGapPx, gridTemplateColumns, gridTemplateRows,
                gridAutoColumns, gridAutoRows, gridTemplateAreas, gridAutoFlow,
                gridColumnStart, gridColumnEnd, gridRowStart, gridRowEnd);
    }
}

package com.browicy.engine.render;

import java.util.List;

/**
 * Unveränderlicher Render-Style einer Box, zusammengesetzt aus sechs
 * logisch kohärenten Sub-Records: {@link BoxModelStyle}, {@link PositionStyle},
 * {@link TypographyStyle}, {@link ColorBackgroundStyle}, {@link FlexGridStyle}
 * und {@link EffectsUiStyle}.
 *
 * <p>Die bisherigen flachen Getter bleiben als Delegationsmethoden erhalten
 * (z.&nbsp;B. {@code style.display()} delegiert an
 * {@code style.box().display()}), damit Aufrufstellen in Layout- und
 * Paint-Engines unverändert weiterarbeiten. Schlanke {@code with*}-Methoden
 * kopieren nur den betroffenen Sub-Record statt des gesamten Stils.</p>
 *
 * <p>Validierung findet in den kompakten Konstruktoren der Sub-Records statt;
 * dieser Record selbst validiert nicht mehr.</p>
 */
public record RenderStyle(
        BoxModelStyle box,
        PositionStyle positionStyle,
        TypographyStyle typography,
        ColorBackgroundStyle colors,
        FlexGridStyle flexGrid,
        EffectsUiStyle effects) {

    public enum Display {
        BLOCK, INLINE, INLINE_BLOCK, FLEX, INLINE_FLEX, NONE, GRID, INLINE_GRID,
        TABLE, INLINE_TABLE, TABLE_ROW_GROUP, TABLE_HEADER_GROUP, TABLE_FOOTER_GROUP,
        TABLE_ROW, TABLE_CELL, TABLE_COLUMN_GROUP, TABLE_COLUMN, TABLE_CAPTION,
        CONTENTS
    }

    public enum GridAutoFlow { ROW, COLUMN, ROW_DENSE, COLUMN_DENSE }

    /**
     * Eine Grid-Linienposition: {@code line} ist die numerische Linie (0 = auto,
     * negative Werte zählen vom Ende), {@code span} eine Spanne ab auto-Platzierung
     * und {@code name} ein benannter Linien- bzw. Bereichsname (null = auto).
     */
    public record GridLine(int line, int span, String name) {
        public static final GridLine AUTO = new GridLine(0, 0, null);

        public boolean isAuto() {
            return line == 0 && span == 0 && name == null;
        }
    }

    public record GridTrack(Type type, float fixed, float fraction,
                            float minFixed, float maxFixed,
                            boolean minPercent, boolean maxPercent) {
        public enum Type { FIXED, PERCENT, FRACTION, AUTO, MINMAX }
    }

    public record TextShadow(CssColor color, float offsetX, float offsetY) {
    }
    public enum BorderCollapse { SEPARATE, COLLAPSE }
    public enum ListStyleType { DISC, CIRCLE, SQUARE, NONE }
    public enum BackgroundRepeat { REPEAT, REPEAT_X, REPEAT_Y, NO_REPEAT }
    public enum BackgroundPositionX { LEFT, CENTER, RIGHT }
    public enum BackgroundPositionY { TOP, CENTER, BOTTOM }
    public enum Position { STATIC, RELATIVE, ABSOLUTE, STICKY, FIXED }
    public enum Cursor {
        DEFAULT, POINTER, TEXT, GRABBING, NS_RESIZE, EW_RESIZE, N_RESIZE, S_RESIZE,
        E_RESIZE, W_RESIZE, NE_RESIZE, NW_RESIZE, SE_RESIZE, SW_RESIZE, CROSSHAIR,
        HELP, MOVE, NOT_ALLOWED, WAIT, PROGRESS, ZOOM_IN, ZOOM_OUT, CELL, COPY,
        NO_DROP, ALIAS, CONTEXT_MENU, VERTICAL_TEXT, ALL_SCROLL, COL_RESIZE, ROW_RESIZE
    }
    public enum FloatMode { NONE, LEFT, RIGHT }
    public enum Clear { NONE, LEFT, RIGHT, BOTH }
    public enum TextAlign { LEFT, CENTER, RIGHT }
    public enum TextTransform { NONE, UPPERCASE, LOWERCASE, CAPITALIZE }
    public enum BoxSizing { CONTENT_BOX, BORDER_BOX }
    public enum Overflow { VISIBLE, HIDDEN, AUTO, SCROLL }
    public enum VerticalAlign { BASELINE, TOP, MIDDLE, BOTTOM }
    public enum FlexDirection { ROW, ROW_REVERSE, COLUMN, COLUMN_REVERSE }
    public enum FlexWrap { NOWRAP, WRAP, WRAP_REVERSE }
    public enum JustifyContent { FLEX_START, CENTER, FLEX_END, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY }
    public enum AlignItems { STRETCH, FLEX_START, CENTER, FLEX_END, BASELINE }
    public enum AlignSelf { AUTO, STRETCH, FLEX_START, CENTER, FLEX_END, BASELINE }
    public enum AlignContent { NORMAL, FLEX_START, FLEX_END, CENTER, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY, STRETCH }
    public enum ObjectFit { FILL, CONTAIN, COVER, NONE, SCALE_DOWN }
    public enum TextOverflow { CLIP, ELLIPSIS }
    public enum WhiteSpace { NORMAL, NOWRAP, PRE, PRE_WRAP, PRE_LINE, BREAK_SPACES }

    /**
     * @return {@code true}, wenn das Schriftgewicht fett (≥ 600) ist
     */
    public boolean bold() {
        return typography.fontWeight() >= 600;
    }

    /**
     * @return die tatsächlich verwendete Zeilenhöhe in Pixeln; ein negativer
     *         {@code lineHeight}-Wert wird als Faktor der Schriftgröße aufgelöst
     */
    public float usedLineHeightPx() {
        return typography.lineHeight() < 0
                ? -typography.lineHeight() * typography.fontSizePx()
                : typography.lineHeight();
    }

    // ── Delegations-Getter (Box-Modell) ──────────────────────────────────────

    /** @return der Anzeigetyp der Box (delegiert an {@link #box()}) */
    public Display display() { return box.display(); }

    /** @return die Box-Sizing-Strategie (delegiert an {@link #box()}) */
    public BoxSizing boxSizing() { return box.boxSizing(); }

    /** @return die Breite (delegiert an {@link #box()}) */
    public RenderLength width() { return box.width(); }

    /** @return die Höhe (delegiert an {@link #box()}) */
    public RenderLength height() { return box.height(); }

    /** @return die Mindestbreite (delegiert an {@link #box()}) */
    public RenderLength minWidth() { return box.minWidth(); }

    /** @return die Maximalbreite (delegiert an {@link #box()}) */
    public RenderLength maxWidth() { return box.maxWidth(); }

    /** @return die Mindesthöhe (delegiert an {@link #box()}) */
    public RenderLength minHeight() { return box.minHeight(); }

    /** @return die Maximalhöhe (delegiert an {@link #box()}) */
    public RenderLength maxHeight() { return box.maxHeight(); }

    /** @return das Seitenverhältnis (NaN = nicht gesetzt; delegiert an {@link #box()}) */
    public float aspectRatio() { return box.aspectRatio(); }

    /** @return das Object-Fit-Verhalten (delegiert an {@link #box()}) */
    public ObjectFit objectFit() { return box.objectFit(); }

    /** @return die Außenabstände (delegiert an {@link #box()}) */
    public BoxEdges margin() { return box.margin(); }

    /** @return die automatischen horizontalen Ränder (delegiert an {@link #box()}) */
    public HorizontalAutoMargins autoMargins() { return box.autoMargins(); }

    /** @return die Innenabstände (delegiert an {@link #box()}) */
    public BoxEdges padding() { return box.padding(); }

    /** @return die Rahmenbreiten (delegiert an {@link #box()}) */
    public BoxEdges borderWidth() { return box.borderWidth(); }

    /** @return die Rahmenfarben (delegiert an {@link #box()}) */
    public BoxColors borderColor() { return box.borderColor(); }

    /** @return die Rahmenstile (delegiert an {@link #box()}) */
    public BoxBorders borderStyle() { return box.borderStyle(); }

    /** @return die Eckradien (delegiert an {@link #box()}) */
    public CornerRadii borderRadius() { return box.borderRadius(); }

    /** @return das Overflow-Verhalten (delegiert an {@link #box()}) */
    public Overflow overflow() { return box.overflow(); }

    /** @return die vertikale Ausrichtung (delegiert an {@link #box()}) */
    public VerticalAlign verticalAlign() { return box.verticalAlign(); }

    /** @return das Border-Collapse-Verhalten (delegiert an {@link #box()}) */
    public BorderCollapse borderCollapse() { return box.borderCollapse(); }

    // ── Delegations-Getter (Positionierung) ──────────────────────────────────

    /** @return der Positionsmodus (delegiert an {@link #positionStyle()}) */
    public Position position() { return positionStyle.position(); }

    /** @return der Z-Index (delegiert an {@link #positionStyle()}) */
    public int zIndex() { return positionStyle.zIndex(); }

    /** @return der Float-Modus (delegiert an {@link #positionStyle()}) */
    public FloatMode floatMode() { return positionStyle.floatMode(); }

    /** @return das Clear-Verhalten (delegiert an {@link #positionStyle()}) */
    public Clear clear() { return positionStyle.clear(); }

    /** @return der obere Offset (delegiert an {@link #positionStyle()}) */
    public RenderOffset top() { return positionStyle.top(); }

    /** @return der rechte Offset (delegiert an {@link #positionStyle()}) */
    public RenderOffset right() { return positionStyle.right(); }

    /** @return der untere Offset (delegiert an {@link #positionStyle()}) */
    public RenderOffset bottom() { return positionStyle.bottom(); }

    /** @return der linke Offset (delegiert an {@link #positionStyle()}) */
    public RenderOffset left() { return positionStyle.left(); }

    /** @return die Transformationsliste (delegiert an {@link #positionStyle()}) */
    public Transform transform() { return positionStyle.transform(); }

    // ── Delegations-Getter (Typografie) ──────────────────────────────────────

    /** @return die Schriftgröße in Pixeln (delegiert an {@link #typography()}) */
    public float fontSizePx() { return typography.fontSizePx(); }

    /** @return die Schriftfamilie (delegiert an {@link #typography()}) */
    public String fontFamily() { return typography.fontFamily(); }

    /** @return das Schriftgewicht (delegiert an {@link #typography()}) */
    public int fontWeight() { return typography.fontWeight(); }

    /** @return {@code true}, wenn kursiv (delegiert an {@link #typography()}) */
    public boolean italic() { return typography.italic(); }

    /** @return die Zeilenhöhe (delegiert an {@link #typography()}) */
    public float lineHeight() { return typography.lineHeight(); }

    /** @return die Textausrichtung (delegiert an {@link #typography()}) */
    public TextAlign textAlign() { return typography.textAlign(); }

    /** @return die Text-Transformation (delegiert an {@link #typography()}) */
    public TextTransform textTransform() { return typography.textTransform(); }

    /** @return das White-Space-Verhalten (delegiert an {@link #typography()}) */
    public WhiteSpace whiteSpace() { return typography.whiteSpace(); }

    /** @return der Buchstabenabstand in Pixeln (delegiert an {@link #typography()}) */
    public float letterSpacingPx() { return typography.letterSpacingPx(); }

    /** @return das Text-Overflow-Verhalten (delegiert an {@link #typography()}) */
    public TextOverflow textOverflow() { return typography.textOverflow(); }

    /** @return der Listenmarker-Typ (delegiert an {@link #typography()}) */
    public ListStyleType listStyleType() { return typography.listStyleType(); }

    // ── Delegations-Getter (Farben & Hintergrund) ────────────────────────────

    /** @return die Textfarbe (delegiert an {@link #colors()}) */
    public CssColor color() { return colors.color(); }

    /** @return {@code true}, wenn unterstrichen (delegiert an {@link #colors()}) */
    public boolean underline() { return colors.underline(); }

    /** @return {@code true}, wenn durchgestrichen (delegiert an {@link #colors()}) */
    public boolean lineThrough() { return colors.lineThrough(); }

    /** @return die Textdekorationsfarbe (delegiert an {@link #colors()}) */
    public CssColor textDecorationColor() { return colors.textDecorationColor(); }

    /** @return die Hintergrundfarbe (delegiert an {@link #colors()}) */
    public CssColor backgroundColor() { return colors.backgroundColor(); }

    /** @return die Hintergrundbild-URL (delegiert an {@link #colors()}) */
    public String backgroundImageUrl() { return colors.backgroundImageUrl(); }

    /** @return die Hintergrundwiederholung (delegiert an {@link #colors()}) */
    public BackgroundRepeat backgroundRepeat() { return colors.backgroundRepeat(); }

    /** @return die horizontale Hintergrundposition (delegiert an {@link #colors()}) */
    public BackgroundPositionX backgroundPositionX() { return colors.backgroundPositionX(); }

    /** @return die vertikale Hintergrundposition (delegiert an {@link #colors()}) */
    public BackgroundPositionY backgroundPositionY() { return colors.backgroundPositionY(); }

    /** @return der horizontale Positions-Offset (delegiert an {@link #colors()}) */
    public RenderLength backgroundPositionOffsetX() { return colors.backgroundPositionOffsetX(); }

    /** @return der vertikale Positions-Offset (delegiert an {@link #colors()}) */
    public RenderLength backgroundPositionOffsetY() { return colors.backgroundPositionOffsetY(); }

    /** @return die horizontale Hintergrundgröße (delegiert an {@link #colors()}) */
    public RenderLength backgroundSizeX() { return colors.backgroundSizeX(); }

    /** @return die vertikale Hintergrundgröße (delegiert an {@link #colors()}) */
    public RenderLength backgroundSizeY() { return colors.backgroundSizeY(); }

    /** @return die Deckkraft im Intervall [0, 1] (delegiert an {@link #colors()}) */
    public float opacity() { return colors.opacity(); }

    /** @return {@code true}, wenn die Box sichtbar ist (delegiert an {@link #colors()}) */
    public boolean visible() { return colors.visible(); }

    // ── Delegations-Getter (Flex & Grid) ─────────────────────────────────────

    /** @return die Flex-Richtung (delegiert an {@link #flexGrid()}) */
    public FlexDirection flexDirection() { return flexGrid.flexDirection(); }

    /** @return das Flex-Umbruchverhalten (delegiert an {@link #flexGrid()}) */
    public FlexWrap flexWrap() { return flexGrid.flexWrap(); }

    /** @return die Hauptachsen-Ausrichtung (delegiert an {@link #flexGrid()}) */
    public JustifyContent justifyContent() { return flexGrid.justifyContent(); }

    /** @return die Querachsen-Ausrichtung (delegiert an {@link #flexGrid()}) */
    public AlignItems alignItems() { return flexGrid.alignItems(); }

    /** @return die Selbst-Ausrichtung (delegiert an {@link #flexGrid()}) */
    public AlignSelf alignSelf() { return flexGrid.alignSelf(); }

    /** @return die Ausrichtung mehrerer Zeilen (delegiert an {@link #flexGrid()}) */
    public AlignContent alignContent() { return flexGrid.alignContent(); }

    /** @return die Reihenfolge im Flex-Container (delegiert an {@link #flexGrid()}) */
    public int order() { return flexGrid.order(); }

    /** @return der Flex-Wachstumsfaktor (delegiert an {@link #flexGrid()}) */
    public float flexGrow() { return flexGrid.flexGrow(); }

    /** @return der Flex-Schrumpfungsfaktor (delegiert an {@link #flexGrid()}) */
    public float flexShrink() { return flexGrid.flexShrink(); }

    /** @return die Flex-Basis (delegiert an {@link #flexGrid()}) */
    public RenderLength flexBasis() { return flexGrid.flexBasis(); }

    /** @return der Zeilenabstand in Pixeln (delegiert an {@link #flexGrid()}) */
    public float rowGapPx() { return flexGrid.rowGapPx(); }

    /** @return der Spaltenabstand in Pixeln (delegiert an {@link #flexGrid()}) */
    public float columnGapPx() { return flexGrid.columnGapPx(); }

    /** @return die Template-Spalten (delegiert an {@link #flexGrid()}) */
    public List<GridTrack> gridTemplateColumns() { return flexGrid.gridTemplateColumns(); }

    /** @return die Template-Zeilen (delegiert an {@link #flexGrid()}) */
    public List<GridTrack> gridTemplateRows() { return flexGrid.gridTemplateRows(); }

    /** @return die automatischen Spalten (delegiert an {@link #flexGrid()}) */
    public List<GridTrack> gridAutoColumns() { return flexGrid.gridAutoColumns(); }

    /** @return die automatischen Zeilen (delegiert an {@link #flexGrid()}) */
    public List<GridTrack> gridAutoRows() { return flexGrid.gridAutoRows(); }

    /** @return die benannten Template-Areas (delegiert an {@link #flexGrid()}) */
    public String[][] gridTemplateAreas() { return flexGrid.gridTemplateAreas(); }

    /** @return der Grid-Auto-Flow (delegiert an {@link #flexGrid()}) */
    public GridAutoFlow gridAutoFlow() { return flexGrid.gridAutoFlow(); }

    /** @return die Start-Spaltenlinie (delegiert an {@link #flexGrid()}) */
    public GridLine gridColumnStart() { return flexGrid.gridColumnStart(); }

    /** @return die End-Spaltenlinie (delegiert an {@link #flexGrid()}) */
    public GridLine gridColumnEnd() { return flexGrid.gridColumnEnd(); }

    /** @return die Start-Zeilenlinie (delegiert an {@link #flexGrid()}) */
    public GridLine gridRowStart() { return flexGrid.gridRowStart(); }

    /** @return die End-Zeilenlinie (delegiert an {@link #flexGrid()}) */
    public GridLine gridRowEnd() { return flexGrid.gridRowEnd(); }

    // ── Delegations-Getter (Effekte & UI) ────────────────────────────────────

    /** @return die Box-Schattenliste (delegiert an {@link #effects()}) */
    public List<BoxShadow> boxShadows() { return effects.boxShadows(); }

    /** @return der Textschatten (delegiert an {@link #effects()}) */
    public TextShadow textShadow() { return effects.textShadow(); }

    /** @return die Outline-Breite (delegiert an {@link #effects()}) */
    public float outlineWidth() { return effects.outlineWidth(); }

    /** @return die Outline-Farbe (delegiert an {@link #effects()}) */
    public CssColor outlineColor() { return effects.outlineColor(); }

    /** @return {@code true}, wenn die Outline sichtbar ist (delegiert an {@link #effects()}) */
    public boolean outlineVisible() { return effects.outlineVisible(); }

    /** @return der Outline-Offset (delegiert an {@link #effects()}) */
    public float outlineOffset() { return effects.outlineOffset(); }

    /** @return der Cursor (delegiert an {@link #effects()}) */
    public Cursor cursor() { return effects.cursor(); }

    /** @return {@code true}, wenn Pointer-Events aktiv sind (delegiert an {@link #effects()}) */
    public boolean pointerEvents() { return effects.pointerEvents(); }

    // ── Schlanke with-Methoden ───────────────────────────────────────────────

    /**
     * Kopie dieses Stils mit geändertem {@code display}; nur der
     * {@link BoxModelStyle}-Sub-Record wird kopiert.
     *
     * @param value neuer Anzeigetyp
     * @return neue Instanz mit aktualisiertem {@code display}
     */
    public RenderStyle withDisplay(Display value) {
        return new RenderStyle(box.withDisplay(value), positionStyle, typography,
                colors, flexGrid, effects);
    }

    /**
     * Kopie dieses Stils mit geänderter {@code width}; nur der
     * {@link BoxModelStyle}-Sub-Record wird kopiert.
     *
     * @param value neue Breite
     * @return neue Instanz mit aktualisierter {@code width}
     */
    public RenderStyle withWidth(RenderLength value) {
        return new RenderStyle(box.withWidth(value), positionStyle, typography,
                colors, flexGrid, effects);
    }

    /**
     * Kopie dieses Stils mit geänderter {@code height}; nur der
     * {@link BoxModelStyle}-Sub-Record wird kopiert.
     *
     * @param value neue Höhe
     * @return neue Instanz mit aktualisierter {@code height}
     */
    public RenderStyle withHeight(RenderLength value) {
        return new RenderStyle(box.withHeight(value), positionStyle, typography,
                colors, flexGrid, effects);
    }

    /**
     * Kopie dieses Stils mit geändertem {@code flexGrow}; nur der
     * {@link FlexGridStyle}-Sub-Record wird kopiert.
     *
     * @param value neuer Flex-Wachstumsfaktor
     * @return neue Instanz mit aktualisiertem {@code flexGrow}
     */
    public RenderStyle withFlexGrow(float value) {
        return new RenderStyle(box, positionStyle, typography, colors,
                flexGrid.withFlexGrow(value), effects);
    }
}

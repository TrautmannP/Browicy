package com.browicy.ui.render;

import com.browicy.engine.render.RenderStyle;
import java.util.ArrayList;
import java.util.List;

/** Die Float-Exclusions eines Block-Formatierungskontexts (CSS2.1 §9.5.1).
 *  Hält die Float-Regionen eines BFC und beantwortet die Platzierungsfragen
 *  der In-Flow-Boxen: Wie breit ist der freie Bereich auf Zeilenhöhe y
 *  ({@link #floatArea(float, float, float)}), ab welcher Höhe ist der
 *  Kontext frei ({@link #clearedY}), und passt eine minimale Breite auf der
 *  Zeile ({@link #dropBelowFloatsIfNarrow(float, float, float, float)}).
 *
 *  <p>Ein neuer BFC bekommt eine eigene Instanz (sie isoliert ihre Floats);
 *  Nicht-BFC-Kinder teilen die Instanz ihres BFC (gemeinsame, mutierbare
 *  Liste – §9.5.1: Normale In-Flow-Blockboxen fließen "als gäbe es den
 *  Float nicht" und reichen die aktive Float-Liste unverändert weiter).
 *  Deshalb wird die Content-Origin (contentX/contentWidth) des <em>aktuellen
 *  Containers</em> jeder Abfrage mitgegeben – bei geteilten Spaces weicht
 *  sie vom Erstellungsort des BFC ab.
 *
 *  <p>Früher Teil von RenderLayoutEngine; Verhalten unverändert übernommen
 *  (Refactoring ohne Diff).
 */
final class FloatExclusionSpace {

    private final List<FloatRegion> floats = new ArrayList<>();

    FloatExclusionSpace() {
    }

    boolean isEmpty() {
        return floats.isEmpty();
    }

    void add(FloatRegion region) {
        floats.add(region);
    }

    List<FloatRegion> regions() {
        return floats;
    }

    /** Freie Zeilenbreite auf Höhe y: schmalste horizontale Lücke zwischen
     *  linken und rechten Floats, die y überspannen. Mindestens 1px, damit
     *  Platzierungslogik nie durch 0 dividiert. contentX/contentWidth sind
     *  die des aktuellen Containers – bei geteilten Spaces (Nicht-BFC-Kinder
     *  teilen den Space ihres BFC) können sie vom Erstellungsort abweichen. */
    FloatArea floatArea(float contentX, float contentWidth, float y) {
        float left = contentX;
        float right = contentX + contentWidth;
        for (FloatRegion region : floats) {
            if (y < region.y() || y >= region.y() + region.height()) continue;
            if (region.mode() == RenderStyle.FloatMode.LEFT) {
                left = Math.max(left, region.x() + region.width());
            } else {
                right = Math.min(right, region.x());
            }
        }
        return new FloatArea(left, Math.max(1, right - left));
    }

    /** Sinkt y unter die Floats ab, falls die freie Zeilenbreite bei y die
     *  Mindestbreite unterschreitet (Zeilenboxen weichen Floats aus). */
    float dropBelowFloatsIfNarrow(float contentX, float contentWidth,
                                  float y, float minimumWidth) {
        if (floats.isEmpty() || minimumWidth <= 0) {
            return y;
        }
        if (floatArea(contentX, contentWidth, y).width() >= Math.max(1, minimumWidth)) {
            return y;
        }
        return clearedY(y, RenderStyle.Clear.BOTH);
    }

    /** Kleinste Höhe ≥ y, unter der die Floats der geforderten Seite(n) frei
     *  sind (CSS2.1 §9.5.2 clear). */
    float clearedY(float y, RenderStyle.Clear clear) {
        if (clear == RenderStyle.Clear.NONE) return y;
        float result = y;
        for (FloatRegion region : floats) {
            boolean applies = clear == RenderStyle.Clear.BOTH
                    || clear == RenderStyle.Clear.LEFT
                            && region.mode() == RenderStyle.FloatMode.LEFT
                    || clear == RenderStyle.Clear.RIGHT
                            && region.mode() == RenderStyle.FloatMode.RIGHT;
            if (applies && region.y() + region.height() > result) {
                result = region.y() + region.height();
            }
        }
        return result;
    }

    /** Äußere Box (x, y, width, height) eines Floats dieses BFC. */
    record FloatRegion(RenderStyle.FloatMode mode,
                       float x,
                       float y,
                       float width,
                       float height) {
    }

    /** Freier Zeilenbereich auf einer Höhe y: x = linke Kante, width =
     *  verfügbare Breite rechts davon. */
    record FloatArea(float x, float width) {
    }
}

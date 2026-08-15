package com.browicy.ui.render;

import com.browicy.engine.render.RenderStyle;
import java.util.ArrayList;
import java.util.List;

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

    float firstFitY(float contentX, float contentWidth, float y, float minimumWidth) {
        if (floats.isEmpty() || minimumWidth <= 0) {
            return y;
        }
        float current = y;
        while (true) {
            if (floatArea(contentX, contentWidth, current).width()
                    >= Math.max(1, minimumWidth)) {
                return current;
            }
            float next = Float.POSITIVE_INFINITY;
            for (FloatRegion region : floats) {
                if (current < region.y() || current >= region.y() + region.height()) continue;
                next = Math.min(next, region.y() + region.height());
            }
            if (!Float.isFinite(next) || next <= current) {
                return current;
            }
            current = next;
        }
    }

    LineSlot lineSlot(float contentX, float contentWidth, float y,
                      float minHeight, float minWidth) {
        if (floats.isEmpty()) {
            return new LineSlot(y, contentX, contentWidth);
        }
        float minW = Math.max(1, minWidth);
        float current = y;
        while (true) {
            float[][] occupied = new float[floats.size()][2];
            int count = 0;
            for (FloatRegion region : floats) {
                if (current >= region.y() + region.height()
                        || current + minHeight <= region.y()) {
                    continue;
                }
                occupied[count][0] = region.x();
                occupied[count][1] = region.x() + region.width();
                count++;
            }
            if (count > 0) {
                java.util.Arrays.sort(occupied, 0, count,
                        (a, b) -> Float.compare(a[0], b[0]));
            }
            float freeStart = contentX;
            float freeWidth = 0;
            float mergedEnd = contentX;
            for (int index = 0; index < count; index++) {
                float start = occupied[index][0];
                float end = occupied[index][1];
                if (start <= mergedEnd) {
                    mergedEnd = Math.max(mergedEnd, end);
                    continue;
                }
                float gap = start - mergedEnd;
                if (gap >= minW) {
                    freeStart = mergedEnd;
                    freeWidth = gap;
                    break;
                }
                mergedEnd = end;
            }
            if (freeWidth == 0) {
                float gap = contentX + contentWidth - mergedEnd;
                if (gap >= minW) {
                    freeStart = mergedEnd;
                    freeWidth = gap;
                }
            }
            if (freeWidth >= minW) {
                return new LineSlot(current, freeStart, freeWidth);
            }
            float next = Float.POSITIVE_INFINITY;
            for (FloatRegion region : floats) {
                if (current >= region.y() + region.height()
                        || current + minHeight <= region.y()) {
                    continue;
                }
                if (region.y() > current) {
                    next = Math.min(next, region.y());
                }
                next = Math.min(next, region.y() + region.height());
            }
            if (!Float.isFinite(next) || next <= current) {
                return new LineSlot(current, freeStart, Math.max(1, freeWidth));
            }
            current = next;
        }
    }

    record LineSlot(float y, float x, float width) {
    }

    record FloatRegion(RenderStyle.FloatMode mode,
                       float x,
                       float y,
                       float width,
                       float height) {
    }

    record FloatArea(float x, float width) {
    }
}

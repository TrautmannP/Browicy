package com.browicy.ui.render;

import com.browicy.engine.render.RenderBox;
import com.browicy.engine.render.RenderNode;
import com.browicy.engine.render.RenderStyle;
import com.browicy.ui.render.PositionedLayout.AbsoluteRequest;
import com.browicy.ui.render.RenderLayoutEngine.BlockLayout;
import com.browicy.ui.render.RenderLayoutEngine.LineBox;
import com.browicy.ui.render.RenderLayoutEngine.PaintFragment;
import com.browicy.ui.render.PositionedLayout.PositionedContext;
import static com.browicy.ui.render.FlexLayoutEngine.forceOuterHeight;
import static com.browicy.ui.render.PositionedLayout.fragmentZIndex;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Grid-Layout (CSS Grid Layout): explizite und implizite Tracks
 * ({@code grid-template-columns/-rows}, {@code grid-auto-columns/-rows}),
 * Auto-Flow-Platzierung ({@code grid-auto-flow}) inklusive dichtem Auffüllen,
 * benannte Grid-Areas und Linien sowie die Verteilung von Freiraum auf
 * fr-Anteile und MINMAX-Spuren.
 *
 * <p>Eingaben sind der Grid-Container, die Content-Box-Geometrie und der
 * Positioned-Kontext; Ausgabe ist ein {@link GridLayout} mit Höhe sowie
 * normalen und über Z-Index angehobenen Fragmenten. Der {@link Host} liefert
 * den Block-Layout-Dienst des {@link RenderLayoutEngine} für die Grid-Items
 * als minimale Callback-Schnittstelle.
 */
final class GridLayoutEngine {

    interface Host {
        BlockLayout layoutBlock(RenderBox box, float containingX, float y, float availableWidth,
                                Float containingHeight, boolean shrinkToFitAuto,
                                Graphics2D graphics, List<LineBox> lineBoxes,
                                PositionedContext positionedContext);
    }

    private final Host host;

    GridLayoutEngine(Host host) {
        this.host = java.util.Objects.requireNonNull(host, "host");
    }

    GridLayout layoutGrid(RenderBox container, float contentX, float contentY,
                                  float contentWidth, Float contentHeight, Graphics2D graphics,
                                  List<LineBox> lineBoxes, PositionedContext positionedContext) {
        List<RenderBox> items = new ArrayList<>();
        for (RenderNode child : container.children()) {
            if (!(child instanceof RenderBox item)) {
                continue;
            }
            if (item.style().position() == RenderStyle.Position.ABSOLUTE
                    || item.style().position() == RenderStyle.Position.FIXED) {
                positionedContext.requests.add(new AbsoluteRequest(item, contentX, contentY));
            } else {
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            return new GridLayout(0, List.of());
        }
        RenderStyle style = container.style();
        List<RenderStyle.GridTrack> columns = style.gridTemplateColumns();
        List<RenderStyle.GridTrack> rows = style.gridTemplateRows();
        boolean columnFlow = style.gridAutoFlow() == RenderStyle.GridAutoFlow.COLUMN
                || style.gridAutoFlow() == RenderStyle.GridAutoFlow.COLUMN_DENSE;
        int rowCapacity = rows.isEmpty() ? 1 : rows.size();
        int requiredColumns = columnFlow
                ? Math.max(1, (items.size() + rowCapacity - 1) / rowCapacity) : 1;
        if (columns.isEmpty()) {
            columns = implicitTracks(style.gridAutoColumns(), requiredColumns);
        } else if (columnFlow && columns.size() < requiredColumns) {
            List<RenderStyle.GridTrack> expanded = new ArrayList<>(columns);
            expanded.addAll(implicitTracks(style.gridAutoColumns(), requiredColumns - columns.size()));
            columns = List.copyOf(expanded);
        }
        if (rows.isEmpty() && columnFlow) {
            rows = implicitTracks(style.gridAutoRows(), rowCapacity);
        }
        int columnCount = columns.size();
        float columnGap = style.columnGapPx();
        float rowGap = style.rowGapPx();
        String[][] areas = style.gridTemplateAreas();

        int rowsUsed = 0;
        java.util.Set<String> occupied = new java.util.HashSet<>();
        List<GridPlacement> placements = new ArrayList<>();
        List<RenderBox> placed = new ArrayList<>();
        for (RenderBox item : items) {
            RenderStyle itemStyle = item.style();
            RenderStyle.GridLine colStartLine = itemStyle.gridColumnStart();
            RenderStyle.GridLine colEndLine = itemStyle.gridColumnEnd();
            RenderStyle.GridLine rowStartLine = itemStyle.gridRowStart();
            RenderStyle.GridLine rowEndLine = itemStyle.gridRowEnd();
            int startCol = resolveGridLine(colStartLine, areas, false, false, columnCount);
            int endCol = resolveGridLine(colEndLine, areas, false, true, columnCount);
            int startRow = resolveGridLine(rowStartLine, areas, true, false, rowCapacity);
            int endRow = resolveGridLine(rowEndLine, areas, true, true, rowCapacity);
            int spanCol = colStartLine.span() > 0 ? colStartLine.span()
                    : (colEndLine.span() > 0 ? colEndLine.span() : 1);
            int spanRow = rowStartLine.span() > 0 ? rowStartLine.span()
                    : (rowEndLine.span() > 0 ? rowEndLine.span() : 1);
            if (startCol > 0 && endCol > 0) {
                spanCol = Math.max(1, endCol - startCol);
            }
            if (startRow > 0 && endRow > 0) {
                spanRow = Math.max(1, endRow - startRow);
            }
            if (startCol == 0 && endCol > 0) {
                startCol = Math.max(1, endCol - spanCol);
            }
            if (startRow == 0 && endRow > 0) {
                startRow = Math.max(1, endRow - spanRow);
            }
            if (startCol == 0 && startRow == 0) {
                int[] slot = findFreeCell(occupied, columnCount, spanCol, spanRow, startRow,
                        rowCapacity, columnFlow);
                startRow = slot[0] + 1;
                startCol = slot[1] + 1;
            } else {
                if (startCol == 0) {
                    startCol = 1;
                }
                if (startRow == 0) {
                    startRow = 1;
                }
            }
            for (int r = startRow; r < startRow + spanRow; r++) {
                for (int c = startCol; c < startCol + spanCol; c++) {
                    occupied.add(r + ":" + c);
                }
            }
            placements.add(new GridPlacement(startRow, startCol, spanRow, spanCol));
            placed.add(item);
            rowsUsed = Math.max(rowsUsed, startRow + spanRow - 1);
        }
        int rowCount = Math.max(rowsUsed, rows.isEmpty() ? rowsUsed : Math.max(rowsUsed, rows.size()));

        float[] columnWidths = new float[columnCount];
        float used = columnGap * Math.max(0, columnCount - 1);
        float[] fractions = new float[columnCount];
        float fractionSum = 0;
        List<Integer> growable = new ArrayList<>();
        for (int column = 0; column < columnCount; column++) {
            RenderStyle.GridTrack track = columns.get(column);
            columnWidths[column] = trackBase(track, contentWidth);
            used += columnWidths[column];
            float fraction = trackFraction(track);
            if (fraction > 0) {
                fractions[column] = fraction;
                fractionSum += fraction;
            } else if (track.type() == RenderStyle.GridTrack.Type.MINMAX) {
                float maxPx = track.maxPercent()
                        ? track.maxFixed() / 100f * contentWidth : track.maxFixed();
                if (maxPx > columnWidths[column]) {
                    growable.add(column);
                }
            }
        }
        float free = Math.max(0, contentWidth - used);
        while (free > 0 && !growable.isEmpty()) {
            float smallestHeadroom = Float.MAX_VALUE;
            for (int column : growable) {
                RenderStyle.GridTrack track = columns.get(column);
                float maxPx = track.maxPercent()
                        ? track.maxFixed() / 100f * contentWidth : track.maxFixed();
                float headroom = maxPx - columnWidths[column];
                if (headroom < smallestHeadroom) {
                    smallestHeadroom = headroom;
                }
            }
            if (smallestHeadroom <= 0) {
                break;
            }
            float delta = Math.min(free / growable.size(), smallestHeadroom);
            for (int column : growable) {
                columnWidths[column] += delta;
            }
            free -= delta * growable.size();
            List<RenderStyle.GridTrack> columnTracks = columns;
            growable.removeIf(column -> {
                RenderStyle.GridTrack track = columnTracks.get(column);
                float maxPx = track.maxPercent()
                        ? track.maxFixed() / 100f * contentWidth : track.maxFixed();
                return columnWidths[column] >= maxPx - 0.001f;
            });
        }
        for (int column = 0; column < columnCount; column++) {
            if (fractions[column] > 0) {
                columnWidths[column] = Math.max(columnWidths[column],
                        fractions[column] / fractionSum * free);
            }
        }

        float[] rowHeights = new float[rowCount];
        for (int row = 0; row < rowCount; row++) {
            rowHeights[row] = 0;
        }
        float[] provisional = new float[rowCount];
        for (int index = 0; index < placed.size(); index++) {
            RenderBox item = placed.get(index);
            GridPlacement placement = placements.get(index);
            float cellWidth = columnWidths[placement.column() - 1];
            for (int c = 1; c < placement.columnSpan(); c++) {
                cellWidth += columnWidths[placement.column() - 1 + c] + columnGap;
            }
            float itemWidth = Math.max(1, cellWidth - item.style().margin().horizontal());
            float rowY = rowOffset(provisional, placement.row() - 1, rowGap, 0);
            float x = columnOffset(columnWidths, placement.column() - 1, columnGap, contentX);
            BlockLayout itemLayout = host.layoutBlock(item, x,
                    contentY + rowY, itemWidth, null,
                    false, graphics, lineBoxes, positionedContext);
            float itemHeight = itemLayout.outerHeight();
            for (int r = 0; r < placement.rowSpan(); r++) {
                provisional[placement.row() - 1 + r] = Math.max(
                        provisional[placement.row() - 1 + r], itemHeight);
            }
        }
        float fixedRows = 0;
        float rowFractionSum = 0;
        float[] rowFractions = new float[rowCount];
        for (int row = 0; row < rowCount; row++) {
            if (row < rows.size()) {
                RenderStyle.GridTrack track = rows.get(row);
                float fraction = trackFraction(track);
                if (fraction > 0) {
                    rowFractions[row] = fraction;
                    rowFractionSum += fraction;
                } else if (track.type() == RenderStyle.GridTrack.Type.FIXED
                        || track.type() == RenderStyle.GridTrack.Type.PERCENT) {
                    rowHeights[row] = trackBase(track, contentHeight == null ? 0 : contentHeight);
                    fixedRows += rowHeights[row];
                } else {
                    rowHeights[row] = provisional[row];
                    fixedRows += rowHeights[row];
                }
            } else {
                rowHeights[row] = provisional[row];
                fixedRows += rowHeights[row];
            }
        }
        float rowFree = contentHeight == null ? 0
                : Math.max(0, contentHeight - fixedRows
                        - rowGap * Math.max(0, rowCount - 1));
        for (int row = 0; row < rowCount; row++) {
            if (rowFractions[row] > 0) {
                rowHeights[row] = rowFractions[row] / rowFractionSum * rowFree;
            }
        }

        List<List<PaintFragment>> itemGroups = new ArrayList<>();
        float totalHeight = rowGap * Math.max(0, rowCount - 1);
        for (int row = 0; row < rowCount; row++) {
            totalHeight += rowHeights[row];
        }
        for (int index = 0; index < placed.size(); index++) {
            RenderBox item = placed.get(index);
            GridPlacement placement = placements.get(index);
            float cellWidth = columnWidths[placement.column() - 1];
            for (int c = 1; c < placement.columnSpan(); c++) {
                cellWidth += columnWidths[placement.column() - 1 + c] + columnGap;
            }
            float cellHeight = rowHeights[placement.row() - 1];
            for (int r = 1; r < placement.rowSpan(); r++) {
                cellHeight += rowHeights[placement.row() - 1 + r] + rowGap;
            }
            float x = columnOffset(columnWidths, placement.column() - 1, columnGap, contentX);
            float y = rowOffset(rowHeights, placement.row() - 1, rowGap, contentY);
            float itemWidth = Math.max(1, cellWidth - item.style().margin().horizontal());
            boolean stretch = item.style().height().isAuto()
                    && placement.rowSpan() == 1;
            RenderBox itemToLayout = item;
            if (stretch) {
                itemToLayout = FlexLayoutEngine.forceOuterHeight(item, cellHeight);
            }
            BlockLayout itemLayout = host.layoutBlock(itemToLayout, x,
                    y, itemWidth, stretch ? cellHeight : null,
                    false, graphics, lineBoxes, positionedContext);
            itemGroups.add(itemLayout.fragments());
        }
        itemGroups.sort(java.util.Comparator.comparingInt(group -> fragmentZIndex(group.getFirst())));
        List<PaintFragment> fragments = new ArrayList<>();
        List<PaintFragment> elevated = new ArrayList<>();
        for (List<PaintFragment> group : itemGroups) {
            if (fragmentZIndex(group.getFirst()) > 0) {
                elevated.addAll(group);
            } else {
                fragments.addAll(group);
            }
        }
        return new GridLayout(contentHeight == null ? totalHeight
                : Math.max(totalHeight, contentHeight), List.copyOf(fragments),
                List.copyOf(elevated));
    }

    private static float trackBase(RenderStyle.GridTrack track, float contentWidth) {
        return switch (track.type()) {
            case FIXED -> track.fixed();
            case PERCENT -> track.fixed() / 100f * contentWidth;
            case MINMAX -> track.minPercent()
                    ? track.minFixed() / 100f * contentWidth : track.minFixed();
            default -> 0;
        };
    }

    private static float trackFraction(RenderStyle.GridTrack track) {
        if (track.type() == RenderStyle.GridTrack.Type.FRACTION) {
            return track.fraction();
        }
        if (track.type() == RenderStyle.GridTrack.Type.MINMAX
                && track.maxFixed() < 0) {
            return -track.maxFixed();
        }
        return 0;
    }

    private static float columnOffset(float[] widths, int start, float gap, float origin) {
        float offset = origin;
        for (int column = 0; column < start; column++) {
            offset += widths[column] + gap;
        }
        return offset;
    }

    private static float rowOffset(float[] heights, int start, float gap, float origin) {
        float offset = origin;
        for (int row = 0; row < start; row++) {
            offset += heights[row] + gap;
        }
        return offset;
    }

    private static List<RenderStyle.GridTrack> implicitTracks(
            List<RenderStyle.GridTrack> pattern, int count) {
        RenderStyle.GridTrack fallback = new RenderStyle.GridTrack(
                RenderStyle.GridTrack.Type.AUTO, 0, 0, 0, 0, false, false);
        List<RenderStyle.GridTrack> source = pattern.isEmpty() ? List.of(fallback) : pattern;
        List<RenderStyle.GridTrack> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(source.get(index % source.size()));
        }
        return List.copyOf(result);
    }

    private static int[] findFreeCell(java.util.Set<String> occupied, int columnCount,
                                      int spanCol, int spanRow, int startRow,
                                      int rowCapacity, boolean columnFlow) {
        int row = Math.max(0, startRow - 1);
        if (columnFlow) {
            for (int column = 0; column < columnCount; column++) {
                for (int candidateRow = 0; candidateRow < rowCapacity; candidateRow++) {
                    if (cellIsFree(occupied, candidateRow, column, spanCol, spanRow)) {
                        return new int[] {candidateRow, column};
                    }
                }
            }
            return new int[] {0, columnCount};
        }
        while (true) {
            for (int column = 0; column < columnCount; column++) {
                if (cellIsFree(occupied, row, column, spanCol, spanRow)) {
                    return new int[] {row, column};
                }
            }
            row++;
        }
    }

    private static boolean cellIsFree(java.util.Set<String> occupied,
                                      int row, int column, int spanCol, int spanRow) {
        for (int r = row; r < row + spanRow; r++) {
            for (int c = column; c < column + spanCol; c++) {
                if (occupied.contains((r + 1) + ":" + (c + 1))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int[] findArea(String[][] areas, String name) {
        for (int row = 0; row < areas.length; row++) {
            for (int column = 0; column < areas[row].length; column++) {
                if (name.equals(areas[row][column])) {
                    return new int[] {row, column};
                }
            }
        }
        return null;
    }

    private static int resolveGridLine(RenderStyle.GridLine line, String[][] areas,
                                       boolean vertical, boolean endSide, int trackCount) {
        if (line == null) {
            return 0;
        }
        if (line.name() != null) {
            return namedGridLine(line.name(), areas, vertical, endSide);
        }
        int value = line.line();
        if (value > 0) {
            return value;
        }
        if (value < 0) {
            return Math.max(0, trackCount + value + 1);
        }
        return 0;
    }

    private static int namedGridLine(String name, String[][] areas,
                                     boolean vertical, boolean endSide) {
        if (areas == null) {
            return 0;
        }
        int line = areaLineForName(name, areas, vertical, endSide);
        if (line != 0) {
            return line;
        }
        String implicit = endSide ? name + "-end" : name + "-start";
        return areaLineForName(implicit, areas, vertical, endSide);
    }

    private static int areaLineForName(String name, String[][] areas,
                                       boolean vertical, boolean endSide) {
        for (int row = 0; row < areas.length; row++) {
            for (int column = 0; column < areas[row].length; column++) {
                if (!name.equals(areas[row][column])) {
                    continue;
                }
                int[] extent = areaExtent(areas, row, column, name);
                int start = vertical ? row + 1 : column + 1;
                int end = vertical ? row + extent[0] + 1 : column + extent[1] + 1;
                return endSide ? end : start;
            }
        }
        return 0;
    }

    private static int[] areaExtent(String[][] areas, int row, int column, String name) {
        int rows = 1;
        int columns = 1;
        while (row + rows < areas.length && areas[row + rows][column].equals(name)) {
            rows++;
        }
        while (column + columns < areas[row].length && areas[row][column + columns].equals(name)) {
            columns++;
        }
        return new int[] {rows, columns};
    }

    record GridLayout(float height, List<PaintFragment> fragments,
                      List<PaintFragment> elevated) {
        private GridLayout(float height, List<PaintFragment> fragments) {
            this(height, fragments, List.of());
        }
    }

    private record GridPlacement(int row, int column, int rowSpan, int columnSpan) {
    }
}

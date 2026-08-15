package com.browicy.ui.render;

import com.browicy.engine.render.BoxEdges;
import com.browicy.engine.render.RenderBox;
import com.browicy.engine.render.RenderLength;
import com.browicy.engine.render.RenderNode;
import com.browicy.engine.render.RenderStyle;
import com.browicy.ui.render.PositionedLayout.PositionedContext;
import com.browicy.ui.render.RenderLayoutEngine.BlockLayout;
import com.browicy.ui.render.RenderLayoutEngine.BoxFragment;
import com.browicy.ui.render.RenderLayoutEngine.IntrinsicWidths;
import com.browicy.ui.render.RenderLayoutEngine.LineBox;
import com.browicy.ui.render.RenderLayoutEngine.PaintFragment;

import static com.browicy.ui.render.RenderLayoutEngine.contentBoxDimension;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

final class TableLayout {

    interface Host {
        BlockLayout layoutBlock(RenderBox box, float containingX, float y, float availableWidth,
                                Float containingHeight, boolean shrinkToFitAuto,
                                Graphics2D graphics, List<LineBox> lineBoxes,
                                PositionedContext positionedContext);

        IntrinsicWidths intrinsicBoxWidth(RenderBox box, float percentageBase,
                                          Graphics2D graphics, boolean contentBased);

        float resolve(RenderLength length, float percentageBase);

        Float resolveContentHeight(RenderStyle style, RenderLength length,
                                   Float containingHeight, float decoration);
    }

    private final Host host;

    TableLayout(Host host) {
        this.host = java.util.Objects.requireNonNull(host, "host");
    }

    BlockLayout layoutTable(RenderBox table,
                            float containingX,
                            float y,
                            float availableWidth,
                            float percentageBase,
                            Float containingHeight,
                            Graphics2D graphics,
                            List<LineBox> lineBoxes,
                            PositionedContext positionedContext) {
        RenderStyle style = table.style();
        BoxEdges margin = style.margin();
        BoxEdges padding = style.padding();
        BoxEdges border = style.borderWidth();
        float decoration = border.horizontal() + padding.horizontal();
        float verticalDecoration = border.vertical() + padding.vertical();
        float availableContentWidth = Math.max(1,
                availableWidth - margin.horizontal() - decoration);
        List<TableRow> rows = tableRows(table);
        if (rows.isEmpty()) {
            RenderBox anonymousTable = new RenderBox(table.source(),
                    style.withDisplay(RenderStyle.Display.BLOCK), table.children());
            return host.layoutBlock(anonymousTable, containingX, y, availableWidth,
                    containingHeight, false, graphics, lineBoxes, positionedContext);
        }
        int columnCount = rows.stream().mapToInt(row -> row.cells().size()).max().orElse(0);
        float[] preferred = new float[columnCount];
        float[] minimum = new float[columnCount];
        for (TableRow row : rows) {
            for (int column = 0; column < row.cells().size(); column++) {
                IntrinsicWidths intrinsic = host.intrinsicBoxWidth(
                        row.cells().get(column), availableContentWidth, graphics, false);
                preferred[column] = Math.max(preferred[column], intrinsic.preferred());
                minimum[column] = Math.max(minimum[column], intrinsic.minimum());
            }
        }
        float specifiedWidth = style.width().isAuto()
                || style.width().unit() == RenderLength.Unit.MAX_CONTENT
                || style.width().unit() == RenderLength.Unit.MIN_CONTENT
                ? Float.NaN
                : contentBoxDimension(style, host.resolve(style.width(), percentageBase), decoration);
        float targetWidth = Float.isNaN(specifiedWidth)
                ? Math.min(sum(preferred), availableContentWidth)
                : Math.max(0, specifiedWidth);
        targetWidth = Math.max(targetWidth, sum(minimum));
        float[] columnWidths = fitColumns(preferred, minimum, targetWidth);
        float contentWidth = sum(columnWidths);
        float borderBoxWidth = contentWidth + decoration;
        float freeWidth = Math.max(0,
                availableWidth - borderBoxWidth - margin.horizontal());
        float automaticLeft = style.autoMargins().left()
                ? (style.autoMargins().right() ? freeWidth / 2f : freeWidth)
                : 0;
        float borderX = containingX + margin.left() + automaticLeft;
        float borderY = y + margin.top();
        float contentX = borderX + border.left() + padding.left();
        float contentTop = borderY + border.top() + padding.top();
        float currentY = contentTop;
        float[] naturalRowHeights = new float[rows.size()];
        List<List<List<PaintFragment>>> rowCellFragments = new ArrayList<>(rows.size());
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            TableRow row = rows.get(rowIndex);
            List<List<PaintFragment>> cellFragments = new ArrayList<>();
            float rowHeight = 0;
            float cellX = contentX;
            for (int column = 0; column < row.cells().size(); column++) {
                RenderBox cell = row.cells().get(column);
                BlockLayout cellLayout = host.layoutBlock(
                        cell, cellX, currentY, columnWidths[column], containingHeight,
                        false, graphics, lineBoxes, positionedContext);
                cellFragments.add(new ArrayList<>(cellLayout.fragments()));
                rowHeight = Math.max(rowHeight, cellLayout.outerHeight());
                cellX += columnWidths[column];
            }
            naturalRowHeights[rowIndex] = rowHeight;
            rowCellFragments.add(cellFragments);
            currentY += rowHeight;
        }
        float naturalContentHeight = Math.max(0, currentY - contentTop);
        float contentHeight = naturalContentHeight;
        Float specifiedContentHeight = host.resolveContentHeight(
                style, style.height(), containingHeight, verticalDecoration);
        if (specifiedContentHeight != null) {
            contentHeight = Math.max(contentHeight, specifiedContentHeight);
        }
        float extra = Math.max(0, contentHeight - naturalContentHeight);
        float[] rowHeights = distributeRowHeight(extra, naturalRowHeights);
        List<PaintFragment> children = new ArrayList<>();

        currentY = contentTop;
        RenderBox currentGroup = null;
        float groupY = contentTop;
        int groupInsertAt = -1;
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            TableRow row = rows.get(rowIndex);
            if (row.group() != currentGroup) {
                if (currentGroup != null) {
                    children.set(groupInsertAt, new BoxFragment(
                            currentGroup, contentX, groupY, contentWidth, currentY - groupY));
                }
                currentGroup = row.group();
                groupY = currentY;
                if (currentGroup != null) {
                    groupInsertAt = children.size();
                    children.add(null);
                }
            }
            float rowHeight = rowHeights[rowIndex];
            children.add(new BoxFragment(row.box(), contentX, currentY, contentWidth, rowHeight));
            List<List<PaintFragment>> cellFragments = rowCellFragments.get(rowIndex);
            for (int column = 0; column < cellFragments.size(); column++) {
                List<PaintFragment> fragments = cellFragments.get(column);
                BoxFragment cellRoot = (BoxFragment) fragments.getFirst();
                fragments.set(0, new BoxFragment(cellRoot.box(), cellRoot.x(), cellRoot.y(),
                        columnWidths[column], Math.max(cellRoot.height(), rowHeight),
                        cellRoot.clip(), cellRoot.transform()));
                children.addAll(fragments);
            }
            currentY += rowHeight;
        }
        if (currentGroup != null) {
            children.set(groupInsertAt, new BoxFragment(
                    currentGroup, contentX, groupY, contentWidth, currentY - groupY));
        }
        float borderBoxHeight = border.vertical() + padding.vertical() + contentHeight;
        List<PaintFragment> fragments = new ArrayList<>(children.size() + 1);
        fragments.add(new BoxFragment(table, borderX, borderY, borderBoxWidth, borderBoxHeight));
        fragments.addAll(children);
        return new BlockLayout(
                margin.top() + borderBoxHeight + margin.bottom(), List.copyOf(fragments));
    }

    private static List<TableRow> tableRows(RenderBox table) {
        List<TableRow> rows = new ArrayList<>();
        collectTableRows(table.children(), null, rows);
        return List.copyOf(rows);
    }

    private static void collectTableRows(List<RenderNode> nodes,
                                         RenderBox group,
                                         List<TableRow> rows) {
        for (RenderNode node : nodes) {
            if (!(node instanceof RenderBox box)) {
                continue;
            }
            switch (box.style().display()) {
                case TABLE_HEADER_GROUP, TABLE_ROW_GROUP, TABLE_FOOTER_GROUP ->
                        collectTableRows(box.children(), box, rows);
                case TABLE_ROW -> rows.add(new TableRow(box, group, box.children().stream()
                        .filter(RenderBox.class::isInstance)
                        .map(RenderBox.class::cast)
                        .filter(cell -> cell.style().display() == RenderStyle.Display.TABLE_CELL)
                        .toList()));
                default -> {
                }
            }
        }
    }

    private static float[] distributeRowHeight(float extra, float[] naturalRowHeights) {
        float[] result = naturalRowHeights.clone();
        if (extra <= 0 || result.length == 0) {
            return result;
        }
        float total = sum(result);
        if (total <= 0.01f) {
            result[result.length - 1] += extra;
            return result;
        }
        float distributed = 0;
        for (int index = 0; index < result.length; index++) {
            float share = extra * result[index] / total;
            result[index] += share;
            distributed += share;
        }
        result[result.length - 1] += extra - distributed;
        return result;
    }

    private static float[] fitColumns(float[] preferred, float[] minimum, float targetWidth) {
        float[] result = preferred.clone();
        float preferredWidth = sum(result);
        if (preferredWidth < targetWidth && result.length > 0) {
            float extra = (targetWidth - preferredWidth) / result.length;
            for (int index = 0; index < result.length; index++) result[index] += extra;
            return result;
        }
        float excess = preferredWidth - targetWidth;
        while (excess > 0.01f) {
            int flexible = 0;
            for (int index = 0; index < result.length; index++) {
                if (result[index] > minimum[index] + 0.01f) flexible++;
            }
            if (flexible == 0) break;
            float share = excess / flexible;
            float removed = 0;
            for (int index = 0; index < result.length; index++) {
                float reduction = Math.min(share, result[index] - minimum[index]);
                if (reduction > 0) {
                    result[index] -= reduction;
                    removed += reduction;
                }
            }
            if (removed <= 0.01f) break;
            excess -= removed;
        }
        return result;
    }

    private static float sum(float[] values) {
        float result = 0;
        for (float value : values) result += value;
        return result;
    }

    private record TableRow(RenderBox box, RenderBox group, List<RenderBox> cells) {
    }
}

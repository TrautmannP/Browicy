package com.browicy.ui.render;

import com.browicy.engine.render.RenderBox;
import com.browicy.engine.render.RenderLength;
import com.browicy.engine.render.RenderNode;
import com.browicy.engine.render.RenderStyle;
import com.browicy.ui.render.PositionedLayout.AbsoluteRequest;
import com.browicy.ui.render.RenderLayoutEngine.BlockLayout;
import com.browicy.ui.render.RenderLayoutEngine.BoxFragment;
import com.browicy.ui.render.RenderLayoutEngine.IntrinsicWidths;
import com.browicy.ui.render.RenderLayoutEngine.LineBox;
import com.browicy.ui.render.RenderLayoutEngine.PaintFragment;
import com.browicy.ui.render.PositionedLayout.PositionedContext;

import static com.browicy.ui.render.RenderLayoutEngine.constrain;
import static com.browicy.ui.render.PositionedLayout.fragmentZIndex;
import static com.browicy.ui.render.PositionedLayout.translate;
import static com.browicy.ui.render.PositionedLayout.translateLines;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

final class FlexLayoutEngine {

    interface Host {
        BlockLayout layoutBlock(RenderBox box, float containingX, float y, float availableWidth,
                                Float containingHeight, boolean shrinkToFitAuto,
                                Graphics2D graphics, List<LineBox> lineBoxes,
                                PositionedContext positionedContext);

        IntrinsicWidths intrinsicBoxWidth(RenderBox box, float percentageBase,
                                          Graphics2D graphics, boolean contentBased);

        IntrinsicWidths intrinsicWidths(List<RenderNode> nodes, float percentageBase,
                                        Graphics2D graphics, boolean contentBased);

        Float resolveBoxConstraint(RenderStyle style, RenderLength length, RenderBox box,
                                   float percentageBase, float decoration, Graphics2D graphics);

        float resolve(RenderLength length, float percentageBase);
    }

    private final Host host;
    private final PositionedLayout positionedLayout;

    FlexLayoutEngine(Host host, PositionedLayout positionedLayout) {
        this.host = java.util.Objects.requireNonNull(host, "host");
        this.positionedLayout = java.util.Objects.requireNonNull(positionedLayout, "positionedLayout");
    }

    FlexLayout layoutFlex(RenderBox container,
                          float contentX,
                          float contentY,
                          float contentWidth,
                          Float contentHeight,
                          Graphics2D graphics,
                          List<LineBox> lineBoxes,
                          PositionedContext positionedContext) {
        List<RenderBox> items = new ArrayList<>();
        for (RenderNode child : container.children()) {
            if (!(child instanceof RenderBox item)) continue;
            if (item.style().position() == RenderStyle.Position.ABSOLUTE
                    || item.style().position() == RenderStyle.Position.FIXED) {
                positionedContext.requests.add(new AbsoluteRequest(item, contentX, contentY));
            } else {
                items.add(item);
            }
        }
        if (items.isEmpty()) return new FlexLayout(0, List.of());
        items.sort(java.util.Comparator.comparingInt(item -> item.style().order()));
        return switch (container.style().flexDirection()) {
            case ROW, ROW_REVERSE -> layoutFlexRow(container.style(), items, contentX, contentY,
                    contentWidth, contentHeight, graphics, lineBoxes);
            case COLUMN, COLUMN_REVERSE -> layoutFlexColumn(container.style(), items,
                    contentX, contentY, contentWidth, contentHeight, graphics, lineBoxes);
        };
    }

    private FlexLayout layoutFlexRow(RenderStyle containerStyle,
                                     List<RenderBox> items,
                                     float contentX,
                                     float contentY,
                                     float contentWidth,
                                     Float contentHeight,
                                     Graphics2D graphics,
                                     List<LineBox> lineBoxes) {
        if (containerStyle.flexWrap() == RenderStyle.FlexWrap.NOWRAP) {
            return layoutFlexRowLine(containerStyle, items, contentX, contentY,
                    contentWidth, contentHeight, graphics, lineBoxes);
        }
        List<List<RenderBox>> rows = new ArrayList<>();
        List<RenderBox> row = new ArrayList<>();
        float used = 0;
        for (RenderBox item : items) {
            float basis = flexBaseOuterWidth(item, contentWidth, graphics);
            float gap = row.isEmpty() ? 0 : containerStyle.columnGapPx();
            if (!row.isEmpty() && used + gap + basis > contentWidth + 0.01f) {
                rows.add(List.copyOf(row));
                row.clear();
                used = 0;
                gap = 0;
            }
            row.add(item);
            used += gap + basis;
        }
        if (!row.isEmpty()) rows.add(List.copyOf(row));
        if (containerStyle.flexWrap() == RenderStyle.FlexWrap.WRAP_REVERSE) {
            java.util.Collections.reverse(rows);
        }
        List<List<PaintFragment>> rowFragments = new ArrayList<>();
        List<List<PaintFragment>> rowElevated = new ArrayList<>();
        List<Float> rowHeights = new ArrayList<>();
        for (List<RenderBox> flexRow : rows) {
            FlexLayout layout = layoutFlexRowLine(containerStyle, flexRow, contentX,
                    contentY, contentWidth, null, graphics, lineBoxes);
            rowFragments.add(layout.fragments());
            rowElevated.add(layout.elevated());
            rowHeights.add(layout.height());
        }
        float rowGap = containerStyle.rowGapPx();
        float usedHeight = 0;
        for (Float rowHeight : rowHeights) {
            usedHeight += rowHeight;
        }
        usedHeight += rowGap * Math.max(0, rowHeights.size() - 1);
        float alignHeight = contentHeight == null
                ? usedHeight : Math.max(usedHeight, contentHeight);
        float free = Math.max(0, alignHeight - usedHeight);
        float firstOffset = 0;
        float extraGap = 0;
        switch (containerStyle.alignContent()) {
            case FLEX_END -> firstOffset = free;
            case CENTER -> firstOffset = free / 2f;
            case SPACE_BETWEEN -> {
                if (rowHeights.size() > 1) {
                    extraGap = free / (rowHeights.size() - 1);
                }
            }
            case SPACE_AROUND -> {
                if (!rowHeights.isEmpty()) {
                    extraGap = free / rowHeights.size();
                    firstOffset = extraGap / 2f;
                }
            }
            case SPACE_EVENLY -> {
                if (!rowHeights.isEmpty()) {
                    extraGap = free / (rowHeights.size() + 1);
                    firstOffset = extraGap;
                }
            }
            default -> {
            }
        }
        List<PaintFragment> fragments = new ArrayList<>();
        List<PaintFragment> elevated = new ArrayList<>();
        float cursor = firstOffset;
        for (int index = 0; index < rowFragments.size(); index++) {
            for (PaintFragment fragment : rowFragments.get(index)) {
                fragments.add(translate(fragment, 0, cursor));
            }
            for (PaintFragment fragment : rowElevated.get(index)) {
                elevated.add(translate(fragment, 0, cursor));
            }
            cursor += rowHeights.get(index) + rowGap + extraGap;
        }
        return new FlexLayout(alignHeight, List.copyOf(fragments), List.copyOf(elevated));
    }

    private FlexLayout layoutFlexRowLine(RenderStyle containerStyle,
                                         List<RenderBox> items,
                                         float contentX,
                                         float contentY,
                                         float contentWidth,
                                         Float contentHeight,
                                         Graphics2D graphics,
                                         List<LineBox> lineBoxes) {
        float[] widths = new float[items.size()];
        float[] minimums = new float[items.size()];
        float[] shrinkFactors = new float[items.size()];
        float totalGrow = 0;
        for (int index = 0; index < items.size(); index++) {
            IntrinsicWidths intrinsic = host.intrinsicBoxWidth(items.get(index), contentWidth, graphics, false);
            RenderStyle itemStyle = items.get(index).style();
            widths[index] = flexBaseOuterWidth(itemStyle, intrinsic, contentWidth);
            minimums[index] = flexMinimumOuterWidth(items.get(index), contentWidth, graphics);
            shrinkFactors[index] = itemStyle.flexShrink();
            totalGrow += itemStyle.flexGrow();
        }
        float declaredGaps = containerStyle.columnGapPx() * Math.max(0, items.size() - 1);
        float availableForItems = Math.max(0, contentWidth - declaredGaps);
        shrinkFlexSizes(widths, minimums, shrinkFactors, availableForItems);
        float remaining = Math.max(0, availableForItems - sum(widths));
        if (remaining > 0 && totalGrow > 0) {
            for (int index = 0; index < widths.length; index++) {
                widths[index] += remaining * items.get(index).style().flexGrow() / totalGrow;
            }
        }

        List<FlexItemLayout> layouts = new ArrayList<>();
        float crossSize = 0;
        for (int index = 0; index < items.size(); index++) {
            RenderBox sized = forceOuterWidth(items.get(index), widths[index]);
            FlexItemLayout layout = layoutFlexItem(sized, widths[index], contentHeight, graphics);
            layouts.add(layout);
            crossSize = Math.max(crossSize, layout.layout().outerHeight());
        }
        float sharedBaseline = 0;
        if (containerStyle.alignItems() == RenderStyle.AlignItems.BASELINE) {
            for (FlexItemLayout layout : layouts) {
                sharedBaseline = Math.max(sharedBaseline, flexItemBaseline(layout));
            }
            float baselineCrossSize = 0;
            for (FlexItemLayout layout : layouts) {
                baselineCrossSize = Math.max(baselineCrossSize, sharedBaseline
                        + layout.layout().outerHeight() - flexItemBaseline(layout));
            }
            crossSize = Math.max(crossSize, baselineCrossSize);
        }
        if (contentHeight != null) crossSize = contentHeight;
        if (containerStyle.alignItems() == RenderStyle.AlignItems.STRETCH) {
            for (int index = 0; index < items.size(); index++) {
                RenderStyle itemStyle = items.get(index).style();
                if (effectiveAlignSelf(itemStyle, containerStyle)
                        != RenderStyle.AlignSelf.STRETCH) continue;
                if (!itemStyle.height().isAuto()) continue;
                RenderBox sized = forceOuterHeight(
                        forceOuterWidth(items.get(index), widths[index]), crossSize);
                layouts.set(index, layoutFlexItem(sized, widths[index], crossSize, graphics));
            }
        }

        AxisSpacing spacing = axisSpacing(containerStyle.justifyContent(),
                Math.max(0, contentWidth - sum(widths) - declaredGaps), items.size());
        boolean reverse = containerStyle.flexDirection() == RenderStyle.FlexDirection.ROW_REVERSE;
        float cursor = reverse ? contentWidth - spacing.offset() : spacing.offset();
        List<List<PaintFragment>> itemGroups = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            if (reverse) cursor -= widths[index];
            FlexItemLayout item = layouts.get(index);
            RenderStyle.AlignSelf alignment = effectiveAlignSelf(
                    items.get(index).style(), containerStyle);
            float crossOffset = alignment == RenderStyle.AlignSelf.BASELINE
                    ? sharedBaseline - flexItemBaseline(item)
                    : crossOffset(alignment, crossSize, item.layout().outerHeight());
            List<PaintFragment> group = new ArrayList<>();
            appendFlexItem(item, contentX + cursor, contentY + crossOffset,
                    group, lineBoxes);
            itemGroups.add(group);
            float gap = containerStyle.columnGapPx() + spacing.gap();
            if (reverse) cursor -= gap;
            else cursor += widths[index] + gap;
        }
        return groupFlexItems(itemGroups, crossSize);
    }

    private FlexLayout groupFlexItems(List<List<PaintFragment>> itemGroups, float height) {
        List<PaintFragment> fragments = new ArrayList<>();
        List<PaintFragment> elevated = new ArrayList<>();
        List<List<PaintFragment>> ordered = new ArrayList<>(itemGroups);
        ordered.sort(java.util.Comparator.comparingInt(group -> fragmentZIndex(group.getFirst())));
        for (List<PaintFragment> group : ordered) {
            if (fragmentZIndex(group.getFirst()) > 0) {
                elevated.addAll(group);
            } else {
                fragments.addAll(group);
            }
        }
        return new FlexLayout(height, List.copyOf(fragments), List.copyOf(elevated));
    }

    private float flexBaseOuterWidth(RenderBox item,
                                     float contentWidth,
                                     Graphics2D graphics) {
        return flexBaseOuterWidth(item.style(),
                host.intrinsicBoxWidth(item, contentWidth, graphics, false), contentWidth);
    }

    private float flexBaseOuterWidth(RenderStyle style,
                                     IntrinsicWidths intrinsic,
                                     float percentageBase) {
        RenderLength basis = style.flexBasis();
        if (basis.isAuto()) {
            return intrinsic.preferred();
        }
        if (basis.unit() == RenderLength.Unit.MIN_CONTENT) {
            return intrinsic.minimum();
        }
        if (basis.unit() == RenderLength.Unit.MAX_CONTENT) {
            return intrinsic.preferred();
        }
        return host.resolve(basis, percentageBase)
                + style.margin().horizontal() + style.padding().horizontal()
                + style.borderWidth().horizontal();
    }

    private float flexMinimumOuterWidth(RenderBox box,
                                        float percentageBase,
                                        Graphics2D graphics) {
        RenderStyle style = box.style();
        if (box.children().isEmpty() && !style.width().isAuto()) {
            return host.intrinsicBoxWidth(box, percentageBase, graphics, false).minimum();
        }
        float boxDecoration = style.borderWidth().horizontal() + style.padding().horizontal();
        float outerDecoration = style.margin().horizontal() + boxDecoration;
        IntrinsicWidths content = host.intrinsicWidths(box.children(), percentageBase, graphics, false);
        Float minConstraint = host.resolveBoxConstraint(
                style, style.minWidth(), box, percentageBase, boxDecoration, graphics);
        Float maxConstraint = host.resolveBoxConstraint(
                style, style.maxWidth(), box, percentageBase, boxDecoration, graphics);
        return constrain(content.minimum(), minConstraint, maxConstraint) + outerDecoration;
    }

    private static float flexItemBaseline(FlexItemLayout item) {
        if (!item.lines().isEmpty()) return item.lines().getFirst().baseline();
        return item.layout().outerHeight();
    }

    private FlexLayout layoutFlexColumn(RenderStyle containerStyle,
                                        List<RenderBox> items,
                                        float contentX,
                                        float contentY,
                                        float contentWidth,
                                        Float contentHeight,
                                        Graphics2D graphics,
                                        List<LineBox> lineBoxes) {
        List<FlexItemLayout> layouts = new ArrayList<>();
        float[] heights = new float[items.size()];
        float totalGrow = 0;
        for (int index = 0; index < items.size(); index++) {
            FlexItemLayout layout = layoutFlexItem(items.get(index), contentWidth,
                    contentHeight, graphics);
            layouts.add(layout);
            heights[index] = layout.layout().outerHeight();
            totalGrow += items.get(index).style().flexGrow();
        }
        float declaredGaps = containerStyle.rowGapPx() * Math.max(0, items.size() - 1);
        float mainSize = contentHeight == null ? sum(heights) + declaredGaps : contentHeight;
        float[] shrinkFactors = new float[heights.length];
        for (int index = 0; index < shrinkFactors.length; index++) {
            shrinkFactors[index] = items.get(index).style().flexShrink();
        }
        float availableForItems = Math.max(0, mainSize - declaredGaps);
        shrinkFlexSizes(heights, new float[heights.length], shrinkFactors, availableForItems);
        float remaining = Math.max(0, availableForItems - sum(heights));
        if (remaining > 0 && totalGrow > 0) {
            for (int index = 0; index < heights.length; index++) {
                heights[index] += remaining * items.get(index).style().flexGrow() / totalGrow;
            }
        }
        for (int index = 0; index < items.size(); index++) {
            if (Math.abs(layouts.get(index).layout().outerHeight() - heights[index]) > 0.01f) {
                layouts.set(index, layoutFlexItem(forceOuterHeight(items.get(index), heights[index]),
                        contentWidth, heights[index], graphics));
            }
        }

        AxisSpacing spacing = axisSpacing(containerStyle.justifyContent(),
                Math.max(0, mainSize - sum(heights) - declaredGaps), items.size());
        boolean reverse = containerStyle.flexDirection()
                == RenderStyle.FlexDirection.COLUMN_REVERSE;
        float cursor = reverse ? mainSize - spacing.offset() : spacing.offset();
        List<List<PaintFragment>> itemGroups = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            if (reverse) cursor -= heights[index];
            FlexItemLayout item = layouts.get(index);
            BoxFragment root = (BoxFragment) item.layout().fragments().getFirst();
            float outerWidth = root.width() + item.box().style().margin().horizontal();
            float x = crossOffset(effectiveAlignSelf(items.get(index).style(), containerStyle),
                    contentWidth, outerWidth);
            List<PaintFragment> group = new ArrayList<>();
            appendFlexItem(item, contentX + x, contentY + cursor, group, lineBoxes);
            itemGroups.add(group);
            float gap = containerStyle.rowGapPx() + spacing.gap();
            if (reverse) cursor -= gap;
            else cursor += heights[index] + gap;
        }
        return groupFlexItems(itemGroups, mainSize);
    }

    private FlexItemLayout layoutFlexItem(RenderBox box,
                                          float availableWidth,
                                          Float containingHeight,
                                          Graphics2D graphics) {
        List<LineBox> localLines = new ArrayList<>();
        PositionedContext context = new PositionedContext();
        BlockLayout layout = host.layoutBlock(box, 0, 0, Math.max(0, availableWidth),
                containingHeight, false, graphics, localLines, context);
        BoxFragment root = (BoxFragment) layout.fragments().getFirst();
        context.setGeometry(root.x(), root.y(), root.width(), root.height());
        List<PaintFragment> fragments = new ArrayList<>(layout.fragments());
        fragments.addAll(positionedLayout.layoutAbsoluteRequests(context, graphics, localLines));
        return new FlexItemLayout(box,
                new BlockLayout(layout.outerHeight(), List.copyOf(fragments)),
                List.copyOf(localLines));
    }

    private static RenderBox forceOuterWidth(RenderBox box, float outerWidth) {
        RenderStyle style = box.style();
        float decoration = style.padding().horizontal() + style.borderWidth().horizontal();
        float content = Math.max(0, outerWidth - style.margin().horizontal() - decoration);
        float declared = style.boxSizing() == RenderStyle.BoxSizing.BORDER_BOX
                ? content + decoration : content;
        return new RenderBox(box.source(),
                style.withWidth(new RenderLength(declared, RenderLength.Unit.PX)), box.children());
    }

    static RenderBox forceOuterHeight(RenderBox box, float outerHeight) {
        RenderStyle style = box.style();
        float decoration = style.padding().vertical() + style.borderWidth().vertical();
        float content = Math.max(0, outerHeight - style.margin().vertical() - decoration);
        float declared = style.boxSizing() == RenderStyle.BoxSizing.BORDER_BOX
                ? content + decoration : content;
        return new RenderBox(box.source(),
                style.withHeight(new RenderLength(declared, RenderLength.Unit.PX)), box.children());
    }

    private static void shrinkFlexSizes(float[] sizes, float[] minimums,
                                        float[] factors, float available) {
        float excess = sum(sizes) - available;
        while (excess > 0.01f) {
            float totalWeight = 0;
            for (int index = 0; index < sizes.length; index++) {
                if (factors[index] > 0 && sizes[index] > minimums[index] + 0.01f) {
                    totalWeight += factors[index] * sizes[index];
                }
            }
            if (totalWeight <= 0) return;
            float removed = 0;
            for (int index = 0; index < sizes.length; index++) {
                if (factors[index] <= 0) continue;
                float share = excess * factors[index] * sizes[index] / totalWeight;
                float reduction = Math.min(share, sizes[index] - minimums[index]);
                if (reduction > 0) {
                    sizes[index] -= reduction;
                    removed += reduction;
                }
            }
            if (removed < 0.01f) return;
            excess -= removed;
        }
    }

    private static AxisSpacing axisSpacing(RenderStyle.JustifyContent justify,
                                           float free,
                                           int itemCount) {
        return switch (justify) {
            case CENTER -> new AxisSpacing(free / 2f, 0);
            case FLEX_END -> new AxisSpacing(free, 0);
            case SPACE_BETWEEN -> new AxisSpacing(0,
                    itemCount > 1 ? free / (itemCount - 1) : 0);
            case SPACE_AROUND -> {
                float gap = itemCount > 0 ? free / itemCount : 0;
                yield new AxisSpacing(gap / 2f, gap);
            }
            case SPACE_EVENLY -> {
                float gap = free / (itemCount + 1);
                yield new AxisSpacing(gap, gap);
            }
            default -> new AxisSpacing(0, 0);
        };
    }

    private static RenderStyle.AlignSelf effectiveAlignSelf(RenderStyle itemStyle,
                                                            RenderStyle containerStyle) {
        return itemStyle.alignSelf() == RenderStyle.AlignSelf.AUTO
                ? switch (containerStyle.alignItems()) {
            case FLEX_START -> RenderStyle.AlignSelf.FLEX_START;
            case CENTER -> RenderStyle.AlignSelf.CENTER;
            case FLEX_END -> RenderStyle.AlignSelf.FLEX_END;
            case BASELINE -> RenderStyle.AlignSelf.BASELINE;
            default -> RenderStyle.AlignSelf.STRETCH;
        }
                : itemStyle.alignSelf();
    }

    private static float crossOffset(RenderStyle.AlignSelf align, float available, float used) {
        float free = Math.max(0, available - used);
        return switch (align) {
            case CENTER -> free / 2f;
            case FLEX_END -> free;
            default -> 0;
        };
    }

    private static void appendFlexItem(FlexItemLayout item,
                                       float x,
                                       float y,
                                       List<PaintFragment> fragments,
                                       List<LineBox> lineBoxes) {
        item.layout().fragments().stream().map(fragment -> translate(fragment, x, y))
                .forEach(fragments::add);
        int first = lineBoxes.size();
        lineBoxes.addAll(item.lines());
        translateLines(lineBoxes, first, x, y);
    }

    private static float sum(float[] values) {
        float result = 0;
        for (float value : values) result += value;
        return result;
    }

    record FlexLayout(float height, List<PaintFragment> fragments,
                      List<PaintFragment> elevated) {
        private FlexLayout(float height, List<PaintFragment> fragments) {
            this(height, fragments, List.of());
        }
    }

    private record FlexItemLayout(RenderBox box,
                                  BlockLayout layout,
                                  List<LineBox> lines) {
    }

    private record AxisSpacing(float offset, float gap) {
    }
}

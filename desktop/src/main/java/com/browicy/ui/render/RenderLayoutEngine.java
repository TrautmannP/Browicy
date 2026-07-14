package com.browicy.ui.render;

import com.browicy.engine.render.BoxEdges;
import com.browicy.engine.render.CssColor;
import com.browicy.engine.render.RenderBox;
import com.browicy.engine.render.RenderInlineBox;
import com.browicy.engine.render.RenderInlineBlock;
import com.browicy.engine.render.RenderImage;
import com.browicy.engine.render.RenderLineBreak;
import com.browicy.engine.render.RenderLength;
import com.browicy.engine.render.RenderNode;
import com.browicy.engine.render.RenderStyle;
import com.browicy.engine.render.RenderTextRun;
import com.browicy.engine.render.RenderTree;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Function;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public final class RenderLayoutEngine {

    private static final float PLACEHOLDER_SIZE = 16f;
    private static final int MAX_IMAGE_DIMENSION = 8192;
    private static final long MAX_IMAGE_PIXELS = 32_000_000L;
    private float rootFontSizePx = 16f;
    private float viewportWidth = 800f;
    private float viewportHeight = 600f;
    private final Function<String, Font> webFontResolver;
    private final Map<com.browicy.engine.dom.Element, Optional<BufferedImage>> decodedImages =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());

    public RenderLayoutEngine() {
        this(ignored -> null);
    }

    public RenderLayoutEngine(Function<String, Font> webFontResolver) {
        this.webFontResolver = java.util.Objects.requireNonNull(webFontResolver, "webFontResolver");
    }

    public LayoutResult layout(RenderTree tree, int viewportWidth, Insets insets, Graphics2D graphics) {
        rootFontSizePx = tree.rootFontSizePx();
        this.viewportWidth = viewportWidth;
        this.viewportHeight = tree.viewportHeight();
        float availableWidth = Math.max(1, viewportWidth - insets.left - insets.right);
        List<LineBox> lineBoxes = new ArrayList<>();
        PositionedContext initialContainingBlock = new PositionedContext();
        BlockLayout root = layoutBlock(
                tree.root(), insets.left, insets.top, availableWidth, null, false,
                graphics, lineBoxes, initialContainingBlock);
        initialContainingBlock.setGeometry(
                insets.left, insets.top, availableWidth, root.outerHeight());
        List<PaintFragment> fragments = new ArrayList<>(root.fragments());
        fragments.addAll(layoutAbsoluteRequests(initialContainingBlock, graphics, lineBoxes));
        float height = insets.top + root.outerHeight() + insets.bottom;
        return new LayoutResult(
                viewportWidth,
                Math.max(insets.top + insets.bottom, height),
                fragments,
                lineBoxes);
    }

    private BlockLayout layoutBlock(RenderBox box,
                                    float containingX,
                                    float y,
                                    float availableWidth,
                                    Float containingHeight,
                                    boolean shrinkToFitAuto,
                                    Graphics2D graphics,
                                    List<LineBox> lineBoxes,
                                    PositionedContext positionedContext) {
        int firstLine = lineBoxes.size();
        RenderStyle style = box.style();
        if (style.display() == RenderStyle.Display.TABLE) {
            return layoutTable(box, containingX, y, availableWidth, containingHeight,
                    graphics, lineBoxes, positionedContext);
        }
        PositionedContext childPositionedContext = style.position() == RenderStyle.Position.STATIC
                ? positionedContext : new PositionedContext();
        BoxEdges margin = style.margin();
        BoxEdges padding = style.padding();
        BoxEdges border = style.borderWidth();

        float horizontalDecoration = border.horizontal() + padding.horizontal();
        float availableContentWidth = Math.max(0,
                availableWidth - margin.horizontal() - horizontalDecoration);
        float contentBoxWidth;
        if (style.width().isAuto()) {
            contentBoxWidth = shrinkToFitAuto
                    ? shrinkToFitWidth(box, availableContentWidth, graphics)
                    : availableContentWidth;
        } else {
            contentBoxWidth = contentBoxDimension(
                    style, resolve(style.width(), availableWidth), horizontalDecoration);
        }
        contentBoxWidth = constrain(contentBoxWidth,
                resolveContentConstraint(
                        style, style.minWidth(), availableWidth, horizontalDecoration),
                resolveContentConstraint(
                        style, style.maxWidth(), availableWidth, horizontalDecoration));
        float borderBoxWidth = contentBoxWidth + horizontalDecoration;
        float freeWidth = Math.max(0,
                availableWidth - borderBoxWidth - margin.horizontal());
        float automaticLeft = style.autoMargins().left()
                ? (style.autoMargins().right() ? freeWidth / 2f : freeWidth)
                : 0;
        float borderX = containingX + margin.left() + automaticLeft;
        float borderY = y + margin.top();
        float contentX = borderX + border.left() + padding.left();
        float contentY = borderY + border.top() + padding.top();
        float contentWidth = Math.max(1, contentBoxWidth);

        float verticalDecoration = border.vertical() + padding.vertical();
        Float specifiedContentHeight = resolveContentHeight(
                style, style.height(), containingHeight, verticalDecoration);
        Float childContainingHeight = specifiedContentHeight == null
                ? null
                : constrain(specifiedContentHeight,
                        resolveContentHeight(
                                style, style.minHeight(), containingHeight, verticalDecoration),
                        resolveContentHeight(
                                style, style.maxHeight(), containingHeight, verticalDecoration));

        List<PaintFragment> childFragments = new ArrayList<>();
        float naturalContentHeight;
        if (style.display() == RenderStyle.Display.FLEX
                || style.display() == RenderStyle.Display.INLINE_FLEX) {
            FlexLayout flex = layoutFlex(box, contentX, contentY, contentWidth,
                    childContainingHeight, graphics, lineBoxes, childPositionedContext);
            childFragments.addAll(flex.fragments());
            naturalContentHeight = flex.height();
        } else {
            naturalContentHeight = layoutBlockChildren(box, contentX, contentY, contentWidth,
                    childContainingHeight, graphics, lineBoxes, childPositionedContext,
                    childFragments);
        }
        float contentHeight = specifiedContentHeight == null
                ? naturalContentHeight
                : specifiedContentHeight;
        contentHeight = constrain(contentHeight,
                resolveContentHeight(
                        style, style.minHeight(), containingHeight, verticalDecoration),
                resolveContentHeight(
                        style, style.maxHeight(), containingHeight, verticalDecoration));
        float borderBoxHeight = border.top() + padding.top() + contentHeight
                + padding.bottom() + border.bottom();
        float outerHeight = Math.max(0, margin.top() + borderBoxHeight + margin.bottom());

        if (childPositionedContext != positionedContext) {
            childPositionedContext.setGeometry(
                    borderX + border.left(), borderY + border.top(),
                    Math.max(0, borderBoxWidth - border.horizontal()),
                    Math.max(0, borderBoxHeight - border.vertical()));
            childFragments.addAll(
                    layoutAbsoluteRequests(childPositionedContext, graphics, lineBoxes));
        }

        if (style.overflow() != RenderStyle.Overflow.VISIBLE) {
            ClipRect clip = new ClipRect(
                    borderX + border.left(), borderY + border.top(),
                    Math.max(0, borderBoxWidth - border.horizontal()),
                    Math.max(0, borderBoxHeight - border.vertical()));
            childFragments.replaceAll(fragment -> withClip(fragment, clip));
        }

        List<PaintFragment> fragments = new ArrayList<>(childFragments.size() + 1);
        fragments.add(new BoxFragment(box, borderX, borderY, borderBoxWidth, borderBoxHeight));
        fragments.addAll(childFragments);
        if (style.position() == RenderStyle.Position.RELATIVE) {
            float dx = relativeHorizontalOffset(style, availableWidth);
            float dy = relativeVerticalOffset(style, containingHeight);
            fragments.replaceAll(fragment -> translate(fragment, dx, dy));
            translateLines(lineBoxes, firstLine, dx, dy);
        }
        return new BlockLayout(outerHeight, List.copyOf(fragments));
    }

    private float layoutBlockChildren(RenderBox box,
                                      float contentX,
                                      float contentY,
                                      float contentWidth,
                                      Float childContainingHeight,
                                      Graphics2D graphics,
                                      List<LineBox> lineBoxes,
                                      PositionedContext childPositionedContext,
                                      List<PaintFragment> childFragments) {
        List<RenderNode> inlineBuffer = new ArrayList<>();
        List<FloatRegion> floats = new ArrayList<>();
        float currentY = contentY;
        Float previousBottomMargin = null;
        for (RenderNode child : box.children()) {
            if (child instanceof RenderBox childBox) {
                FloatArea inlineArea = floatArea(floats, contentX, contentWidth, currentY);
                float inlineHeight = flushInline(inlineBuffer, inlineArea.x(), currentY,
                        inlineArea.width(), childContainingHeight, box.style().textAlign(),
                        graphics, childFragments, lineBoxes);
                currentY += inlineHeight;
                if (inlineHeight > 0) previousBottomMargin = null;
                if (childBox.style().position() == RenderStyle.Position.ABSOLUTE) {
                    childPositionedContext.requests.add(
                            new AbsoluteRequest(childBox, contentX, currentY));
                    continue;
                }
                currentY = clearedY(floats, currentY, childBox.style().clear());
                FloatArea blockArea = floatArea(floats, contentX, contentWidth, currentY);
                if (childBox.style().floatMode() != RenderStyle.FloatMode.NONE) {
                    int floatFirstLine = lineBoxes.size();
                    BlockLayout floatLayout = layoutBlock(
                            childBox, blockArea.x(), currentY, blockArea.width(),
                            childContainingHeight, true, graphics, lineBoxes,
                            childPositionedContext);
                    BoxFragment root = (BoxFragment) floatLayout.fragments().getFirst();
                    float desiredX = childBox.style().floatMode() == RenderStyle.FloatMode.LEFT
                            ? blockArea.x() + childBox.style().margin().left()
                            : blockArea.x() + blockArea.width()
                                    - childBox.style().margin().right() - root.width();
                    float dx = desiredX - root.x();
                    floatLayout.fragments().stream().map(fragment -> translate(fragment, dx, 0))
                            .forEach(childFragments::add);
                    translateLines(lineBoxes, floatFirstLine, dx, 0);
                    floats.add(new FloatRegion(
                            childBox.style().floatMode(),
                            desiredX - childBox.style().margin().left(), currentY,
                            root.width() + childBox.style().margin().horizontal(),
                            floatLayout.outerHeight()));
                    previousBottomMargin = null;
                    continue;
                }
                float collapsedOverlap = previousBottomMargin == null ? 0
                        : previousBottomMargin + childBox.style().margin().top()
                        - Math.max(previousBottomMargin, childBox.style().margin().top());
                currentY -= collapsedOverlap;
                BlockLayout childLayout = layoutBlock(
                        childBox, blockArea.x(), currentY, blockArea.width(),
                        childContainingHeight, false, graphics, lineBoxes,
                        childPositionedContext);
                childFragments.addAll(childLayout.fragments());
                currentY += childLayout.outerHeight();
                previousBottomMargin = childBox.style().margin().bottom();
            } else {
                previousBottomMargin = null;
                inlineBuffer.add(child);
            }
        }
        FloatArea finalArea = floatArea(floats, contentX, contentWidth, currentY);
        currentY += flushInline(inlineBuffer, finalArea.x(), currentY, finalArea.width(),
                childContainingHeight, box.style().textAlign(), graphics, childFragments,
                lineBoxes);
        return Math.max(0, currentY - contentY);
    }

    private FlexLayout layoutFlex(RenderBox container,
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
            if (item.style().position() == RenderStyle.Position.ABSOLUTE) {
                positionedContext.requests.add(new AbsoluteRequest(item, contentX, contentY));
            } else {
                items.add(item);
            }
        }
        if (items.isEmpty()) return new FlexLayout(0, List.of());
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
        float[] widths = new float[items.size()];
        float[] minimums = new float[items.size()];
        float totalGrow = 0;
        for (int index = 0; index < items.size(); index++) {
            IntrinsicWidths intrinsic = intrinsicBoxWidth(items.get(index), contentWidth, graphics);
            widths[index] = intrinsic.preferred();
            minimums[index] = intrinsic.minimum();
            totalGrow += items.get(index).style().flexGrow();
        }
        shrinkFlexSizes(widths, minimums, contentWidth);
        float remaining = Math.max(0, contentWidth - sum(widths));
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
        if (contentHeight != null) crossSize = contentHeight;
        if (containerStyle.alignItems() == RenderStyle.AlignItems.STRETCH) {
            for (int index = 0; index < items.size(); index++) {
                if (!items.get(index).style().height().isAuto()) continue;
                RenderBox sized = forceOuterHeight(
                        forceOuterWidth(items.get(index), widths[index]), crossSize);
                layouts.set(index, layoutFlexItem(sized, widths[index], crossSize, graphics));
            }
        }

        AxisSpacing spacing = axisSpacing(containerStyle.justifyContent(),
                Math.max(0, contentWidth - sum(widths)), items.size());
        boolean reverse = containerStyle.flexDirection() == RenderStyle.FlexDirection.ROW_REVERSE;
        float cursor = reverse ? contentWidth - spacing.offset() : spacing.offset();
        List<PaintFragment> fragments = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            if (reverse) cursor -= widths[index];
            FlexItemLayout item = layouts.get(index);
            float crossOffset = crossOffset(containerStyle.alignItems(), crossSize,
                    item.layout().outerHeight());
            appendFlexItem(item, contentX + cursor, contentY + crossOffset,
                    fragments, lineBoxes);
            if (reverse) cursor -= spacing.gap();
            else cursor += widths[index] + spacing.gap();
        }
        return new FlexLayout(crossSize, List.copyOf(fragments));
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
        float mainSize = contentHeight == null ? sum(heights) : contentHeight;
        shrinkFlexSizes(heights, new float[heights.length], mainSize);
        float remaining = Math.max(0, mainSize - sum(heights));
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
                Math.max(0, mainSize - sum(heights)), items.size());
        boolean reverse = containerStyle.flexDirection()
                == RenderStyle.FlexDirection.COLUMN_REVERSE;
        float cursor = reverse ? mainSize - spacing.offset() : spacing.offset();
        List<PaintFragment> fragments = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            if (reverse) cursor -= heights[index];
            FlexItemLayout item = layouts.get(index);
            BoxFragment root = (BoxFragment) item.layout().fragments().getFirst();
            float outerWidth = root.width() + item.box().style().margin().horizontal();
            float x = crossOffset(containerStyle.alignItems(), contentWidth, outerWidth);
            appendFlexItem(item, contentX + x, contentY + cursor, fragments, lineBoxes);
            if (reverse) cursor -= spacing.gap();
            else cursor += heights[index] + spacing.gap();
        }
        return new FlexLayout(mainSize, List.copyOf(fragments));
    }

    private FlexItemLayout layoutFlexItem(RenderBox box,
                                          float availableWidth,
                                          Float containingHeight,
                                          Graphics2D graphics) {
        List<LineBox> localLines = new ArrayList<>();
        PositionedContext context = new PositionedContext();
        BlockLayout layout = layoutBlock(box, 0, 0, Math.max(0, availableWidth),
                containingHeight, false, graphics, localLines, context);
        BoxFragment root = (BoxFragment) layout.fragments().getFirst();
        context.setGeometry(root.x(), root.y(), root.width(), root.height());
        List<PaintFragment> fragments = new ArrayList<>(layout.fragments());
        fragments.addAll(layoutAbsoluteRequests(context, graphics, localLines));
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

    private static RenderBox forceOuterHeight(RenderBox box, float outerHeight) {
        RenderStyle style = box.style();
        float decoration = style.padding().vertical() + style.borderWidth().vertical();
        float content = Math.max(0, outerHeight - style.margin().vertical() - decoration);
        float declared = style.boxSizing() == RenderStyle.BoxSizing.BORDER_BOX
                ? content + decoration : content;
        return new RenderBox(box.source(),
                style.withHeight(new RenderLength(declared, RenderLength.Unit.PX)), box.children());
    }

    private static void shrinkFlexSizes(float[] sizes, float[] minimums, float available) {
        float excess = sum(sizes) - available;
        while (excess > 0.01f) {
            int flexible = 0;
            for (int index = 0; index < sizes.length; index++) {
                if (sizes[index] > minimums[index] + 0.01f) flexible++;
            }
            if (flexible == 0) return;
            float share = excess / flexible;
            float removed = 0;
            for (int index = 0; index < sizes.length; index++) {
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

    private static float crossOffset(RenderStyle.AlignItems align, float available, float used) {
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

    private static FloatArea floatArea(List<FloatRegion> floats,
                                       float contentX,
                                       float contentWidth,
                                       float y) {
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

    private static float clearedY(List<FloatRegion> floats,
                                  float y,
                                  RenderStyle.Clear clear) {
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

    private BlockLayout layoutTable(RenderBox table,
                                    float containingX,
                                    float y,
                                    float availableWidth,
                                    Float containingHeight,
                                    Graphics2D graphics,
                                    List<LineBox> lineBoxes,
                                    PositionedContext positionedContext) {
        RenderStyle style = table.style();
        BoxEdges margin = style.margin();
        BoxEdges padding = style.padding();
        BoxEdges border = style.borderWidth();
        float decoration = border.horizontal() + padding.horizontal();
        float availableContentWidth = Math.max(1,
                availableWidth - margin.horizontal() - decoration);
        List<TableRow> rows = tableRows(table);
        int columnCount = rows.stream().mapToInt(row -> row.cells().size()).max().orElse(0);
        float[] preferred = new float[columnCount];
        float[] minimum = new float[columnCount];
        for (TableRow row : rows) {
            for (int column = 0; column < row.cells().size(); column++) {
                IntrinsicWidths intrinsic = intrinsicBoxWidth(
                        row.cells().get(column), availableContentWidth, graphics);
                preferred[column] = Math.max(preferred[column], intrinsic.preferred());
                minimum[column] = Math.max(minimum[column], intrinsic.minimum());
            }
        }
        float specifiedWidth = style.width().isAuto()
                ? Float.NaN
                : contentBoxDimension(style, resolve(style.width(), availableWidth), decoration);
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
        float currentY = borderY + border.top() + padding.top();
        List<PaintFragment> children = new ArrayList<>();

        RenderBox currentGroup = null;
        float groupY = currentY;
        int groupInsertAt = -1;
        for (TableRow row : rows) {
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
            List<List<PaintFragment>> cellFragments = new ArrayList<>();
            float rowHeight = 0;
            float cellX = contentX;
            for (int column = 0; column < row.cells().size(); column++) {
                RenderBox cell = row.cells().get(column);
                BlockLayout cellLayout = layoutBlock(
                        cell, cellX, currentY, columnWidths[column], containingHeight,
                        false, graphics, lineBoxes, positionedContext);
                cellFragments.add(new ArrayList<>(cellLayout.fragments()));
                rowHeight = Math.max(rowHeight, cellLayout.outerHeight());
                cellX += columnWidths[column];
            }
            children.add(new BoxFragment(row.box(), contentX, currentY, contentWidth, rowHeight));
            for (int column = 0; column < cellFragments.size(); column++) {
                List<PaintFragment> fragments = cellFragments.get(column);
                BoxFragment cellRoot = (BoxFragment) fragments.getFirst();
                fragments.set(0, new BoxFragment(cellRoot.box(), cellRoot.x(), cellRoot.y(),
                        columnWidths[column], Math.max(cellRoot.height(), rowHeight), cellRoot.clip()));
                children.addAll(fragments);
            }
            currentY += rowHeight;
        }
        if (currentGroup != null) {
            children.set(groupInsertAt, new BoxFragment(
                    currentGroup, contentX, groupY, contentWidth, currentY - groupY));
        }
        float contentHeight = Math.max(0,
                currentY - (borderY + border.top() + padding.top()));
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
                default -> { }
            }
        }
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

    private List<PaintFragment> layoutAbsoluteRequests(PositionedContext context,
                                                        Graphics2D graphics,
                                                        List<LineBox> lineBoxes) {
        List<PaintFragment> result = new ArrayList<>();
        for (AbsoluteRequest request : context.requests.stream()
                .sorted(Comparator.comparingInt(request -> request.box().style().zIndex()))
                .toList()) {
            RenderStyle style = request.box().style();
            float left = style.left().isAuto() ? 0 : resolve(style.left(), context.width);
            float right = style.right().isAuto() ? 0 : resolve(style.right(), context.width);
            boolean stretchAutoWidth = style.width().isAuto()
                    && !style.left().isAuto() && !style.right().isAuto();
            float availableWidth = stretchAutoWidth
                    ? Math.max(0, context.width - left - right)
                    : context.width;
            int firstLine = lineBoxes.size();
            BlockLayout layout = layoutBlock(
                    request.box(), context.x, context.y, availableWidth, context.height,
                    style.width().isAuto() && !stretchAutoWidth,
                    graphics, lineBoxes, context);
            BoxFragment root = (BoxFragment) layout.fragments().getFirst();
            float desiredX;
            if (!style.left().isAuto()) {
                desiredX = context.x + left + style.margin().left();
            } else if (!style.right().isAuto()) {
                desiredX = context.x + context.width - right
                        - style.margin().right() - root.width();
            } else {
                desiredX = request.staticX() + style.margin().left();
            }

            float desiredY;
            if (!style.top().isAuto()) {
                desiredY = context.y + resolve(style.top(), context.height) + style.margin().top();
            } else if (!style.bottom().isAuto()) {
                desiredY = context.y + context.height - resolve(style.bottom(), context.height)
                        - style.margin().bottom() - root.height();
            } else {
                desiredY = request.staticY() + style.margin().top();
            }
            float dx = desiredX - root.x();
            float dy = desiredY - root.y();
            layout.fragments().stream().map(fragment -> translate(fragment, dx, dy))
                    .forEach(result::add);
            translateLines(lineBoxes, firstLine, dx, dy);
        }
        return result;
    }

    private float relativeHorizontalOffset(RenderStyle style, float containingWidth) {
        if (!style.left().isAuto()) return resolve(style.left(), containingWidth);
        if (!style.right().isAuto()) return -resolve(style.right(), containingWidth);
        return 0;
    }

    private float relativeVerticalOffset(RenderStyle style, Float containingHeight) {
        float base = containingHeight == null ? 0 : containingHeight;
        if (!style.top().isAuto()) return resolve(style.top(), base);
        if (!style.bottom().isAuto()) return -resolve(style.bottom(), base);
        return 0;
    }

    private static void translateLines(List<LineBox> lines, int first, float dx, float dy) {
        if (dx == 0 && dy == 0) return;
        for (int index = first; index < lines.size(); index++) {
            LineBox line = lines.get(index);
            List<InlineFragment> fragments = line.fragments().stream()
                    .map(fragment -> (InlineFragment) translate(fragment, dx, dy))
                    .toList();
            lines.set(index, new LineBox(line.x() + dx, line.y() + dy, line.width(),
                    line.height(), line.baseline() + dy, fragments));
        }
    }

    private static PaintFragment translate(PaintFragment fragment, float dx, float dy) {
        if (fragment instanceof BoxFragment box) {
            return new BoxFragment(box.box(), box.x() + dx, box.y() + dy,
                    box.width(), box.height(), translate(box.clip(), dx, dy));
        }
        if (fragment instanceof InlineBoxFragment box) {
            return new InlineBoxFragment(box.box(), box.x() + dx, box.y() + dy,
                    box.width(), box.height(), box.firstFragment(), box.lastFragment(),
                    translate(box.clip(), dx, dy));
        }
        if (fragment instanceof ImageFragment image) {
            return new ImageFragment(image.image(), image.bitmap(), image.x() + dx,
                    image.y() + dy, image.width(), image.height(),
                    translate(image.clip(), dx, dy));
        }
        TextFragment text = (TextFragment) fragment;
        return new TextFragment(text.text(), text.x() + dx, text.width(),
                text.baseline() + dy, text.top() + dy, text.height(), text.font(),
                text.color(), text.underline(), text.decorationColor(),
                translate(text.clip(), dx, dy));
    }

    private static ClipRect translate(ClipRect clip, float dx, float dy) {
        return clip == null ? null
                : new ClipRect(clip.x() + dx, clip.y() + dy, clip.width(), clip.height());
    }

    private static float constrain(float value, Float minimum, Float maximum) {
        float result = Math.max(0, value);
        if (maximum != null) {
            result = Math.min(result, maximum);
        }
        if (minimum != null) {
            result = Math.max(result, minimum);
        }
        return result;
    }

    private Float resolveConstraint(RenderLength length, float percentageBase) {
        return length.isAuto() ? null : Math.max(0, resolve(length, percentageBase));
    }

    private Float resolveContentConstraint(RenderStyle style,
                                           RenderLength length,
                                           float percentageBase,
                                           float decoration) {
        Float resolved = resolveConstraint(length, percentageBase);
        return resolved == null ? null : contentBoxDimension(style, resolved, decoration);
    }

    private Float resolveDefiniteHeight(RenderLength length, Float containingHeight) {
        if (length.isAuto()) {
            return null;
        }
        if (length.unit() == RenderLength.Unit.PERCENT) {
            return containingHeight == null ? null : Math.max(0, resolve(length, containingHeight));
        }
        return Math.max(0, resolve(length, containingHeight == null ? 0 : containingHeight));
    }

    private Float resolveHeightConstraint(RenderLength length, Float containingHeight) {
        return resolveDefiniteHeight(length, containingHeight);
    }

    private Float resolveContentHeight(RenderStyle style,
                                       RenderLength length,
                                       Float containingHeight,
                                       float decoration) {
        Float resolved = resolveDefiniteHeight(length, containingHeight);
        return resolved == null ? null : contentBoxDimension(style, resolved, decoration);
    }

    private static float contentBoxDimension(RenderStyle style,
                                             float specifiedDimension,
                                             float decoration) {
        return Math.max(0, specifiedDimension
                - (style.boxSizing() == RenderStyle.BoxSizing.BORDER_BOX ? decoration : 0));
    }

    private float resolve(RenderLength length, float percentageBase) {
        return length.resolve(percentageBase, rootFontSizePx, viewportWidth, viewportHeight);
    }

    private float resolve(com.browicy.engine.render.RenderOffset offset, float percentageBase) {
        return offset.resolve(percentageBase, rootFontSizePx, viewportWidth, viewportHeight);
    }

    private ImageLayout imageLayout(RenderImage image,
                                    float percentageBase,
                                    Float containingHeight) {
        BufferedImage bitmap = decode(image);
        float naturalWidth = image.htmlWidth() != null
                ? image.htmlWidth()
                : bitmap == null ? PLACEHOLDER_SIZE : bitmap.getWidth();
        float naturalHeight = image.htmlHeight() != null
                ? image.htmlHeight()
                : bitmap == null ? PLACEHOLDER_SIZE : bitmap.getHeight();
        float ratio = bitmap != null && bitmap.getWidth() > 0 && bitmap.getHeight() > 0
                ? (float) bitmap.getWidth() / bitmap.getHeight()
                : naturalWidth / Math.max(1, naturalHeight);
        Float cssWidth = image.style().width().isAuto()
                ? null
                : Math.max(0, resolve(image.style().width(), percentageBase));
        Float cssHeight = resolveDefiniteHeight(image.style().height(), containingHeight);
        float width;
        float height;
        if (cssWidth != null && cssHeight != null) {
            width = cssWidth;
            height = cssHeight;
        } else if (cssWidth != null) {
            width = cssWidth;
            height = width / Math.max(0.0001f, ratio);
        } else if (cssHeight != null) {
            height = cssHeight;
            width = height * ratio;
        } else {
            width = naturalWidth;
            height = naturalHeight;
        }
        width = constrain(width,
                resolveConstraint(image.style().minWidth(), percentageBase),
                resolveConstraint(image.style().maxWidth(), percentageBase));
        height = constrain(height,
                resolveHeightConstraint(image.style().minHeight(), containingHeight),
                resolveHeightConstraint(image.style().maxHeight(), containingHeight));
        return new ImageLayout(bitmap, width, height, image.style().verticalAlign(),
                image.style().fontSizePx());
    }

    private BufferedImage decode(RenderImage image) {
        byte[] data = image.data();
        if (data == null || data.length == 0) return null;
        Optional<BufferedImage> cached = decodedImages.get(image.source());
        if (cached != null) return cached.orElse(null);
        BufferedImage decoded = decodeWithinLimits(data);
        decodedImages.put(image.source(), Optional.ofNullable(decoded));
        return decoded;
    }

    private static BufferedImage decodeWithinLimits(byte[] data) {
        try (ImageInputStream input =
                     ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (input == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width <= 0 || height <= 0
                        || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                        || width * height > MAX_IMAGE_PIXELS) {
                    return null;
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException invalidImage) {
            return null;
        }
    }

    private float shrinkToFitWidth(RenderBox box,
                                   float availableContentWidth,
                                   Graphics2D graphics) {
        IntrinsicWidths intrinsic = intrinsicWidths(box.children(), availableContentWidth, graphics);
        return Math.min(Math.max(intrinsic.minimum(), availableContentWidth),
                intrinsic.preferred());
    }

    private IntrinsicWidths intrinsicWidths(List<RenderNode> nodes,
                                            float percentageBase,
                                            Graphics2D graphics) {
        float preferred = 0;
        float minimum = 0;
        float inlinePreferred = 0;
        float inlineMinimum = 0;
        for (RenderNode node : nodes) {
            if (node instanceof RenderBox block) {
                preferred = Math.max(preferred, inlinePreferred);
                minimum = Math.max(minimum, inlineMinimum);
                inlinePreferred = 0;
                inlineMinimum = 0;
                IntrinsicWidths child = intrinsicBoxWidth(block, percentageBase, graphics);
                preferred = Math.max(preferred, child.preferred());
                minimum = Math.max(minimum, child.minimum());
            } else {
                IntrinsicWidths child = intrinsicNodeWidth(node, percentageBase, graphics);
                inlinePreferred += child.preferred();
                inlineMinimum = Math.max(inlineMinimum, child.minimum());
            }
        }
        return new IntrinsicWidths(
                Math.max(preferred, inlinePreferred),
                Math.max(minimum, inlineMinimum));
    }

    private IntrinsicWidths intrinsicBoxWidth(RenderBox box,
                                              float percentageBase,
                                              Graphics2D graphics) {
        RenderStyle style = box.style();
        float boxDecoration = style.borderWidth().horizontal() + style.padding().horizontal();
        float outerDecoration = style.margin().horizontal() + boxDecoration;
        if (!style.width().isAuto()) {
            float width = constrain(
                    contentBoxDimension(
                            style, resolve(style.width(), percentageBase), boxDecoration),
                    resolveContentConstraint(
                            style, style.minWidth(), percentageBase, boxDecoration),
                    resolveContentConstraint(
                            style, style.maxWidth(), percentageBase, boxDecoration))
                    + outerDecoration;
            return new IntrinsicWidths(width, width);
        }
        IntrinsicWidths content = intrinsicWidths(box.children(), percentageBase, graphics);
        float preferred = constrain(content.preferred(),
                resolveContentConstraint(
                        style, style.minWidth(), percentageBase, boxDecoration),
                resolveContentConstraint(
                        style, style.maxWidth(), percentageBase, boxDecoration));
        float minimum = constrain(content.minimum(),
                resolveContentConstraint(
                        style, style.minWidth(), percentageBase, boxDecoration),
                resolveContentConstraint(
                        style, style.maxWidth(), percentageBase, boxDecoration));
        return new IntrinsicWidths(preferred + outerDecoration, minimum + outerDecoration);
    }

    private IntrinsicWidths intrinsicNodeWidth(RenderNode node,
                                               float percentageBase,
                                               Graphics2D graphics) {
        if (node instanceof RenderTextRun run) {
            FontMetrics metrics = graphics.getFontMetrics(fontFor(run.style()));
            String collapsed = run.text().trim().replaceAll("\\s+", " ");
            float preferred = metrics.stringWidth(collapsed);
            float minimum = 0;
            for (String word : collapsed.split(" ")) {
                minimum = Math.max(minimum, metrics.stringWidth(word));
            }
            return new IntrinsicWidths(preferred, minimum);
        }
        if (node instanceof RenderInlineBox inline) {
            IntrinsicWidths content = intrinsicWidths(inline.children(), percentageBase, graphics);
            RenderStyle style = inline.style();
            float decoration = style.margin().horizontal() + style.borderWidth().horizontal()
                    + style.padding().horizontal();
            return new IntrinsicWidths(content.preferred() + decoration,
                    content.minimum() + decoration);
        }
        if (node instanceof RenderInlineBlock inlineBlock) {
            return intrinsicBoxWidth(inlineBlock.box(), percentageBase, graphics);
        }
        if (node instanceof RenderImage image) {
            ImageLayout layout = imageLayout(image, percentageBase, null);
            return new IntrinsicWidths(layout.width(), layout.width());
        }
        return new IntrinsicWidths(0, 0);
    }

    private static PaintFragment withClip(PaintFragment fragment, ClipRect clip) {
        ClipRect effective = intersect(fragment.clip(), clip);
        if (fragment instanceof BoxFragment box) {
            return new BoxFragment(box.box(), box.x(), box.y(), box.width(), box.height(), effective);
        }
        if (fragment instanceof InlineBoxFragment box) {
            return new InlineBoxFragment(box.box(), box.x(), box.y(), box.width(), box.height(),
                    box.firstFragment(), box.lastFragment(), effective);
        }
        if (fragment instanceof ImageFragment image) {
            return new ImageFragment(image.image(), image.bitmap(), image.x(), image.y(),
                    image.width(), image.height(), effective);
        }
        TextFragment text = (TextFragment) fragment;
        return new TextFragment(text.text(), text.x(), text.width(), text.baseline(), text.top(),
                text.height(), text.font(), text.color(), text.underline(),
                text.decorationColor(), effective);
    }

    private static ClipRect intersect(ClipRect first, ClipRect second) {
        if (first == null) {
            return second;
        }
        float left = Math.max(first.x(), second.x());
        float top = Math.max(first.y(), second.y());
        float right = Math.min(first.x() + first.width(), second.x() + second.width());
        float bottom = Math.min(first.y() + first.height(), second.y() + second.height());
        return new ClipRect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    private float flushInline(List<RenderNode> inlineNodes,
                              float x,
                              float y,
                              float width,
                              Float containingHeight,
                              RenderStyle.TextAlign textAlign,
                              Graphics2D graphics,
                              List<PaintFragment> target,
                              List<LineBox> lineBoxes) {
        if (inlineNodes.isEmpty()) {
            return 0;
        }
        InlineLayouter layouter = new InlineLayouter(
                x, y, width, containingHeight, textAlign, graphics, target, lineBoxes);
        layouter.layout(inlineNodes);
        inlineNodes.clear();
        return layouter.finish();
    }

    public record LayoutResult(int width,
                               float height,
                               List<PaintFragment> fragments,
                               List<LineBox> lineBoxes) {
        public LayoutResult {
            fragments = List.copyOf(fragments);
            lineBoxes = List.copyOf(lineBoxes);
        }
    }

    public interface PaintFragment {
        float top();
        float bottom();
        ClipRect clip();
    }

    public record ClipRect(float x, float y, float width, float height) {
    }

    public interface InlineFragment extends PaintFragment {
        float x();
        float width();
    }

    public record BoxFragment(RenderBox box, float x, float y, float width, float height,
                              ClipRect clip)
            implements PaintFragment {
        public BoxFragment(RenderBox box, float x, float y, float width, float height) {
            this(box, x, y, width, height, null);
        }
        @Override public float top() { return y; }
        @Override public float bottom() { return y + height; }
    }

    public record InlineBoxFragment(RenderInlineBox box,
                                    float x,
                                    float y,
                                    float width,
                                    float height,
                                    boolean firstFragment,
                                    boolean lastFragment,
                                    ClipRect clip) implements InlineFragment {
        public InlineBoxFragment(RenderInlineBox box, float x, float y, float width,
                                 float height, boolean firstFragment, boolean lastFragment) {
            this(box, x, y, width, height, firstFragment, lastFragment, null);
        }
        @Override public float top() { return y; }
        @Override public float bottom() { return y + height; }
    }

    public record TextFragment(String text,
                               float x,
                               float width,
                               float baseline,
                               float top,
                               float height,
                               Font font,
                               CssColor color,
                               boolean underline,
                               CssColor decorationColor,
                               ClipRect clip) implements InlineFragment {
        public TextFragment(String text, float x, float width, float baseline, float top,
                            float height, Font font, CssColor color, boolean underline,
                            CssColor decorationColor) {
            this(text, x, width, baseline, top, height, font, color, underline,
                    decorationColor, null);
        }
        @Override public float bottom() { return top + height; }
    }

    public record ImageFragment(RenderImage image,
                                BufferedImage bitmap,
                                float x,
                                float y,
                                float width,
                                float height,
                                ClipRect clip) implements InlineFragment {
        public ImageFragment(RenderImage image, BufferedImage bitmap, float x, float y,
                             float width, float height) {
            this(image, bitmap, x, y, width, height, null);
        }
        @Override public float top() { return y; }
        @Override public float bottom() { return y + height; }
    }

    public record LineBox(float x,
                          float y,
                          float width,
                          float height,
                          float baseline,
                          List<InlineFragment> fragments) {
        public LineBox {
            fragments = List.copyOf(fragments);
        }
    }

    private record BlockLayout(float outerHeight, List<PaintFragment> fragments) {
    }

    private record FlexLayout(float height, List<PaintFragment> fragments) {
    }

    private record FlexItemLayout(RenderBox box,
                                  BlockLayout layout,
                                  List<LineBox> lines) {
    }

    private record AxisSpacing(float offset, float gap) {
    }

    private record AbsoluteRequest(RenderBox box, float staticX, float staticY) {
    }

    private static final class PositionedContext {
        private final List<AbsoluteRequest> requests = new ArrayList<>();
        private float x;
        private float y;
        private float width;
        private float height;

        void setGeometry(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private record IntrinsicWidths(float preferred, float minimum) {
    }

    private record TableRow(RenderBox box, RenderBox group, List<RenderBox> cells) {
    }

    private record FloatRegion(RenderStyle.FloatMode mode,
                               float x,
                               float y,
                               float width,
                               float height) {
    }

    private record FloatArea(float x, float width) {
    }

    private sealed interface InlineToken
            permits OpenBoxToken, CloseBoxToken, AtomicBlockToken, WordToken, SpaceToken,
                    BreakToken, ImageToken {
    }

    private record OpenBoxToken(RenderInlineBox box) implements InlineToken {
    }

    private record CloseBoxToken(RenderInlineBox box) implements InlineToken {
    }

    private record AtomicBlockToken(RenderInlineBlock block) implements InlineToken {
    }

    private record WordToken(String text, RenderStyle style) implements InlineToken {
    }

    private record SpaceToken(RenderStyle style) implements InlineToken {
    }

    private record BreakToken(RenderStyle style) implements InlineToken {
    }

    private record ImageToken(RenderImage image) implements InlineToken {
    }

    private Font fontFor(RenderStyle style) {
        int awtStyle = Font.PLAIN;
        if (style.bold()) awtStyle |= Font.BOLD;
        if (style.italic()) awtStyle |= Font.ITALIC;
        Font webFont = webFontResolver.apply(style.fontFamily());
        if (webFont != null) {
            return webFont.deriveFont(awtStyle, Math.max(1f, style.fontSizePx()));
        }
        String family = switch (style.fontFamily().toLowerCase(java.util.Locale.ROOT)) {
            case "serif" -> Font.SERIF;
            case "sans-serif" -> Font.SANS_SERIF;
            case "monospace" -> Font.MONOSPACED;
            case "cursive", "fantasy", "system-ui" -> Font.DIALOG;
            default -> style.fontFamily();
        };
        return new Font(family, awtStyle, Math.max(1, Math.round(style.fontSizePx())));
    }

    private final class InlineLayouter {
        private final float startY;
        private final float x;
        private final float width;
        private final Float containingHeight;
        private final RenderStyle.TextAlign textAlign;
        private final Graphics2D graphics;
        private final List<PaintFragment> target;
        private final List<LineBox> lineTarget;
        private final List<RenderInlineBox> activeBoxes = new ArrayList<>();
        private final List<InlineToken> tokens = new ArrayList<>();
        private LineBuilder line;
        private float y;
        private boolean pendingSpace;
        private RenderStyle pendingSpaceStyle;

        InlineLayouter(float x,
                       float y,
                       float width,
                       Float containingHeight,
                       RenderStyle.TextAlign textAlign,
                       Graphics2D graphics,
                       List<PaintFragment> target,
                       List<LineBox> lineTarget) {
            this.x = x;
            this.y = y;
            this.startY = y;
            this.width = width;
            this.containingHeight = containingHeight;
            this.textAlign = textAlign;
            this.graphics = graphics;
            this.target = target;
            this.lineTarget = lineTarget;
            this.line = new LineBuilder(graphics, activeBoxes, width, containingHeight);
        }

        void layout(List<RenderNode> nodes) {
            appendTokens(nodes);
            for (int index = 0; index < tokens.size(); index++) {
                InlineToken token = tokens.get(index);
                if (token instanceof SpaceToken space) {
                    pendingSpace = true;
                    if (pendingSpaceStyle == null) {
                        pendingSpaceStyle = space.style();
                    }
                } else if (token instanceof OpenBoxToken open) {
                    openBox(open.box());
                } else if (token instanceof CloseBoxToken close) {
                    closeBox(close.box());
                } else if (token instanceof AtomicBlockToken atomic) {
                    addAtomicBlock(atomic.block());
                } else if (token instanceof ImageToken image) {
                    addImage(image.image());
                } else if (token instanceof BreakToken lineBreak) {
                    pendingSpace = false;
                    pendingSpaceStyle = null;
                    flushLine(true, lineBreak.style());
                } else if (token instanceof WordToken word) {
                    addWord(word.text(), word.style(), closingDecorationWidthAfter(index));
                }
            }
        }

        float finish() {
            pendingSpace = false;
            pendingSpaceStyle = null;
            flushLine(false, null);
            return y - startY;
        }

        private void appendTokens(List<RenderNode> nodes) {
            for (RenderNode node : nodes) {
                if (node instanceof RenderTextRun run) {
                    appendText(run.text(), run.style());
                } else if (node instanceof RenderLineBreak lineBreak) {
                    tokens.add(new BreakToken(lineBreak.style()));
                } else if (node instanceof RenderInlineBox inlineBox) {
                    tokens.add(new OpenBoxToken(inlineBox));
                    appendTokens(inlineBox.children());
                    tokens.add(new CloseBoxToken(inlineBox));
                } else if (node instanceof RenderInlineBlock inlineBlock) {
                    tokens.add(new AtomicBlockToken(inlineBlock));
                } else if (node instanceof RenderImage image) {
                    tokens.add(new ImageToken(image));
                }
            }
        }

        private void addImage(RenderImage image) {
            ImageLayout layout = imageLayout(image, width, containingHeight);
            float pendingWidth = pendingSpaceWidth();
            if (line.hasPlacedContent()
                    && line.width() + pendingWidth + layout.width() > width) {
                pendingSpace = false;
                pendingSpaceStyle = null;
                flushLine(false, null);
            } else {
                materializePendingSpace();
            }
            line.addImage(image, layout);
        }

        private void addAtomicBlock(RenderInlineBlock inlineBlock) {
            List<LineBox> atomicLines = new ArrayList<>();
            PositionedContext atomicContainingBlock = new PositionedContext();
            BlockLayout block = layoutBlock(
                    inlineBlock.box(), 0, 0, width, containingHeight, true,
                    graphics, atomicLines, atomicContainingBlock);
            atomicContainingBlock.setGeometry(0, 0, width, block.outerHeight());
            List<PaintFragment> atomicFragments = new ArrayList<>(block.fragments());
            atomicFragments.addAll(
                    layoutAbsoluteRequests(atomicContainingBlock, graphics, atomicLines));
            block = new BlockLayout(block.outerHeight(), List.copyOf(atomicFragments));
            BoxFragment root = (BoxFragment) block.fragments().getFirst();
            float atomicWidth = inlineBlock.box().style().margin().left()
                    + root.width() + inlineBlock.box().style().margin().right();
            float baselineOffset = inlineBlock.box().style().overflow()
                    != RenderStyle.Overflow.VISIBLE || atomicLines.isEmpty()
                    ? block.outerHeight()
                    : atomicLines.getLast().baseline();
            AtomicLayout atomic = new AtomicLayout(block, atomicLines, atomicWidth,
                    block.outerHeight(), inlineBlock.box().style().verticalAlign(),
                    inlineBlock.box().style().fontSizePx(), baselineOffset);

            float pendingWidth = pendingSpaceWidth();
            if (line.hasPlacedContent()
                    && line.width() + pendingWidth + atomic.width() > width) {
                pendingSpace = false;
                pendingSpaceStyle = null;
                flushLine(false, null);
            } else {
                materializePendingSpace();
            }
            line.addAtomic(atomic);
        }

        private void appendText(String text, RenderStyle style) {
            int offset = 0;
            while (offset < text.length()) {
                int codePoint = text.codePointAt(offset);
                if (Character.isWhitespace(codePoint)) {
                    do {
                        offset += Character.charCount(codePoint);
                        if (offset >= text.length()) {
                            break;
                        }
                        codePoint = text.codePointAt(offset);
                    } while (Character.isWhitespace(codePoint));
                    tokens.add(new SpaceToken(style));
                    continue;
                }

                int end = offset + Character.charCount(codePoint);
                while (end < text.length()) {
                    int next = text.codePointAt(end);
                    if (Character.isWhitespace(next)) {
                        break;
                    }
                    end += Character.charCount(next);
                }
                tokens.add(new WordToken(text.substring(offset, end), style));
                offset = end;
            }
        }

        private void openBox(RenderInlineBox box) {
            RenderStyle style = box.style();
            float pendingWidth = pendingSpaceWidth();
            float openingWidth = style.margin().left()
                    + style.borderWidth().left() + style.padding().left();
            if (line.hasPlacedContent()
                    && line.width() + pendingWidth + openingWidth > width) {
                pendingSpace = false;
                pendingSpaceStyle = null;
                flushLine(false, null);
            } else {
                materializePendingSpace();
            }

            line.openBox(box, true);
            activeBoxes.add(box);
        }

        private void closeBox(RenderInlineBox box) {
            pendingSpace = false;
            pendingSpaceStyle = null;
            line.closeBox(box, true);
            if (activeBoxes.isEmpty() || activeBoxes.getLast() != box) {
                throw new IllegalStateException("Unbalanced inline box: " + box.tagName());
            }
            activeBoxes.removeLast();
        }

        private void addWord(String word, RenderStyle style, float trailingDecorationWidth) {
            Font font = fontFor(style);
            FontMetrics metrics = graphics.getFontMetrics(font);
            float spaceWidth = pendingSpaceWidth();
            float wordWidth = metrics.stringWidth(word);

            if (line.hasPlacedContent()
                    && line.width() + spaceWidth + wordWidth + trailingDecorationWidth > width) {
                pendingSpace = false;
                pendingSpaceStyle = null;
                flushLine(false, null);
            }

            materializePendingSpace();
            int offset = 0;
            while (offset < word.length()) {
                float finalWidth = metrics.stringWidth(word.substring(offset));
                if (finalWidth + trailingDecorationWidth <= width - line.width()) {
                    line.addText(word.substring(offset), font, metrics, style);
                    return;
                }

                float remaining = Math.max(1, width - line.width());
                int end = longestFittingEnd(word, offset, metrics, remaining);
                line.addText(word.substring(offset, end), font, metrics, style);
                offset = end;
                if (offset < word.length()) {
                    flushLine(false, null);
                }
            }
        }

        private float closingDecorationWidthAfter(int tokenIndex) {
            float result = 0;
            for (int index = tokenIndex + 1; index < tokens.size(); index++) {
                InlineToken token = tokens.get(index);
                if (!(token instanceof CloseBoxToken close)) {
                    break;
                }
                RenderStyle style = close.box().style();
                result += style.padding().right() + style.borderWidth().right()
                        + style.margin().right();
            }
            return result;
        }

        private float pendingSpaceWidth() {
            if (!pendingSpace || !line.hasPlacedContent() || pendingSpaceStyle == null) {
                return 0;
            }
            return graphics.getFontMetrics(fontFor(pendingSpaceStyle)).stringWidth(" ");
        }

        private void materializePendingSpace() {
            if (pendingSpace && line.hasPlacedContent() && pendingSpaceStyle != null) {
                Font font = fontFor(pendingSpaceStyle);
                FontMetrics metrics = graphics.getFontMetrics(font);
                line.addText(" ", font, metrics, pendingSpaceStyle);
            }
            pendingSpace = false;
            pendingSpaceStyle = null;
        }

        private void flushLine(boolean force, RenderStyle fallbackStyle) {
            if (!line.hasContent()) {
                if (force && fallbackStyle != null) {
                    line.addStrut(fontFor(fallbackStyle), fallbackStyle);
                } else {
                    line = new LineBuilder(graphics, activeBoxes, width, containingHeight);
                    return;
                }
            }

            float alignmentOffset = switch (textAlign) {
                case CENTER -> Math.max(0, width - line.width()) / 2f;
                case RIGHT -> Math.max(0, width - line.width());
                case LEFT -> 0;
            };
            FinishedLine finished = line.finish(x + alignmentOffset, y);
            LineBox lineBox = finished.line();
            lineTarget.add(lineBox);
            lineTarget.addAll(finished.atomicLines());
            target.addAll(lineBox.fragments());
            target.addAll(finished.atomicFragments());
            y += lineBox.height();
            line = new LineBuilder(graphics, activeBoxes, width, containingHeight);
        }

        private static int longestFittingEnd(String text,
                                             int start,
                                             FontMetrics metrics,
                                             float availableWidth) {
            int codePointCount = text.codePointCount(start, text.length());
            int low = 1;
            int high = codePointCount;
            int bestCodePoints = 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                int end = text.offsetByCodePoints(start, middle);
                if (metrics.stringWidth(text.substring(start, end)) <= availableWidth) {
                    bestCodePoints = middle;
                    low = middle + 1;
                } else {
                    high = middle - 1;
                }
            }
            return text.offsetByCodePoints(start, bestCodePoints);
        }
    }

    private record AtomicLayout(BlockLayout block,
                                List<LineBox> lines,
                                float width,
                                float height,
                                RenderStyle.VerticalAlign verticalAlign,
                                float fontSize,
                                float baselineOffset) {
    }

    private record ImageLayout(BufferedImage bitmap,
                               float width,
                               float height,
                               RenderStyle.VerticalAlign verticalAlign,
                               float fontSize) {
    }

    private record FinishedLine(LineBox line,
                                List<PaintFragment> atomicFragments,
                                List<LineBox> atomicLines) {
    }

    private sealed interface LineItem permits TextItem, BoxItem, StrutItem, AtomicItem, ImageItem {
        float ascent();
        float descent();
        default RenderStyle.VerticalAlign verticalAlign() {
            return RenderStyle.VerticalAlign.BASELINE;
        }
        default float height() {
            return ascent() + descent();
        }
    }

    private record TextItem(String text,
                            float x,
                            float width,
                            Font font,
                            FontMetrics metrics,
                            CssColor color,
                            boolean underline,
                            CssColor decorationColor,
                            float usedLineHeight) implements LineItem {
        private float adjustment() {
            return usedLineHeight <= 0 ? 0 : (usedLineHeight - metrics.getHeight()) / 2f;
        }
        @Override public float ascent() { return Math.max(0, metrics.getAscent() + adjustment()); }
        @Override public float descent() {
            return Math.max(0, metrics.getDescent() + metrics.getLeading() + adjustment());
        }
    }

    private record StrutItem(FontMetrics metrics, float usedLineHeight) implements LineItem {
        private float adjustment() {
            return usedLineHeight <= 0 ? 0 : (usedLineHeight - metrics.getHeight()) / 2f;
        }
        @Override public float ascent() { return Math.max(0, metrics.getAscent() + adjustment()); }
        @Override public float descent() {
            return Math.max(0, metrics.getDescent() + metrics.getLeading() + adjustment());
        }
    }

    private record AtomicItem(AtomicLayout layout, float x) implements LineItem {
        @Override public float ascent() {
            if (layout.verticalAlign() == RenderStyle.VerticalAlign.MIDDLE) {
                return Math.min(layout.height(), layout.height() / 2f + layout.fontSize() / 4f);
            }
            return layout.baselineOffset();
        }
        @Override public float descent() {
            return layout.height() - ascent();
        }
        @Override public RenderStyle.VerticalAlign verticalAlign() {
            return layout.verticalAlign();
        }
        @Override public float height() { return layout.height(); }
    }

    private record ImageItem(RenderImage image, ImageLayout layout, float x) implements LineItem {
        @Override public float ascent() {
            if (layout.verticalAlign() == RenderStyle.VerticalAlign.MIDDLE) {
                return Math.min(layout.height(), layout.height() / 2f + layout.fontSize() / 4f);
            }
            return layout.height();
        }
        @Override public float descent() { return layout.height() - ascent(); }
        @Override public RenderStyle.VerticalAlign verticalAlign() {
            return layout.verticalAlign();
        }
        @Override public float height() { return layout.height(); }
    }

    private final class BoxItem implements LineItem {
        private final RenderInlineBox box;
        private final float x;
        private final boolean firstFragment;
        private final List<LineItem> children = new ArrayList<>();
        private float width;
        private boolean lastFragment;
        private float ascent;
        private float descent;

        BoxItem(RenderInlineBox box, float x, boolean firstFragment) {
            this.box = box;
            this.x = x;
            this.firstFragment = firstFragment;
        }

        void finish(float endX, boolean lastFragment, Graphics2D graphics) {
            this.width = Math.max(0, endX - x);
            this.lastFragment = lastFragment;
            calculateMetrics(graphics);
        }

        void calculateMetrics(Graphics2D graphics) {
            FontMetrics ownMetrics = graphics.getFontMetrics(fontFor(box.style()));
            float contentAscent = ownMetrics.getAscent();
            float contentDescent = ownMetrics.getDescent() + ownMetrics.getLeading();
            for (LineItem child : children) {
                if (child instanceof BoxItem childBox) {
                    childBox.calculateMetrics(graphics);
                }
                contentAscent = Math.max(contentAscent, child.ascent());
                contentDescent = Math.max(contentDescent, child.descent());
            }
            ascent = contentAscent + box.style().padding().top() + box.style().borderWidth().top();
            descent = contentDescent + box.style().padding().bottom()
                    + box.style().borderWidth().bottom();
        }

        @Override public float ascent() { return ascent; }
        @Override public float descent() { return descent; }
    }

    private final class LineBuilder {
        private final Graphics2D graphics;
        private final float containingWidth;
        private final Float containingHeight;
        private final List<LineItem> roots = new ArrayList<>();
        private final List<BoxItem> active = new ArrayList<>();
        private float width;
        private boolean placedContent;
        private boolean structuralContent;

        LineBuilder(Graphics2D graphics,
                    List<RenderInlineBox> continuingBoxes,
                    float containingWidth,
                    Float containingHeight) {
            this.graphics = graphics;
            this.containingWidth = containingWidth;
            this.containingHeight = containingHeight;
            for (RenderInlineBox box : continuingBoxes) {
                openBox(box, false);
            }
        }

        float width() {
            return width;
        }

        boolean hasPlacedContent() {
            return placedContent;
        }

        boolean hasContent() {
            return placedContent || structuralContent;
        }

        void openBox(RenderInlineBox box, boolean firstFragment) {
            RenderStyle style = box.style();
            if (firstFragment) {
                width += style.margin().left();
            }
            BoxItem item = new BoxItem(box, width, firstFragment);
            addItem(item);
            active.add(item);
            structuralContent = true;
            if (firstFragment) {
                width += style.borderWidth().left() + style.padding().left();
            }
        }

        void closeBox(RenderInlineBox box, boolean lastFragment) {
            if (active.isEmpty() || active.getLast().box != box) {
                throw new IllegalStateException("Unbalanced inline layout box: " + box.tagName());
            }
            RenderStyle style = box.style();
            if (lastFragment) {
                width += style.padding().right() + style.borderWidth().right();
            }
            BoxItem item = active.removeLast();
            item.finish(width, lastFragment, graphics);
            placedContent = true;
            if (lastFragment) {
                width += style.margin().right();
            }
        }

        void addText(String text,
                     Font font,
                     FontMetrics metrics,
                     RenderStyle style) {
            float itemWidth = metrics.stringWidth(text);
            addItem(new TextItem(text, width, itemWidth, font, metrics, style.color(),
                    style.underline(), style.textDecorationColor(), style.usedLineHeightPx()));
            width += itemWidth;
            placedContent = true;
        }

        void addStrut(Font font, RenderStyle style) {
            addItem(new StrutItem(graphics.getFontMetrics(font), style.usedLineHeightPx()));
            placedContent = true;
        }

        void addAtomic(AtomicLayout atomic) {
            addItem(new AtomicItem(atomic, width));
            width += atomic.width();
            placedContent = true;
        }

        void addImage(RenderImage image, ImageLayout layout) {
            addItem(new ImageItem(image, layout, width));
            width += layout.width();
            placedContent = true;
        }

        FinishedLine finish(float lineX, float lineY) {
            for (BoxItem box : active) {
                box.finish(width, false, graphics);
            }
            for (LineItem root : roots) {
                if (root instanceof BoxItem box) {
                    box.calculateMetrics(graphics);
                }
            }

            float ascent = 0;
            float descent = 0;
            float edgeAlignedHeight = 0;
            for (LineItem item : roots) {
                if (item.verticalAlign() == RenderStyle.VerticalAlign.TOP
                        || item.verticalAlign() == RenderStyle.VerticalAlign.BOTTOM) {
                    edgeAlignedHeight = Math.max(edgeAlignedHeight, item.height());
                } else {
                    ascent = Math.max(ascent, item.ascent());
                    descent = Math.max(descent, item.descent());
                }
            }
            if (ascent + descent < edgeAlignedHeight) {
                descent += edgeAlignedHeight - ascent - descent;
            }
            float baseline = lineY + ascent;
            float height = ascent + descent;

            List<InlineFragment> fragments = new ArrayList<>();
            collectBoxFragments(roots, fragments, lineX, baseline, 0, 0);
            collectTextFragments(roots, fragments, lineX, baseline, 0, 0);
            collectImageFragments(roots, fragments, lineX, lineY, height, baseline, 0, 0);
            List<PaintFragment> atomicFragments = new ArrayList<>();
            List<LineBox> atomicLines = new ArrayList<>();
            collectAtomicFragments(
                    roots, atomicFragments, atomicLines, lineX, lineY, height, baseline, 0, 0);
            return new FinishedLine(
                    new LineBox(lineX, lineY, width, height, baseline, fragments),
                    atomicFragments,
                    atomicLines);
        }

        private void addItem(LineItem item) {
            if (active.isEmpty()) {
                roots.add(item);
            } else {
                active.getLast().children.add(item);
            }
        }

        private void collectBoxFragments(List<LineItem> items,
                                                List<InlineFragment> fragments,
                                                float lineX,
                                                float baseline,
                                                float inheritedDx,
                                                float inheritedDy) {
            for (LineItem item : items) {
                if (item instanceof BoxItem box) {
                    float dx = inheritedDx + inlineOffsetX(box.box.style(), containingWidth);
                    float dy = inheritedDy + inlineOffsetY(box.box.style(), containingHeight);
                    fragments.add(new InlineBoxFragment(
                            box.box,
                            lineX + box.x + dx,
                            baseline - box.ascent + dy,
                            box.width,
                            box.ascent + box.descent,
                            box.firstFragment,
                            box.lastFragment));
                    collectBoxFragments(box.children, fragments, lineX, baseline, dx, dy);
                }
            }
        }

        private void collectTextFragments(List<LineItem> items,
                                                 List<InlineFragment> fragments,
                                                 float lineX,
                                                 float baseline,
                                                 float inheritedDx,
                                                 float inheritedDy) {
            for (LineItem item : items) {
                if (item instanceof TextItem text) {
                    fragments.add(new TextFragment(
                            text.text,
                            lineX + text.x + inheritedDx,
                            text.width,
                            baseline + inheritedDy,
                            baseline - text.metrics.getAscent() + inheritedDy,
                            text.metrics.getHeight(),
                            text.font,
                            text.color,
                            text.underline,
                            text.decorationColor));
                } else if (item instanceof BoxItem box) {
                    float dx = inheritedDx + inlineOffsetX(box.box.style(), containingWidth);
                    float dy = inheritedDy + inlineOffsetY(box.box.style(), containingHeight);
                    collectTextFragments(box.children, fragments, lineX, baseline, dx, dy);
                }
            }
        }

        private void collectImageFragments(List<LineItem> items,
                                                  List<InlineFragment> fragments,
                                                  float lineX,
                                                  float lineY,
                                                  float lineHeight,
                                                  float baseline,
                                                  float inheritedDx,
                                                  float inheritedDy) {
            for (LineItem item : items) {
                if (item instanceof ImageItem image) {
                    float top = switch (image.verticalAlign()) {
                        case TOP -> lineY;
                        case BOTTOM -> lineY + lineHeight - image.height();
                        case BASELINE, MIDDLE -> baseline - image.ascent();
                    };
                    fragments.add(new ImageFragment(image.image(), image.layout().bitmap(),
                            lineX + image.x() + inheritedDx, top + inheritedDy,
                            image.layout().width(), image.layout().height()));
                } else if (item instanceof BoxItem box) {
                    float dx = inheritedDx + inlineOffsetX(box.box.style(), containingWidth);
                    float dy = inheritedDy + inlineOffsetY(box.box.style(), containingHeight);
                    collectImageFragments(box.children, fragments, lineX, lineY,
                            lineHeight, baseline, dx, dy);
                }
            }
        }

        private void collectAtomicFragments(List<LineItem> items,
                                                   List<PaintFragment> fragments,
                                                   List<LineBox> lines,
                                                   float lineX,
                                                   float lineY,
                                                   float lineHeight,
                                                   float baseline,
                                                   float inheritedDx,
                                                   float inheritedDy) {
            for (LineItem item : items) {
                if (item instanceof AtomicItem atomic) {
                    float dx = lineX + atomic.x() + inheritedDx;
                    float dy = inheritedDy + switch (atomic.verticalAlign()) {
                        case TOP -> lineY;
                        case BOTTOM -> lineY + lineHeight - atomic.height();
                        case BASELINE, MIDDLE -> baseline - atomic.ascent();
                    };
                    for (PaintFragment fragment : atomic.layout().block().fragments()) {
                        fragments.add(translate(fragment, dx, dy));
                    }
                    for (LineBox line : atomic.layout().lines()) {
                        lines.add(translate(line, dx, dy));
                    }
                } else if (item instanceof BoxItem box) {
                    float dx = inheritedDx + inlineOffsetX(box.box.style(), containingWidth);
                    float dy = inheritedDy + inlineOffsetY(box.box.style(), containingHeight);
                    collectAtomicFragments(box.children, fragments, lines, lineX, lineY,
                            lineHeight, baseline, dx, dy);
                }
            }
        }

        private float inlineOffsetX(RenderStyle style, float containingWidth) {
            return style.position() == RenderStyle.Position.RELATIVE
                    ? relativeHorizontalOffset(style, containingWidth) : 0;
        }

        private float inlineOffsetY(RenderStyle style, Float containingHeight) {
            return style.position() == RenderStyle.Position.RELATIVE
                    ? relativeVerticalOffset(style, containingHeight) : 0;
        }

        private static PaintFragment translate(PaintFragment fragment, float dx, float dy) {
            if (fragment instanceof BoxFragment box) {
                return new BoxFragment(
                        box.box(), box.x() + dx, box.y() + dy, box.width(), box.height(),
                        translate(box.clip(), dx, dy));
            }
            if (fragment instanceof InlineBoxFragment box) {
                return new InlineBoxFragment(box.box(), box.x() + dx, box.y() + dy,
                        box.width(), box.height(), box.firstFragment(), box.lastFragment(),
                        translate(box.clip(), dx, dy));
            }
            if (fragment instanceof ImageFragment image) {
                return new ImageFragment(image.image(), image.bitmap(), image.x() + dx,
                        image.y() + dy, image.width(), image.height(),
                        translate(image.clip(), dx, dy));
            }
            TextFragment text = (TextFragment) fragment;
            return new TextFragment(text.text(), text.x() + dx, text.width(),
                    text.baseline() + dy, text.top() + dy, text.height(), text.font(),
                    text.color(), text.underline(), text.decorationColor(),
                    translate(text.clip(), dx, dy));
        }

        private static ClipRect translate(ClipRect clip, float dx, float dy) {
            return clip == null ? null
                    : new ClipRect(clip.x() + dx, clip.y() + dy, clip.width(), clip.height());
        }

        private static LineBox translate(LineBox line, float dx, float dy) {
            List<InlineFragment> fragments = line.fragments().stream()
                    .map(fragment -> (InlineFragment) translate(fragment, dx, dy))
                    .toList();
            return new LineBox(line.x() + dx, line.y() + dy, line.width(), line.height(),
                    line.baseline() + dy, fragments);
        }
    }
}

package com.browicy.ui.render;

import com.browicy.engine.render.BoxEdges;
import com.browicy.engine.render.CssColor;
import com.browicy.engine.render.RenderBox;
import com.browicy.engine.render.RenderInlineBox;
import com.browicy.engine.render.RenderInlineBlock;
import com.browicy.engine.render.RenderImage;
import com.browicy.engine.render.RenderLength;
import com.browicy.engine.render.RenderNode;
import com.browicy.engine.render.RenderOffset;
import com.browicy.engine.render.RenderStyle;
import com.browicy.engine.render.RenderTextRun;
import com.browicy.engine.render.RenderTree;
import com.browicy.engine.render.Transform;
import com.browicy.ui.render.FloatExclusionSpace.FloatArea;
import com.browicy.ui.render.FloatExclusionSpace.FloatRegion;
import com.browicy.ui.render.FloatExclusionSpace.LineSlot;
import com.browicy.ui.render.PositionedLayout.AbsoluteRequest;
import com.browicy.ui.render.PositionedLayout.PositionedContext;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Function;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import static com.browicy.ui.render.PositionedLayout.fragmentLeft;
import static com.browicy.ui.render.PositionedLayout.fragmentRight;
import static com.browicy.ui.render.PositionedLayout.fragmentZIndex;
import static com.browicy.ui.render.PositionedLayout.mergeElevated;
import static com.browicy.ui.render.PositionedLayout.translate;
import static com.browicy.ui.render.PositionedLayout.translateLines;
import static com.browicy.ui.render.PositionedLayout.withClip;
import static com.browicy.ui.render.PositionedLayout.withTransform;

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
    private final InlineLayout.Host inlineServices = new InlineLayout.Host() {
        @Override
        public Font fontFor(RenderStyle style) {
            return RenderLayoutEngine.this.fontFor(style);
        }

        @Override
        public ImageLayout imageLayout(RenderImage image, float percentageBase,
                                       Float containingHeight) {
            return RenderLayoutEngine.this.imageLayout(image, percentageBase, containingHeight);
        }

        @Override
        public AtomicLayout layoutAtomic(RenderInlineBlock inlineBlock, float width,
                                         Float containingHeight, Graphics2D graphics) {
            return RenderLayoutEngine.this.layoutAtomic(
                    inlineBlock, width, containingHeight, graphics);
        }

        @Override
        public float relativeHorizontalOffset(RenderStyle style,
                                              float containingWidth) {
            return RenderLayoutEngine.this.relativeHorizontalOffset(style, containingWidth);
        }

        @Override
        public float relativeVerticalOffset(RenderStyle style, Float containingHeight) {
            return RenderLayoutEngine.this.relativeVerticalOffset(style, containingHeight);
        }
    };

    private final TableLayout tableLayout = new TableLayout(new TableLayout.Host() {
        @Override
        public BlockLayout layoutBlock(RenderBox box, float containingX, float y,
                                       float availableWidth, Float containingHeight, boolean shrinkToFitAuto,
                                       Graphics2D graphics, List<LineBox> lineBoxes,
                                       PositionedContext positionedContext) {
            return RenderLayoutEngine.this.layoutBlock(box, containingX, y, availableWidth,
                    containingHeight, shrinkToFitAuto, graphics, lineBoxes, positionedContext);
        }

        @Override
        public IntrinsicWidths intrinsicBoxWidth(RenderBox box, float percentageBase,
                                                 Graphics2D graphics, boolean contentBased) {
            return RenderLayoutEngine.this.intrinsicBoxWidth(
                    box, percentageBase, graphics, contentBased);
        }

        @Override
        public float resolve(RenderLength length, float percentageBase) {
            return RenderLayoutEngine.this.resolve(length, percentageBase);
        }

        @Override
        public Float resolveContentHeight(RenderStyle style, RenderLength length,
                                          Float containingHeight, float decoration) {
            return RenderLayoutEngine.this.resolveContentHeight(
                    style, length, containingHeight, decoration);
        }
    });

    private final PositionedLayout positionedLayout = new PositionedLayout(new PositionedLayout.Host() {
        @Override
        public BlockLayout layoutBlock(RenderBox box, float containingX, float y,
                                       float availableWidth, Float containingHeight, boolean shrinkToFitAuto,
                                       Graphics2D graphics, List<LineBox> lineBoxes,
                                       PositionedContext positionedContext) {
            return RenderLayoutEngine.this.layoutBlock(box, containingX, y, availableWidth,
                    containingHeight, shrinkToFitAuto, graphics, lineBoxes, positionedContext);
        }

        @Override
        public float resolve(RenderOffset offset, float percentageBase) {
            return RenderLayoutEngine.this.resolve(offset, percentageBase);
        }
    });

    private final FlexLayoutEngine flexLayoutEngine = new FlexLayoutEngine(new FlexLayoutEngine.Host() {
        @Override
        public BlockLayout layoutBlock(RenderBox box, float containingX, float y,
                                       float availableWidth, Float containingHeight, boolean shrinkToFitAuto,
                                       Graphics2D graphics, List<LineBox> lineBoxes,
                                       PositionedContext positionedContext) {
            return RenderLayoutEngine.this.layoutBlock(box, containingX, y, availableWidth,
                    containingHeight, shrinkToFitAuto, graphics, lineBoxes, positionedContext);
        }

        @Override
        public IntrinsicWidths intrinsicBoxWidth(RenderBox box, float percentageBase,
                                                 Graphics2D graphics, boolean contentBased) {
            return RenderLayoutEngine.this.intrinsicBoxWidth(
                    box, percentageBase, graphics, contentBased);
        }

        @Override
        public IntrinsicWidths intrinsicWidths(List<RenderNode> nodes,
                                               float percentageBase, Graphics2D graphics, boolean contentBased) {
            return RenderLayoutEngine.this.intrinsicWidths(
                    nodes, percentageBase, graphics, contentBased);
        }

        @Override
        public Float resolveBoxConstraint(RenderStyle style, RenderLength length,
                                          RenderBox box, float percentageBase, float decoration, Graphics2D graphics) {
            return RenderLayoutEngine.this.resolveBoxConstraint(
                    style, length, box, percentageBase, decoration, graphics);
        }

        @Override
        public float resolve(RenderLength length, float percentageBase) {
            return RenderLayoutEngine.this.resolve(length, percentageBase);
        }
    }, positionedLayout);

    private final GridLayoutEngine gridLayoutEngine = new GridLayoutEngine(new GridLayoutEngine.Host() {
        @Override
        public BlockLayout layoutBlock(RenderBox box, float containingX, float y,
                                       float availableWidth, Float containingHeight, boolean shrinkToFitAuto,
                                       Graphics2D graphics, List<LineBox> lineBoxes,
                                       PositionedContext positionedContext) {
            return RenderLayoutEngine.this.layoutBlock(box, containingX, y, availableWidth,
                    containingHeight, shrinkToFitAuto, graphics, lineBoxes, positionedContext);
        }
    });

    public RenderLayoutEngine() {
        this(ignored -> null);
    }

    public RenderLayoutEngine(Function<String, Font> webFontResolver) {
        this.webFontResolver = java.util.Objects.requireNonNull(webFontResolver, "webFontResolver");
    }

    public synchronized LayoutResult layout(RenderTree tree, int viewportWidth, Insets insets,
                                            Graphics2D graphics) {
        rootFontSizePx = tree.rootFontSizePx();
        this.viewportWidth = viewportWidth;
        this.viewportHeight = tree.viewportHeight();
        float availableWidth = Math.max(1, viewportWidth);
        List<LineBox> lineBoxes = new ArrayList<>();
        PositionedContext initialContainingBlock = new PositionedContext();
        initialContainingBlock.setGeometry(0, 0, viewportWidth, this.viewportHeight);
        int rootFirstLine = lineBoxes.size();
        BlockLayout root = layoutBlock(
                tree.root(), 0, 0, availableWidth, this.viewportHeight, false,
                graphics, lineBoxes, initialContainingBlock);
        root = positionedLayout.positionRootInInitialContainingBlock(
                root, tree.root(), initialContainingBlock, graphics, lineBoxes, rootFirstLine);
        List<PaintFragment> positioned =
                positionedLayout.layoutAbsoluteRequests(initialContainingBlock, graphics, lineBoxes);
        List<PaintFragment> negative = new ArrayList<>();
        List<PaintFragment> zero = new ArrayList<>();
        List<PaintFragment> positive = new ArrayList<>();
        for (PaintFragment fragment : positioned) {
            int z = fragmentZIndex(fragment);
            if (z < 0) negative.add(fragment);
            else if (z == 0) zero.add(fragment);
            else positive.add(fragment);
        }
        List<PaintFragment> fragments = new ArrayList<>(
                root.fragments().size() + positioned.size());
        fragments.addAll(negative);
        fragments.addAll(root.fragments());
        fragments.addAll(zero);
        fragments.addAll(positive);
        float height = Math.max(0, root.outerHeight());
        return new LayoutResult(viewportWidth, height, fragments, lineBoxes);
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
        return layoutBlock(box, containingX, y, availableWidth, availableWidth,
                containingHeight, shrinkToFitAuto, graphics, lineBoxes,
                positionedContext, false, null);
    }

    private BlockLayout layoutBlock(RenderBox box,
                                    float containingX,
                                    float y,
                                    float availableWidth,
                                    Float containingHeight,
                                    boolean shrinkToFitAuto,
                                    Graphics2D graphics,
                                    List<LineBox> lineBoxes,
                                    PositionedContext positionedContext,
                                    boolean collapseTopMargin) {
        return layoutBlock(box, containingX, y, availableWidth, availableWidth,
                containingHeight, shrinkToFitAuto, graphics, lineBoxes,
                positionedContext, collapseTopMargin, null);
    }

    private BlockLayout layoutBlock(RenderBox box,
                                    float containingX,
                                    float y,
                                    float availableWidth,
                                    float percentageBase,
                                    Float containingHeight,
                                    boolean shrinkToFitAuto,
                                    Graphics2D graphics,
                                    List<LineBox> lineBoxes,
                                    PositionedContext positionedContext,
                                    boolean collapseTopMargin,
                                    FloatExclusionSpace bfcFloats) {
        int firstLine = lineBoxes.size();
        RenderStyle style = box.style();
        if (style.display() == RenderStyle.Display.TABLE) {
            return tableLayout.layoutTable(box, containingX, y, availableWidth, percentageBase,
                    containingHeight, graphics, lineBoxes, positionedContext);
        }
        boolean positioned = switch (style.position()) {
            case STATIC -> false;
            default -> true;
        };
        PositionedContext childPositionedContext = positioned
                ? new PositionedContext() : positionedContext;
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
        } else if (style.width().unit() == RenderLength.Unit.MAX_CONTENT) {
            contentBoxWidth = shrinkToFitWidth(box, availableContentWidth, graphics);
        } else if (style.width().unit() == RenderLength.Unit.MIN_CONTENT) {
            contentBoxWidth = Math.max(1,
                    intrinsicWidths(box.children(), availableContentWidth, graphics, false).minimum());
        } else {
            contentBoxWidth = contentBoxDimension(
                    style, resolve(style.width(), percentageBase), horizontalDecoration);
        }
        contentBoxWidth = constrain(contentBoxWidth,
                resolveBoxConstraint(
                        style, style.minWidth(), box, percentageBase, horizontalDecoration,
                        graphics),
                resolveBoxConstraint(
                        style, style.maxWidth(), box, percentageBase, horizontalDecoration,
                        graphics));
        float borderBoxWidth = contentBoxWidth + horizontalDecoration;
        float freeWidth = Math.max(0,
                availableWidth - borderBoxWidth - margin.horizontal());
        float automaticLeft = style.autoMargins().left()
                ? (style.autoMargins().right() ? freeWidth / 2f : freeWidth)
                : 0;
        float borderX = containingX + margin.left() + automaticLeft;
        float borderY = y + (collapseTopMargin ? 0 : effectiveTopMargin(box));
        float contentX = borderX + border.left() + padding.left();
        float contentY = borderY + border.top() + padding.top();
        float contentWidth = Math.max(1, contentBoxWidth);

        float verticalDecoration = border.vertical() + padding.vertical();
        Float specifiedContentHeight = resolveContentHeight(
                style, style.height(), containingHeight, verticalDecoration);
        if (specifiedContentHeight == null && Float.isFinite(style.aspectRatio())) {
            specifiedContentHeight = style.boxSizing() == RenderStyle.BoxSizing.BORDER_BOX
                    ? Math.max(0, borderBoxWidth / style.aspectRatio() - verticalDecoration)
                    : contentBoxWidth / style.aspectRatio();
        }
        Float childContainingHeight = specifiedContentHeight == null
                ? null
                : constrain(specifiedContentHeight,
                resolveContentHeight(
                        style, style.minHeight(), containingHeight, verticalDecoration),
                resolveContentHeight(
                        style, style.maxHeight(), containingHeight, verticalDecoration));

        List<PaintFragment> childFragments = new ArrayList<>();
        List<PaintFragment> elevatedFragments = new ArrayList<>();
        float naturalContentHeight;
        if (style.display() == RenderStyle.Display.FLEX
                || style.display() == RenderStyle.Display.INLINE_FLEX) {
            FlexLayoutEngine.FlexLayout flex = flexLayoutEngine.layoutFlex(box, contentX,
                    contentY, contentWidth, childContainingHeight, graphics, lineBoxes,
                    childPositionedContext);
            childFragments.addAll(flex.fragments());
            elevatedFragments.addAll(flex.elevated());
            naturalContentHeight = flex.height();
        } else if (style.display() == RenderStyle.Display.GRID
                || style.display() == RenderStyle.Display.INLINE_GRID) {
            GridLayoutEngine.GridLayout grid = gridLayoutEngine.layoutGrid(box, contentX,
                    contentY, contentWidth, childContainingHeight, graphics, lineBoxes,
                    childPositionedContext);
            childFragments.addAll(grid.fragments());
            elevatedFragments.addAll(grid.elevated());
            naturalContentHeight = grid.height();
        } else {
            FloatExclusionSpace floats = bfcFloats == null ? new FloatExclusionSpace() : bfcFloats;
            naturalContentHeight = layoutBlockChildren(box, contentX, contentY, contentWidth,
                    childContainingHeight, graphics, lineBoxes, childPositionedContext,
                    childFragments, floats);
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
        float outerHeight = Math.max(0,
                (collapseTopMargin ? 0 : effectiveTopMargin(box))
                        + borderBoxHeight + effectiveBottomMargin(box));

        if (childPositionedContext != positionedContext) {
            childPositionedContext.setGeometry(
                    borderX + border.left(), borderY + border.top(),
                    Math.max(0, borderBoxWidth - border.horizontal()),
                    Math.max(0, borderBoxHeight - border.vertical()));
            float minLeft = Float.POSITIVE_INFINITY;
            float maxRight = Float.NEGATIVE_INFINITY;
            for (PaintFragment fragment : childFragments) {
                minLeft = Math.min(minLeft, fragmentLeft(fragment));
                maxRight = Math.max(maxRight, fragmentRight(fragment));
            }
            childPositionedContext.setContentExtent(minLeft, maxRight);
            List<PaintFragment> positionedFragments =
                    positionedLayout.layoutAbsoluteRequests(childPositionedContext, graphics, lineBoxes);
            List<PaintFragment> negative = new ArrayList<>();
            List<PaintFragment> zero = new ArrayList<>();
            List<PaintFragment> positive = new ArrayList<>();
            for (PaintFragment fragment : positionedFragments) {
                int z = fragmentZIndex(fragment);
                if (z < 0) negative.add(fragment);
                else if (z == 0) zero.add(fragment);
                else positive.add(fragment);
            }
            List<PaintFragment> ordered = new ArrayList<>(
                    childFragments.size() + positionedFragments.size());
            ordered.addAll(negative);
            ordered.addAll(childFragments);
            ordered.addAll(zero);
            ordered.addAll(mergeElevated(elevatedFragments, positive));
            childFragments = ordered;
        } else {
            childFragments.addAll(elevatedFragments);
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
        if (style.position() == RenderStyle.Position.RELATIVE
                || style.position() == RenderStyle.Position.STICKY) {
            float dx = relativeHorizontalOffset(style, availableWidth);
            float dy = relativeVerticalOffset(style, containingHeight);
            fragments.replaceAll(fragment -> translate(fragment, dx, dy));
            translateLines(lineBoxes, firstLine, dx, dy);
        }
        Transform transform = style.transform();
        if (transform != null && !transform.isIdentity()) {
            java.awt.geom.AffineTransform matrix = transform.matrix(
                    borderX, borderY, borderBoxWidth, borderBoxHeight,
                    rootFontSizePx, viewportWidth, viewportHeight);
            fragments.replaceAll(fragment -> withTransform(fragment, matrix));
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
                                      List<PaintFragment> childFragments,
                                      FloatExclusionSpace floats) {
        List<RenderNode> inlineBuffer = new ArrayList<>();
        List<PaintFragment> deferredFloats = new ArrayList<>();
        float currentY = contentY;
        Float previousBottomMargin = null;
        RenderBox firstBlockChild = firstInFlowBlockChild(box);
        RenderBox lastBlockChild = lastInFlowBlockChild(box);
        boolean parentCollapsesTop = collapsesWithChildren(box.style());
        boolean parentCollapsesBottom = collapsesWithChildren(box.style());
        for (RenderNode child : box.children()) {
            if (child instanceof RenderBox childBox) {
                float inlineHeight = flushInline(inlineBuffer, contentX, contentWidth, currentY,
                        childContainingHeight, box.style().textAlign(), floats, graphics,
                        childFragments, lineBoxes);
                currentY += inlineHeight;
                if (inlineHeight > 0) previousBottomMargin = null;
                if (childBox.style().position() == RenderStyle.Position.ABSOLUTE
                        || childBox.style().position() == RenderStyle.Position.FIXED) {
                    childPositionedContext.requests.add(
                            new AbsoluteRequest(childBox, contentX, currentY));
                    continue;
                }
                float placementY = floats.clearedY(currentY, childBox.style().clear());
                FloatArea blockArea = floats.floatArea(contentX, contentWidth, placementY);
                float blockMinimum = floats.isEmpty() ? 0
                        : intrinsicBoxWidth(childBox, contentWidth, graphics, false, true).minimum();
                boolean childEstablishesBfc = establishesBfc(childBox);
                if (blockArea.width() < Math.max(1, blockMinimum)) {
                    boolean floatSticksOut = switch (childBox.style().floatMode()) {
                        case LEFT -> blockArea.x() <= contentX + 0.5f
                                && blockArea.x() + blockArea.width()
                                >= contentX + contentWidth - 0.5f;
                        case RIGHT -> blockArea.x() + blockArea.width()
                                >= contentX + contentWidth - 0.5f
                                && blockArea.x() <= contentX + 0.5f;
                        case NONE -> false;
                    };
                    if (!floatSticksOut) {
                        if (childEstablishesBfc
                                && childBox.style().floatMode() == RenderStyle.FloatMode.NONE) {
                            boolean shrinkToFitSized = switch (childBox.style().display()) {
                                case TABLE, INLINE_TABLE, INLINE_BLOCK -> true;
                                default -> false;
                            };
                            if (shrinkToFitSized || !childBox.style().width().isAuto()) {
                                placementY = floats.firstFitY(contentX, contentWidth,
                                        placementY, blockMinimum);
                                blockArea = floats.floatArea(contentX, contentWidth,
                                        placementY);
                            }
                        } else {
                            placementY = floats.clearedY(placementY, RenderStyle.Clear.BOTH);
                            blockArea = floats.floatArea(contentX, contentWidth, placementY);
                        }
                    }
                }
                if (childBox.style().floatMode() != RenderStyle.FloatMode.NONE) {
                    int floatFirstLine = lineBoxes.size();
                    BlockLayout floatLayout = layoutBlock(
                            childBox, blockArea.x(), placementY, blockArea.width(),
                            childContainingHeight, true, graphics, lineBoxes,
                            childPositionedContext);
                    BoxFragment root = (BoxFragment) floatLayout.fragments().getFirst();
                    float desiredX = childBox.style().floatMode() == RenderStyle.FloatMode.LEFT
                            ? blockArea.x() + childBox.style().margin().left()
                            : blockArea.x() + blockArea.width()
                            - childBox.style().margin().right() - root.width();
                    float dx = desiredX - root.x();
                    floatLayout.fragments().stream().map(fragment -> translate(fragment, dx, 0))
                            .forEach(deferredFloats::add);
                    translateLines(lineBoxes, floatFirstLine, dx, 0);
                    floats.add(new FloatRegion(
                            childBox.style().floatMode(),
                            desiredX - childBox.style().margin().left(), placementY,
                            root.width() + childBox.style().margin().horizontal(),
                            floatLayout.outerHeight()));
                    previousBottomMargin = null;
                    continue;
                }
                currentY = placementY;
                float collapsedOverlap = previousBottomMargin == null ? 0
                        : previousBottomMargin + effectiveTopMargin(childBox)
                        - Math.max(previousBottomMargin, effectiveTopMargin(childBox));
                currentY -= collapsedOverlap;
                boolean collapseTop = parentCollapsesTop && childBox == firstBlockChild;
                BlockLayout childLayout;
                if (childEstablishesBfc) {
                    int bfcFirstLine = lineBoxes.size();
                    int bfcRequests = childPositionedContext.requests.size();
                    float childY = currentY;
                    childLayout = layoutBlock(
                            childBox, blockArea.x(), childY, blockArea.width(),
                            contentWidth, childContainingHeight, false, graphics, lineBoxes,
                            childPositionedContext, collapseTop, null);
                    for (int attempt = 0; attempt < 2; attempt++) {
                        float measuredHeight = childLayout.outerHeight();
                        float measuredWidth = childLayout.fragments().getFirst() instanceof BoxFragment root
                                ? root.width() + childBox.style().margin().horizontal() : blockArea.width();
                        LineSlot slot = floats.lineSlot(contentX, contentWidth, currentY,
                                Math.max(1, measuredHeight), Math.max(1, measuredWidth));
                        boolean moved = slot.y() > currentY + 0.5f
                                || Math.abs(slot.x() - blockArea.x()) > 0.5f
                                || Math.abs(slot.width() - blockArea.width()) > 0.5f;
                        if (!moved) {
                            break;
                        }
                        while (lineBoxes.size() > bfcFirstLine) {
                            lineBoxes.remove(lineBoxes.size() - 1);
                        }
                        while (childPositionedContext.requests.size() > bfcRequests) {
                            childPositionedContext.requests.remove(
                                    childPositionedContext.requests.size() - 1);
                        }
                        blockArea = new FloatArea(slot.x(), slot.width());
                        childY = slot.y();
                        childLayout = layoutBlock(
                                childBox, blockArea.x(), childY, blockArea.width(),
                                contentWidth, childContainingHeight, false, graphics,
                                lineBoxes, childPositionedContext, collapseTop, null);
                    }
                    currentY = childY;
                } else {
                    childLayout = layoutBlock(
                            childBox, contentX, currentY, contentWidth, contentWidth,
                            childContainingHeight, false, graphics, lineBoxes,
                            childPositionedContext, collapseTop, floats);
                }
                childFragments.addAll(childLayout.fragments());
                float bottomAbsorbed = childBox == lastBlockChild && parentCollapsesBottom
                        ? effectiveBottomMargin(childBox) : 0;
                currentY += childLayout.outerHeight() - bottomAbsorbed;
                previousBottomMargin = effectiveBottomMargin(childBox);
            } else {
                previousBottomMargin = null;
                inlineBuffer.add(child);
            }
        }
        childFragments.addAll(deferredFloats);
        currentY += flushInline(inlineBuffer, contentX, contentWidth, currentY,
                childContainingHeight, box.style().textAlign(), floats, graphics,
                childFragments, lineBoxes);
        float contentHeight = Math.max(0, currentY - contentY);
        if (establishesBfc(box)) {
            for (FloatRegion region : floats.regions()) {
                contentHeight = Math.max(contentHeight,
                        region.y() + region.height() - contentY);
            }
        }
        return contentHeight;
    }

    private static boolean establishesBfc(RenderBox box) {
        RenderStyle style = box.style();
        return style.overflow() != RenderStyle.Overflow.VISIBLE
                || switch (style.display()) {
            case INLINE_BLOCK, FLEX, INLINE_FLEX, GRID, INLINE_GRID,
                 TABLE, INLINE_TABLE, TABLE_CELL, TABLE_CAPTION -> true;
            default -> false;
        };
    }

    private static boolean collapsesWithChildren(RenderStyle style) {
        return style.position() == RenderStyle.Position.STATIC
                && style.floatMode() == RenderStyle.FloatMode.NONE
                && style.overflow() == RenderStyle.Overflow.VISIBLE
                && style.display() == RenderStyle.Display.BLOCK
                && style.borderWidth().top() == 0
                && style.borderWidth().bottom() == 0
                && style.padding().top() == 0
                && style.padding().bottom() == 0;
    }

    private static float effectiveTopMargin(RenderBox box) {
        RenderStyle style = box.style();
        if (!collapsesWithChildren(style)) {
            return style.margin().top();
        }
        RenderBox first = firstInFlowBlockChild(box);
        return first == null ? style.margin().top()
                : Math.max(style.margin().top(), effectiveTopMargin(first));
    }

    private static float effectiveBottomMargin(RenderBox box) {
        RenderStyle style = box.style();
        if (!collapsesWithChildren(style)) {
            return style.margin().bottom();
        }
        RenderBox last = lastInFlowBlockChild(box);
        return last == null ? style.margin().bottom()
                : Math.max(style.margin().bottom(), effectiveBottomMargin(last));
    }

    private static RenderBox firstInFlowBlockChild(RenderBox box) {
        for (RenderNode node : box.children()) {
            if (node instanceof RenderBox child) {
                if (child.style().position() == RenderStyle.Position.ABSOLUTE
                        || child.style().position() == RenderStyle.Position.FIXED) {
                    continue;
                }
                if (child.style().floatMode() != RenderStyle.FloatMode.NONE
                        || child.style().clear() != RenderStyle.Clear.NONE) {
                    return null;
                }
                return child;
            }
            return null;
        }
        return null;
    }

    private static RenderBox lastInFlowBlockChild(RenderBox box) {
        List<RenderNode> children = box.children();
        for (int index = children.size() - 1; index >= 0; index--) {
            if (children.get(index) instanceof RenderBox child) {
                if (child.style().position() == RenderStyle.Position.ABSOLUTE
                        || child.style().position() == RenderStyle.Position.FIXED) {
                    continue;
                }
                if (child.style().floatMode() != RenderStyle.FloatMode.NONE
                        || child.style().clear() != RenderStyle.Clear.NONE) {
                    return null;
                }
                return child;
            }
            return null;
        }
        return null;
    }

    static float textWidth(String text, FontMetrics metrics, float letterSpacingPx) {
        float base = metrics.stringWidth(text);
        return letterSpacingPx == 0 ? base : base + letterSpacingPx * Math.max(0, text.length() - 1);
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

    static float constrain(float value, Float minimum, Float maximum) {
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
        if (length.isAuto()
                || length.unit() == RenderLength.Unit.MAX_CONTENT
                || length.unit() == RenderLength.Unit.MIN_CONTENT) {
            return null;
        }
        return Math.max(0, resolve(length, percentageBase));
    }

    private Float resolveBoxConstraint(RenderStyle style,
                                       RenderLength length,
                                       RenderBox box,
                                       float percentageBase,
                                       float decoration,
                                       Graphics2D graphics) {
        if (length.isAuto()) {
            return null;
        }
        if (length.unit() == RenderLength.Unit.MAX_CONTENT
                || length.unit() == RenderLength.Unit.MIN_CONTENT) {
            IntrinsicWidths intrinsic = intrinsicWidths(box.children(), percentageBase, graphics, false);
            float contentWidth = length.unit() == RenderLength.Unit.MAX_CONTENT
                    ? intrinsic.preferred() : intrinsic.minimum();
            return contentBoxDimension(style, contentWidth, decoration);
        }
        Float resolved = resolveConstraint(length, percentageBase);
        return resolved == null ? null : contentBoxDimension(style, resolved, decoration);
    }

    private Float resolveDefiniteHeight(RenderLength length, Float containingHeight) {
        if (length.isAuto()
                || length.unit() == RenderLength.Unit.MAX_CONTENT
                || length.unit() == RenderLength.Unit.MIN_CONTENT) {
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

    static float contentBoxDimension(RenderStyle style,
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
        float intrinsicWidth = bitmap != null ? bitmap.getWidth()
                : image.svg() != null ? image.svg().width() : PLACEHOLDER_SIZE;
        float intrinsicHeight = bitmap != null ? bitmap.getHeight()
                : image.svg() != null ? image.svg().height() : PLACEHOLDER_SIZE;
        float naturalWidth = image.htmlWidth() != null ? image.htmlWidth() : intrinsicWidth;
        float naturalHeight = image.htmlHeight() != null ? image.htmlHeight() : intrinsicHeight;
        float naturalRatio = intrinsicWidth / Math.max(1, intrinsicHeight);
        float ratio = Float.isFinite(image.style().aspectRatio())
                ? image.style().aspectRatio() : naturalRatio;
        Float cssWidth = image.style().width().isAuto()
                || image.style().width().unit() == RenderLength.Unit.MAX_CONTENT
                || image.style().width().unit() == RenderLength.Unit.MIN_CONTENT
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
        } else if (image.htmlWidth() != null && image.htmlHeight() == null) {
            width = naturalWidth;
            height = width / Math.max(0.0001f, ratio);
        } else if (image.htmlHeight() != null && image.htmlWidth() == null) {
            height = naturalHeight;
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
        IntrinsicWidths intrinsic = intrinsicWidths(
                box.children(), availableContentWidth, graphics, true);
        return Math.min(Math.max(intrinsic.minimum(), availableContentWidth),
                intrinsic.preferred());
    }

    private IntrinsicWidths intrinsicWidths(List<RenderNode> nodes,
                                            float percentageBase,
                                            Graphics2D graphics,
                                            boolean contentBased) {
        return intrinsicWidths(nodes, percentageBase, graphics, contentBased, false);
    }

    private IntrinsicWidths intrinsicWidths(List<RenderNode> nodes,
                                            float percentageBase,
                                            Graphics2D graphics,
                                            boolean contentBased,
                                            boolean excludeFloats) {
        float preferred = 0;
        float minimum = 0;
        float inlinePreferred = 0;
        float inlineMinimum = 0;
        for (RenderNode node : nodes) {
            if (node instanceof RenderBox block) {
                if (excludeFloats && block.style().floatMode() != RenderStyle.FloatMode.NONE) {
                    continue;
                }
                preferred = Math.max(preferred, inlinePreferred);
                minimum = Math.max(minimum, inlineMinimum);
                inlinePreferred = 0;
                inlineMinimum = 0;
                IntrinsicWidths child = intrinsicBoxWidth(
                        block, percentageBase, graphics, contentBased, excludeFloats);
                preferred = Math.max(preferred, child.preferred());
                minimum = Math.max(minimum, child.minimum());
            } else {
                IntrinsicWidths child = intrinsicNodeWidth(
                        node, percentageBase, graphics, contentBased, excludeFloats);
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
                                              Graphics2D graphics,
                                              boolean contentBased) {
        return intrinsicBoxWidth(box, percentageBase, graphics, contentBased, false);
    }

    private IntrinsicWidths intrinsicBoxWidth(RenderBox box,
                                              float percentageBase,
                                              Graphics2D graphics,
                                              boolean contentBased,
                                              boolean excludeFloats) {
        RenderStyle style = box.style();
        float boxDecoration = style.borderWidth().horizontal() + style.padding().horizontal();
        float outerDecoration = style.margin().horizontal() + boxDecoration;
        Float minConstraint = resolveBoxConstraint(
                style, style.minWidth(), box, percentageBase, boxDecoration, graphics);
        Float maxConstraint = resolveBoxConstraint(
                style, style.maxWidth(), box, percentageBase, boxDecoration, graphics);
        if (!style.width().isAuto()) {
            if (contentBased && style.width().unit() == RenderLength.Unit.PERCENT) {
                IntrinsicWidths content = intrinsicWidths(
                        box.children(), percentageBase, graphics, true, excludeFloats);
                float preferred = constrain(content.preferred(), minConstraint, maxConstraint);
                float minimum = constrain(content.minimum(), minConstraint, maxConstraint);
                return new IntrinsicWidths(preferred + outerDecoration,
                        minimum + outerDecoration);
            }
            float width;
            RenderLength widthLength = style.width();
            if (widthLength.unit() == RenderLength.Unit.MAX_CONTENT
                    || widthLength.unit() == RenderLength.Unit.MIN_CONTENT) {
                IntrinsicWidths content = intrinsicWidths(
                        box.children(), percentageBase, graphics, contentBased, excludeFloats);
                width = widthLength.unit() == RenderLength.Unit.MAX_CONTENT
                        ? content.preferred() : content.minimum();
            } else {
                width = resolve(widthLength, percentageBase);
            }
            width = constrain(contentBoxDimension(style, width, boxDecoration),
                    minConstraint, maxConstraint);
            float minimum = width;
            if (widthLength.unit() == RenderLength.Unit.PERCENT) {
                IntrinsicWidths content = intrinsicWidths(
                        box.children(), percentageBase, graphics, contentBased, excludeFloats);
                minimum = constrain(content.minimum(), minConstraint, maxConstraint);
            }
            return new IntrinsicWidths(width + outerDecoration, minimum + outerDecoration);
        }
        IntrinsicWidths content = intrinsicWidths(
                box.children(), percentageBase, graphics, contentBased, excludeFloats);
        float preferred = constrain(content.preferred(), minConstraint, maxConstraint);
        float minimum = constrain(content.minimum(), minConstraint, maxConstraint);
        return new IntrinsicWidths(preferred + outerDecoration, minimum + outerDecoration);
    }

    private IntrinsicWidths intrinsicNodeWidth(RenderNode node,
                                               float percentageBase,
                                               Graphics2D graphics,
                                               boolean contentBased) {
        return intrinsicNodeWidth(node, percentageBase, graphics, contentBased, false);
    }

    private IntrinsicWidths intrinsicNodeWidth(RenderNode node,
                                               float percentageBase,
                                               Graphics2D graphics,
                                               boolean contentBased,
                                               boolean excludeFloats) {
        if (node instanceof RenderTextRun run) {
            RenderStyle.WhiteSpace mode = run.style().whiteSpace();
            FontMetrics metrics = graphics.getFontMetrics(fontFor(run.style()));
            boolean preserve = switch (mode) {
                case PRE, PRE_WRAP, BREAK_SPACES -> true;
                default -> false;
            };
            float spacing = run.style().letterSpacingPx();
            if (preserve) {
                float preferred = textWidth(run.text(), metrics, spacing);
                if (mode == RenderStyle.WhiteSpace.PRE) {
                    return new IntrinsicWidths(preferred, preferred);
                }
                float minimum = 0;
                for (String word : run.text().split("\\s+")) {
                    minimum = Math.max(minimum, textWidth(word, metrics, spacing));
                }
                return new IntrinsicWidths(preferred, minimum);
            }
            String collapsed = run.text().trim().replaceAll("\\s+", " ");
            float preferred = textWidth(collapsed, metrics, spacing);
            float minimum = 0;
            for (String word : collapsed.split(" ")) {
                minimum = Math.max(minimum, textWidth(word, metrics, spacing));
            }
            if (mode == RenderStyle.WhiteSpace.NOWRAP) {
                return new IntrinsicWidths(preferred, preferred);
            }
            return new IntrinsicWidths(preferred, minimum);
        }
        if (node instanceof RenderInlineBox inline) {
            IntrinsicWidths content = intrinsicWidths(
                    inline.children(), percentageBase, graphics, contentBased, excludeFloats);
            RenderStyle style = inline.style();
            float decoration = style.margin().horizontal() + style.borderWidth().horizontal()
                    + style.padding().horizontal();
            return new IntrinsicWidths(content.preferred() + decoration,
                    content.minimum() + decoration);
        }
        if (node instanceof RenderInlineBlock inlineBlock) {
            return intrinsicBoxWidth(
                    inlineBlock.box(), percentageBase, graphics, contentBased, excludeFloats);
        }
        if (node instanceof RenderImage image) {
            ImageLayout layout = imageLayout(image, percentageBase, null);
            float minimum = image.style().width().unit() == RenderLength.Unit.PERCENT
                    ? 0 : layout.width();
            return new IntrinsicWidths(layout.width(), minimum);
        }
        return new IntrinsicWidths(0, 0);
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

    private AtomicLayout layoutAtomic(RenderInlineBlock inlineBlock,
                                      float width,
                                      Float containingHeight,
                                      Graphics2D graphics) {
        List<LineBox> atomicLines = new ArrayList<>();
        PositionedContext atomicContainingBlock = new PositionedContext();
        BlockLayout block = layoutBlock(
                inlineBlock.box(), 0, 0, width, containingHeight, true,
                graphics, atomicLines, atomicContainingBlock);
        atomicContainingBlock.setGeometry(0, 0, width, block.outerHeight());
        List<PaintFragment> atomicFragments = new ArrayList<>(block.fragments());
        atomicFragments.addAll(
                positionedLayout.layoutAbsoluteRequests(atomicContainingBlock, graphics, atomicLines));
        block = new BlockLayout(block.outerHeight(), List.copyOf(atomicFragments));
        BoxFragment root = (BoxFragment) block.fragments().getFirst();
        float atomicWidth = inlineBlock.box().style().margin().left()
                + root.width() + inlineBlock.box().style().margin().right();
        float baselineOffset = inlineBlock.box().style().overflow()
                != RenderStyle.Overflow.VISIBLE || atomicLines.isEmpty()
                ? block.outerHeight()
                : atomicLines.getLast().baseline();
        return new AtomicLayout(block, atomicLines, atomicWidth,
                block.outerHeight(), inlineBlock.box().style().verticalAlign(),
                inlineBlock.box().style().fontSizePx(), baselineOffset);
    }

    private float flushInline(List<RenderNode> inlineNodes,
                              float contentX,
                              float contentWidth,
                              float y,
                              Float containingHeight,
                              RenderStyle.TextAlign textAlign,
                              FloatExclusionSpace floats,
                              Graphics2D graphics,
                              List<PaintFragment> target,
                              List<LineBox> lineBoxes) {
        if (inlineNodes.isEmpty()) {
            return 0;
        }
        InlineLayout layouter = new InlineLayout(
                inlineServices,
                new InlineLayout.LineConstraints(floats, contentX, contentWidth),
                y, contentWidth, containingHeight, textAlign, graphics, target, lineBoxes);
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

        default java.awt.geom.AffineTransform transform() {
            return null;
        }
    }

    public record ClipRect(float x, float y, float width, float height) {
    }

    public interface InlineFragment extends PaintFragment {
        float x();

        float width();
    }

    public record BoxFragment(RenderBox box, float x, float y, float width, float height,
                              ClipRect clip, java.awt.geom.AffineTransform transform)
            implements PaintFragment {
        public BoxFragment(RenderBox box, float x, float y, float width, float height) {
            this(box, x, y, width, height, null, null);
        }

        @Override
        public float top() {
            return y;
        }

        @Override
        public float bottom() {
            return y + height;
        }
    }

    public record InlineBoxFragment(RenderInlineBox box,
                                    float x,
                                    float y,
                                    float width,
                                    float height,
                                    boolean firstFragment,
                                    boolean lastFragment,
                                    ClipRect clip,
                                    java.awt.geom.AffineTransform transform) implements InlineFragment {
        public InlineBoxFragment(RenderInlineBox box, float x, float y, float width,
                                 float height, boolean firstFragment, boolean lastFragment) {
            this(box, x, y, width, height, firstFragment, lastFragment, null, null);
        }

        @Override
        public float top() {
            return y;
        }

        @Override
        public float bottom() {
            return y + height;
        }
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
                               boolean lineThrough,
                               CssColor decorationColor,
                               float opacity,
                               float letterSpacingPx,
                               boolean ellipsis,
                               ClipRect clip,
                               java.awt.geom.AffineTransform transform,
                               RenderStyle.TextShadow shadow,
                               boolean visible) implements InlineFragment {
        public TextFragment(String text, float x, float width, float baseline, float top,
                            float height, Font font, CssColor color, boolean underline,
                            boolean lineThrough, CssColor decorationColor, float opacity) {
            this(text, x, width, baseline, top, height, font, color, underline, lineThrough,
                    decorationColor, opacity, 0, false, null, null, null, true);
        }

        public TextFragment(String text, float x, float width, float baseline, float top,
                            float height, Font font, CssColor color, boolean underline,
                            boolean lineThrough, CssColor decorationColor, float opacity,
                            float letterSpacingPx, boolean ellipsis) {
            this(text, x, width, baseline, top, height, font, color, underline, lineThrough,
                    decorationColor, opacity, letterSpacingPx, ellipsis, null, null, null, true);
        }

        @Override
        public float bottom() {
            return top + height;
        }
    }

    public record ImageFragment(RenderImage image,
                                BufferedImage bitmap,
                                float x,
                                float y,
                                float width,
                                float height,
                                ClipRect clip,
                                java.awt.geom.AffineTransform transform) implements InlineFragment {
        public ImageFragment(RenderImage image, BufferedImage bitmap, float x, float y,
                             float width, float height) {
            this(image, bitmap, x, y, width, height, null, null);
        }

        @Override
        public float top() {
            return y;
        }

        @Override
        public float bottom() {
            return y + height;
        }
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

    record BlockLayout(float outerHeight, List<PaintFragment> fragments) {
    }

    record IntrinsicWidths(float preferred, float minimum) {
    }

}

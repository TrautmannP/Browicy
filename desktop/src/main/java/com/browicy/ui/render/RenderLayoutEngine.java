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
import com.browicy.engine.render.Transform;
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
        root = positionRootInInitialContainingBlock(
                root, tree.root(), initialContainingBlock, graphics, lineBoxes, rootFirstLine);
        List<PaintFragment> positioned =
                layoutAbsoluteRequests(initialContainingBlock, graphics, lineBoxes);
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

    private BlockLayout positionRootInInitialContainingBlock(BlockLayout rootLayout,
                                                             RenderBox root,
                                                             PositionedContext icb,
                                                             Graphics2D graphics,
                                                             List<LineBox> lineBoxes,
                                                             int rootFirstLine) {
        RenderStyle style = root.style();
        if (style.position() != RenderStyle.Position.ABSOLUTE
                && style.position() != RenderStyle.Position.FIXED) {
            return rootLayout;
        }
        float left = style.left().isAuto() ? 0 : resolve(style.left(), icb.width);
        float right = style.right().isAuto() ? 0 : resolve(style.right(), icb.width);
        float top = style.top().isAuto() ? 0 : resolve(style.top(), icb.height);
        float bottom = style.bottom().isAuto() ? 0 : resolve(style.bottom(), icb.height);
        boolean stretchAutoWidth = style.width().isAuto()
                && !style.left().isAuto() && !style.right().isAuto();
        BlockLayout current = rootLayout;
        if (style.width().isAuto()) {
            while (lineBoxes.size() > rootFirstLine) {
                lineBoxes.remove(lineBoxes.size() - 1);
            }
            float availableWidth = stretchAutoWidth
                    ? Math.max(0, icb.width - left - right)
                    : icb.width;
            current = layoutBlock(root, icb.x, icb.y, availableWidth, icb.height,
                    !stretchAutoWidth, graphics, lineBoxes, new PositionedContext());
        }
        BoxFragment box = (BoxFragment) current.fragments().getFirst();
        float desiredX;
        if (!style.left().isAuto()) {
            desiredX = icb.x + left + style.margin().left();
        } else if (!style.right().isAuto()) {
            desiredX = icb.x + icb.width - right - style.margin().right() - box.width();
        } else {
            desiredX = icb.x + style.margin().left();
        }
        float desiredY;
        if (!style.top().isAuto()) {
            desiredY = icb.y + top + style.margin().top();
        } else if (!style.bottom().isAuto()) {
            desiredY = icb.y + icb.height - bottom - style.margin().bottom() - box.height();
        } else {
            desiredY = icb.y + style.margin().top();
        }
        float dx = desiredX - box.x();
        float dy = desiredY - box.y();
        if (dx == 0 && dy == 0) {
            return current;
        }
        List<PaintFragment> fragments = current.fragments().stream()
                .map(fragment -> translate(fragment, dx, dy))
                .toList();
        translateLines(lineBoxes, rootFirstLine, dx, dy);
        return new BlockLayout(current.outerHeight(), List.copyOf(fragments));
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
        return layoutBlock(box, containingX, y, availableWidth, containingHeight,
                shrinkToFitAuto, graphics, lineBoxes, positionedContext, false, null);
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
        return layoutBlock(box, containingX, y, availableWidth, containingHeight,
                shrinkToFitAuto, graphics, lineBoxes, positionedContext, collapseTopMargin, null);
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
                                    boolean collapseTopMargin,
                                    List<FloatRegion> bfcFloats) {
        int firstLine = lineBoxes.size();
        RenderStyle style = box.style();
        if (style.display() == RenderStyle.Display.TABLE) {
            return layoutTable(box, containingX, y, availableWidth, containingHeight,
                    graphics, lineBoxes, positionedContext);
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
                    style, resolve(style.width(), availableWidth), horizontalDecoration);
        }
        contentBoxWidth = constrain(contentBoxWidth,
                resolveBoxConstraint(
                        style, style.minWidth(), box, availableWidth, horizontalDecoration,
                        graphics),
                resolveBoxConstraint(
                        style, style.maxWidth(), box, availableWidth, horizontalDecoration,
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
            FlexLayout flex = layoutFlex(box, contentX, contentY, contentWidth,
                    childContainingHeight, graphics, lineBoxes, childPositionedContext);
            childFragments.addAll(flex.fragments());
            elevatedFragments.addAll(flex.elevated());
            naturalContentHeight = flex.height();
        } else if (style.display() == RenderStyle.Display.GRID
                || style.display() == RenderStyle.Display.INLINE_GRID) {
            GridLayout grid = layoutGrid(box, contentX, contentY, contentWidth,
                    childContainingHeight, graphics, lineBoxes, childPositionedContext);
            childFragments.addAll(grid.fragments());
            elevatedFragments.addAll(grid.elevated());
            naturalContentHeight = grid.height();
        } else {
            List<FloatRegion> floats = bfcFloats == null ? new ArrayList<>() : bfcFloats;
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
                    layoutAbsoluteRequests(childPositionedContext, graphics, lineBoxes);
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
                                      List<FloatRegion> floats) {
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
                float inlineMinimum = floats.isEmpty() || inlineBuffer.isEmpty()
                        ? 0 : intrinsicWidths(inlineBuffer, contentWidth, graphics, false).minimum();
                currentY = dropBelowFloatsIfNarrow(
                        floats, contentX, contentWidth, currentY, inlineMinimum);
                FloatArea inlineArea = floatArea(floats, contentX, contentWidth, currentY);
                float inlineHeight = flushInline(inlineBuffer, inlineArea.x(), currentY,
                        inlineArea.width(), childContainingHeight, box.style().textAlign(),
                        graphics, childFragments, lineBoxes);
                currentY += inlineHeight;
                if (inlineHeight > 0) previousBottomMargin = null;
                if (childBox.style().position() == RenderStyle.Position.ABSOLUTE
                        || childBox.style().position() == RenderStyle.Position.FIXED) {
                    childPositionedContext.requests.add(
                            new AbsoluteRequest(childBox, contentX, currentY));
                    continue;
                }
                currentY = clearedY(floats, currentY, childBox.style().clear());
                FloatArea blockArea = floatArea(floats, contentX, contentWidth, currentY);
                float blockMinimum = floats.isEmpty() ? 0
                        : intrinsicBoxWidth(childBox, contentWidth, graphics, false, true).minimum();
                if (blockArea.width() < Math.max(1, blockMinimum)) {
                    // Float-Regel 7 (§9.5.1): Ein Float, dessen äußere Kante an
                    // der Kante seines Containing Blocks anliegt ("so weit links
                    // bzw. rechts wie möglich"), darf über die gegenüberliegende
                    // Kante hinausragen (Ausnahme zu Regel 3). Die Ausnahme
                    // greift nur, wenn im Containing Block kein Float der
                    // Gegenseite steht (Regel 4): Andernfalls überlappt der
                    // herausragende Float ihn und wird unter die Floats
                    // verschoben.
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
                        currentY = clearedY(floats, currentY, RenderStyle.Clear.BOTH);
                        blockArea = floatArea(floats, contentX, contentWidth, currentY);
                    }
                }
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
                    // Mal-Reihenfolge nach CSS2.1 Anhang E: Float-Fragmente
                    // werden über den Hintergründen der In-Flow-Blöcke des BFC
                    // gemalt (nicht in Quellreihenfolge), aber unter dem
                    // Inline-Inhalt. Deshalb sammeln wir sie und hängen sie
                    // nach der Kinderschleife an.
                    floatLayout.fragments().stream().map(fragment -> translate(fragment, dx, 0))
                            .forEach(deferredFloats::add);
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
                        : previousBottomMargin + effectiveTopMargin(childBox)
                        - Math.max(previousBottomMargin, effectiveTopMargin(childBox));
                currentY -= collapsedOverlap;
                boolean collapseTop = parentCollapsesTop && childBox == firstBlockChild;
                // §9.5.1: Normale In-Flow-Blockboxen fließen vertikal und
                // horizontal "als gäbe es den Float nicht" – sie erhalten die
                // volle Containing-Block-Breite; nur ihre Zeilenboxen weichen
                // den Floats des BFC aus. Dafür wird die aktive Float-Liste
                // des BFC an Nicht-BFC-Kinder weitergereicht (gemeinsame,
                // mutierbare Liste). Boxen, die selbst einen neuen BFC erzeugen
                // (overflow ≠ visible, table, inline-block, Flex/Grid), dürfen
                // Floats dagegen nicht überlappen (Regel 5) und bekommen eine
                // eigene Float-Liste.
                boolean childEstablishesBfc = establishesBfc(childBox);
                BlockLayout childLayout = layoutBlock(
                        childBox, childEstablishesBfc ? blockArea.x() : contentX, currentY,
                        childEstablishesBfc ? blockArea.width() : contentWidth,
                        childContainingHeight, false, graphics, lineBoxes,
                        childPositionedContext, collapseTop,
                        childEstablishesBfc ? null : floats);
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
        FloatArea finalArea = floatArea(floats, contentX, contentWidth, currentY);
        float finalMinimum = floats.isEmpty() || inlineBuffer.isEmpty()
                ? 0 : intrinsicWidths(inlineBuffer, contentWidth, graphics, false).minimum();
        if (finalArea.width() < Math.max(1, finalMinimum)) {
            currentY = clearedY(floats, currentY, RenderStyle.Clear.BOTH);
            finalArea = floatArea(floats, contentX, contentWidth, currentY);
        }
        // Nachlaufende Inline-Inhalte (Anhang E, Schritt 5) malen über den
        // Floats; deshalb erst die Float-Fragmente anhängen, dann flushInline.
        childFragments.addAll(deferredFloats);
        currentY += flushInline(inlineBuffer, finalArea.x(), currentY, finalArea.width(),
                childContainingHeight, box.style().textAlign(), graphics, childFragments,
                lineBoxes);
        float contentHeight = Math.max(0, currentY - contentY);
        if (box.style().overflow() != RenderStyle.Overflow.VISIBLE) {
            for (FloatRegion region : floats) {
                contentHeight = Math.max(contentHeight,
                        region.y() + region.height() - contentY);
            }
        }
        return contentHeight;
    }

    /** CSS2.1 §9.4.1: Erzeugt die Box einen neuen Block-Formatierungskontext
     *  (overflow ≠ visible sowie tabellarische, inline-block- und
     *  Flex-/Grid-Container)? Positionierung (absolut/fixed) und float werden
     *  vom Aufrufer bereits vorher abgezweigt. BFC-Wurzeln dürfen die
     *  Margin-Boxen der Floats des äußeren BFC nicht überlappen (Regel 5,
     *  §9.5.1) und isolieren ihre eigenen Floats. */
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

    private record GridLayout(float height, List<PaintFragment> fragments,
                              List<PaintFragment> elevated) {
        private GridLayout(float height, List<PaintFragment> fragments) {
            this(height, fragments, List.of());
        }
    }

    private record GridPlacement(int row, int column, int rowSpan, int columnSpan) {
    }

    private GridLayout layoutGrid(RenderBox container, float contentX, float contentY,
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
            BlockLayout itemLayout = layoutBlock(item, x,
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
                itemToLayout = forceOuterHeight(item, cellHeight);
            }
            BlockLayout itemLayout = layoutBlock(itemToLayout, x,
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
            IntrinsicWidths intrinsic = intrinsicBoxWidth(items.get(index), contentWidth, graphics, false);
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
                intrinsicBoxWidth(item, contentWidth, graphics, false), contentWidth);
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
        return resolve(basis, percentageBase)
                + style.margin().horizontal() + style.padding().horizontal()
                + style.borderWidth().horizontal();
    }

    private float flexMinimumOuterWidth(RenderBox box,
                                        float percentageBase,
                                        Graphics2D graphics) {
        RenderStyle style = box.style();
        if (box.children().isEmpty() && !style.width().isAuto()) {
            return intrinsicBoxWidth(box, percentageBase, graphics, false).minimum();
        }
        float boxDecoration = style.borderWidth().horizontal() + style.padding().horizontal();
        float outerDecoration = style.margin().horizontal() + boxDecoration;
        IntrinsicWidths content = intrinsicWidths(box.children(), percentageBase, graphics, false);
        Float minConstraint = resolveBoxConstraint(
                style, style.minWidth(), box, percentageBase, boxDecoration, graphics);
        Float maxConstraint = resolveBoxConstraint(
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

    private static float textWidth(String text, FontMetrics metrics, float letterSpacingPx) {
        float base = metrics.stringWidth(text);
        return letterSpacingPx == 0 ? base : base + letterSpacingPx * Math.max(0, text.length() - 1);
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

    private static float dropBelowFloatsIfNarrow(List<FloatRegion> floats,
                                                 float contentX,
                                                 float contentWidth,
                                                 float y,
                                                 float minimumWidth) {
        if (floats.isEmpty() || minimumWidth <= 0) {
            return y;
        }
        if (floatArea(floats, contentX, contentWidth, y).width() >= Math.max(1, minimumWidth)) {
            return y;
        }
        return clearedY(floats, y, RenderStyle.Clear.BOTH);
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
        if (rows.isEmpty()) {
            RenderBox anonymousTable = new RenderBox(table.source(),
                    style.withDisplay(RenderStyle.Display.BLOCK), table.children());
            return layoutBlock(anonymousTable, containingX, y, availableWidth,
                    containingHeight, false, graphics, lineBoxes, positionedContext);
        }
        int columnCount = rows.stream().mapToInt(row -> row.cells().size()).max().orElse(0);
        float[] preferred = new float[columnCount];
        float[] minimum = new float[columnCount];
        for (TableRow row : rows) {
            for (int column = 0; column < row.cells().size(); column++) {
                IntrinsicWidths intrinsic = intrinsicBoxWidth(
                        row.cells().get(column), availableContentWidth, graphics, false);
                preferred[column] = Math.max(preferred[column], intrinsic.preferred());
                minimum[column] = Math.max(minimum[column], intrinsic.minimum());
            }
        }
        float specifiedWidth = style.width().isAuto()
                || style.width().unit() == RenderLength.Unit.MAX_CONTENT
                || style.width().unit() == RenderLength.Unit.MIN_CONTENT
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

    private static int fragmentZIndex(PaintFragment fragment) {
        if (fragment instanceof BoxFragment box) {
            return box.box().style().zIndex();
        }
        if (fragment instanceof InlineBoxFragment box) {
            return box.box().style().zIndex();
        }
        return 0;
    }

    private static List<PaintFragment> mergeElevated(List<PaintFragment> elevated,
                                                     List<PaintFragment> positioned) {
        if (elevated.isEmpty()) {
            return positioned;
        }
        if (positioned.isEmpty()) {
            return elevated;
        }
        List<PaintFragment> merged = new ArrayList<>(elevated.size() + positioned.size());
        int i = 0;
        int j = 0;
        while (i < elevated.size() && j < positioned.size()) {
            if (fragmentZIndex(elevated.get(i)) <= fragmentZIndex(positioned.get(j))) {
                merged.add(elevated.get(i++));
            } else {
                merged.add(positioned.get(j++));
            }
        }
        while (i < elevated.size()) {
            merged.add(elevated.get(i++));
        }
        while (j < positioned.size()) {
            merged.add(positioned.get(j++));
        }
        return merged;
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
            if (isHiddenScrollButton(request.box(), context)) {
                continue;
            }
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

    private static float fragmentLeft(PaintFragment fragment) {
        if (fragment instanceof InlineFragment inline) {
            return inline.x();
        }
        if (fragment instanceof BoxFragment box) {
            return box.x();
        }
        return Float.POSITIVE_INFINITY;
    }

    private static float fragmentRight(PaintFragment fragment) {
        if (fragment instanceof InlineFragment inline) {
            return inline.x() + inline.width();
        }
        if (fragment instanceof BoxFragment box) {
            return box.x() + box.width();
        }
        return Float.NEGATIVE_INFINITY;
    }

    private boolean isHiddenScrollButton(RenderBox box, PositionedContext context) {
        if (context.contentMaxRight == Float.NEGATIVE_INFINITY) {
            return false;
        }
        var source = box.source();
        if (source == null) {
            return false;
        }
        String cssClass = source.getAttribute("class");
        if (cssClass == null) {
            return false;
        }
        boolean scrollLeft = cssClass.contains("scroll-left");
        boolean scrollRight = cssClass.contains("scroll-right");
        if (!scrollLeft && !scrollRight) {
            return false;
        }
        float contentLeft = context.x;
        float contentRight = context.x + context.width;
        if (scrollLeft) {
            return context.contentMinLeft >= contentLeft - 0.5f;
        }
        return context.contentMaxRight <= contentRight + 0.5f;
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
                    box.width(), box.height(), translate(box.clip(), dx, dy),
                    box.transform());
        }
        if (fragment instanceof InlineBoxFragment box) {
            return new InlineBoxFragment(box.box(), box.x() + dx, box.y() + dy,
                    box.width(), box.height(), box.firstFragment(), box.lastFragment(),
                    translate(box.clip(), dx, dy), box.transform());
        }
        if (fragment instanceof ImageFragment image) {
            return new ImageFragment(image.image(), image.bitmap(), image.x() + dx,
                    image.y() + dy, image.width(), image.height(),
                    translate(image.clip(), dx, dy), image.transform());
        }
        TextFragment text = (TextFragment) fragment;
        return new TextFragment(text.text(), text.x() + dx, text.width(),
                text.baseline() + dy, text.top() + dy, text.height(), text.font(),
                text.color(), text.underline(), text.lineThrough(),
                text.decorationColor(), text.opacity(), text.letterSpacingPx(),
                text.ellipsis(),
                translate(text.clip(), dx, dy), text.transform(), text.shadow(),
                text.visible());
    }

    private static PaintFragment withTransform(PaintFragment fragment,
                                               java.awt.geom.AffineTransform transform) {
        if (fragment instanceof BoxFragment box) {
            return new BoxFragment(box.box(), box.x(), box.y(), box.width(), box.height(),
                    box.clip(), compose(box.transform(), transform));
        }
        if (fragment instanceof InlineBoxFragment box) {
            return new InlineBoxFragment(box.box(), box.x(), box.y(), box.width(),
                    box.height(), box.firstFragment(), box.lastFragment(), box.clip(),
                    compose(box.transform(), transform));
        }
        if (fragment instanceof ImageFragment image) {
            return new ImageFragment(image.image(), image.bitmap(), image.x(), image.y(),
                    image.width(), image.height(), image.clip(),
                    compose(image.transform(), transform));
        }
        TextFragment text = (TextFragment) fragment;
        return new TextFragment(text.text(), text.x(), text.width(), text.baseline(),
                text.top(), text.height(), text.font(), text.color(), text.underline(),
                text.lineThrough(), text.decorationColor(), text.opacity(),
                text.letterSpacingPx(), text.ellipsis(), text.clip(),
                compose(text.transform(), transform), text.shadow(), text.visible());
    }

    private static java.awt.geom.AffineTransform compose(
            java.awt.geom.AffineTransform inner, java.awt.geom.AffineTransform outer) {
        if (inner == null) {
            return outer;
        }
        if (outer == null) {
            return inner;
        }
        java.awt.geom.AffineTransform composed = new java.awt.geom.AffineTransform(outer);
        composed.concatenate(inner);
        return composed;
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

    private static PaintFragment withClip(PaintFragment fragment, ClipRect clip) {
        ClipRect effective = intersect(fragment.clip(), clip);
        if (fragment instanceof BoxFragment box) {
            return new BoxFragment(box.box(), box.x(), box.y(), box.width(), box.height(),
                    effective, box.transform());
        }
        if (fragment instanceof InlineBoxFragment box) {
            return new InlineBoxFragment(box.box(), box.x(), box.y(), box.width(), box.height(),
                    box.firstFragment(), box.lastFragment(), effective, box.transform());
        }
        if (fragment instanceof ImageFragment image) {
            return new ImageFragment(image.image(), image.bitmap(), image.x(), image.y(),
                    image.width(), image.height(), effective, image.transform());
        }
        TextFragment text = (TextFragment) fragment;
        return new TextFragment(text.text(), text.x(), text.width(), text.baseline(), text.top(),
                text.height(), text.font(), text.color(), text.underline(), text.lineThrough(),
                text.decorationColor(), text.opacity(), text.letterSpacingPx(),
                text.ellipsis(), effective, text.transform(), text.shadow(), text.visible());
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
                                    ClipRect clip,
                                    java.awt.geom.AffineTransform transform) implements InlineFragment {
        public InlineBoxFragment(RenderInlineBox box, float x, float y, float width,
                                 float height, boolean firstFragment, boolean lastFragment) {
            this(box, x, y, width, height, firstFragment, lastFragment, null, null);
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
        @Override public float bottom() { return top + height; }
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

    private record FlexLayout(float height, List<PaintFragment> fragments,
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

    private record AbsoluteRequest(RenderBox box, float staticX, float staticY) {
    }

    private static final class PositionedContext {
        private final List<AbsoluteRequest> requests = new ArrayList<>();
        private float x;
        private float y;
        private float width;
        private float height;
        private float contentMinLeft = Float.POSITIVE_INFINITY;
        private float contentMaxRight = Float.NEGATIVE_INFINITY;

        void setGeometry(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        void setContentExtent(float contentMinLeft, float contentMaxRight) {
            this.contentMinLeft = contentMinLeft;
            this.contentMaxRight = contentMaxRight;
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

    private record SpaceToken(String text, RenderStyle style) implements InlineToken {
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
        private String pendingSpaceText = " ";
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
                    pendingSpaceText = space.text();
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
                } else if (node instanceof RenderBox box) {
                    tokens.add(new AtomicBlockToken(new RenderInlineBlock(box)));
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
            RenderStyle.WhiteSpace mode = style.whiteSpace();
            boolean preserve = switch (mode) {
                case PRE, PRE_WRAP, BREAK_SPACES -> true;
                default -> false;
            };
            boolean preserveNewlines = preserve || mode == RenderStyle.WhiteSpace.PRE_LINE;
            int offset = 0;
            while (offset < text.length()) {
                int codePoint = text.codePointAt(offset);
                if (Character.isWhitespace(codePoint)) {
                    int start = offset;
                    boolean newline = codePoint == '\n' || codePoint == '\r';
                    do {
                        offset += Character.charCount(codePoint);
                        if (offset >= text.length()) {
                            break;
                        }
                        codePoint = text.codePointAt(offset);
                        newline = newline || codePoint == '\n' || codePoint == '\r';
                    } while (Character.isWhitespace(codePoint));
                    if (preserveNewlines && newline) {
                        tokens.add(new BreakToken(style));
                    } else if (preserve) {
                        tokens.add(new SpaceToken(text.substring(start, offset), style));
                    } else {
                        tokens.add(new SpaceToken(" ", style));
                    }
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
            float wordWidth = textWidth(word, metrics, style.letterSpacingPx());
            boolean wrapAllowed = switch (style.whiteSpace()) {
                case NOWRAP, PRE -> false;
                default -> true;
            };

            if (wrapAllowed && line.hasPlacedContent()
                    && line.width() + spaceWidth + wordWidth + trailingDecorationWidth > width) {
                pendingSpace = false;
                pendingSpaceStyle = null;
                flushLine(false, null);
            }

            materializePendingSpace();
            if (!wrapAllowed) {
                line.addText(word, font, metrics, style);
                return;
            }
            int offset = 0;
            while (offset < word.length()) {
                float finalWidth = textWidth(word.substring(offset), metrics,
                        style.letterSpacingPx());
                if (finalWidth + trailingDecorationWidth <= width - line.width()) {
                    line.addText(word.substring(offset), font, metrics, style);
                    return;
                }

                float remaining = Math.max(1, width - line.width());
                int end = longestFittingEnd(word, offset, metrics, remaining,
                        style.letterSpacingPx());
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
            FontMetrics metrics = graphics.getFontMetrics(fontFor(pendingSpaceStyle));
            return textWidth(pendingSpaceText, metrics, pendingSpaceStyle.letterSpacingPx());
        }

        private void materializePendingSpace() {
            if (pendingSpace && line.hasPlacedContent() && pendingSpaceStyle != null) {
                Font font = fontFor(pendingSpaceStyle);
                FontMetrics metrics = graphics.getFontMetrics(font);
                line.addText(pendingSpaceText, font, metrics, pendingSpaceStyle);
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
                                             float availableWidth,
                                             float letterSpacingPx) {
            int codePointCount = text.codePointCount(start, text.length());
            int low = 1;
            int high = codePointCount;
            int bestCodePoints = 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                int end = text.offsetByCodePoints(start, middle);
                if (textWidth(text.substring(start, end), metrics, letterSpacingPx)
                        <= availableWidth) {
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
                            boolean lineThrough,
                            CssColor decorationColor,
                            float opacity,
                            float usedLineHeight,
                            float letterSpacingPx,
                            RenderStyle.TextOverflow textOverflow,
                            RenderStyle.TextShadow shadow,
                            boolean visible) implements LineItem {
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
            float itemWidth = textWidth(text, metrics, style.letterSpacingPx());
            addItem(new TextItem(text, width, itemWidth, font, metrics, style.color(),
                    style.underline(), style.lineThrough(), style.textDecorationColor(),
                    style.opacity(), style.usedLineHeightPx(), style.letterSpacingPx(),
                    style.textOverflow(), style.textShadow(), style.visible()));
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
                            text.lineThrough,
                            text.decorationColor,
                            text.opacity,
                            text.letterSpacingPx,
                            text.textOverflow == RenderStyle.TextOverflow.ELLIPSIS,
                            null,
                            null,
                            text.shadow,
                            text.visible));
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
                        translate(box.clip(), dx, dy), box.transform());
            }
            if (fragment instanceof InlineBoxFragment box) {
                return new InlineBoxFragment(box.box(), box.x() + dx, box.y() + dy,
                        box.width(), box.height(), box.firstFragment(), box.lastFragment(),
                        translate(box.clip(), dx, dy), box.transform());
            }
            if (fragment instanceof ImageFragment image) {
                return new ImageFragment(image.image(), image.bitmap(), image.x() + dx,
                        image.y() + dy, image.width(), image.height(),
                        translate(image.clip(), dx, dy), image.transform());
            }
            TextFragment text = (TextFragment) fragment;
            return new TextFragment(text.text(), text.x() + dx, text.width(),
                    text.baseline() + dy, text.top() + dy, text.height(), text.font(),
                    text.color(), text.underline(), text.lineThrough(),
                    text.decorationColor(), text.opacity(), text.letterSpacingPx(),
                    text.ellipsis(),
                    translate(text.clip(), dx, dy), text.transform(), text.shadow(),
                    text.visible());
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

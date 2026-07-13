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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public final class RenderLayoutEngine {

    private static final float PLACEHOLDER_SIZE = 16f;
    private static final int MAX_IMAGE_DIMENSION = 8192;
    private static final long MAX_IMAGE_PIXELS = 32_000_000L;
    private final Map<com.browicy.engine.dom.Element, Optional<BufferedImage>> decodedImages =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());

    public LayoutResult layout(RenderTree tree, int viewportWidth, Insets insets, Graphics2D graphics) {
        float availableWidth = Math.max(1, viewportWidth - insets.left - insets.right);
        List<LineBox> lineBoxes = new ArrayList<>();
        BlockLayout root = layoutBlock(
                tree.root(), insets.left, insets.top, availableWidth, null, false,
                graphics, lineBoxes);
        float height = insets.top + root.outerHeight() + insets.bottom;
        return new LayoutResult(
                viewportWidth,
                Math.max(insets.top + insets.bottom, height),
                root.fragments(),
                lineBoxes);
    }

    private BlockLayout layoutBlock(RenderBox box,
                                    float containingX,
                                    float y,
                                    float availableWidth,
                                    Float containingHeight,
                                    boolean shrinkToFitAuto,
                                    Graphics2D graphics,
                                    List<LineBox> lineBoxes) {
        RenderStyle style = box.style();
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
            contentBoxWidth = Math.max(0, style.width().resolve(availableWidth));
        }
        contentBoxWidth = constrain(contentBoxWidth,
                resolveConstraint(style.minWidth(), availableWidth),
                resolveConstraint(style.maxWidth(), availableWidth));
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

        Float specifiedContentHeight = resolveDefiniteHeight(style.height(), containingHeight);
        Float childContainingHeight = specifiedContentHeight == null
                ? null
                : constrain(specifiedContentHeight,
                        resolveHeightConstraint(style.minHeight(), containingHeight),
                        resolveHeightConstraint(style.maxHeight(), containingHeight));

        List<PaintFragment> childFragments = new ArrayList<>();
        List<RenderNode> inlineBuffer = new ArrayList<>();
        float currentY = contentY;

        for (RenderNode child : box.children()) {
            if (child instanceof RenderBox childBox) {
                currentY += flushInline(inlineBuffer, contentX, currentY, contentWidth,
                        childContainingHeight, style.textAlign(), graphics, childFragments,
                        lineBoxes);
                BlockLayout childLayout = layoutBlock(
                        childBox, contentX, currentY, contentWidth, childContainingHeight,
                        false, graphics, lineBoxes);
                childFragments.addAll(childLayout.fragments());
                currentY += childLayout.outerHeight();
            } else {
                inlineBuffer.add(child);
            }
        }
        currentY += flushInline(inlineBuffer, contentX, currentY, contentWidth,
                childContainingHeight, style.textAlign(), graphics, childFragments, lineBoxes);

        float naturalContentHeight = Math.max(0, currentY - contentY);
        float contentHeight = specifiedContentHeight == null
                ? naturalContentHeight
                : specifiedContentHeight;
        contentHeight = constrain(contentHeight,
                resolveHeightConstraint(style.minHeight(), containingHeight),
                resolveHeightConstraint(style.maxHeight(), containingHeight));
        float borderBoxHeight = border.top() + padding.top() + contentHeight
                + padding.bottom() + border.bottom();
        float outerHeight = Math.max(0, margin.top() + borderBoxHeight + margin.bottom());

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
        return new BlockLayout(outerHeight, List.copyOf(fragments));
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

    private static Float resolveConstraint(RenderLength length, float percentageBase) {
        return length.isAuto() ? null : Math.max(0, length.resolve(percentageBase));
    }

    private static Float resolveDefiniteHeight(RenderLength length, Float containingHeight) {
        if (length.isAuto()) {
            return null;
        }
        if (length.unit() == RenderLength.Unit.PERCENT) {
            return containingHeight == null ? null : Math.max(0, length.resolve(containingHeight));
        }
        return Math.max(0, length.value());
    }

    private static Float resolveHeightConstraint(RenderLength length, Float containingHeight) {
        return resolveDefiniteHeight(length, containingHeight);
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
                : Math.max(0, image.style().width().resolve(percentageBase));
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
        float decoration = style.margin().horizontal() + style.borderWidth().horizontal()
                + style.padding().horizontal();
        if (!style.width().isAuto()) {
            float width = constrain(style.width().resolve(percentageBase),
                    resolveConstraint(style.minWidth(), percentageBase),
                    resolveConstraint(style.maxWidth(), percentageBase)) + decoration;
            return new IntrinsicWidths(width, width);
        }
        IntrinsicWidths content = intrinsicWidths(box.children(), percentageBase, graphics);
        float preferred = constrain(content.preferred(),
                resolveConstraint(style.minWidth(), percentageBase),
                resolveConstraint(style.maxWidth(), percentageBase));
        float minimum = constrain(content.minimum(),
                resolveConstraint(style.minWidth(), percentageBase),
                resolveConstraint(style.maxWidth(), percentageBase));
        return new IntrinsicWidths(preferred + decoration, minimum + decoration);
    }

    private IntrinsicWidths intrinsicNodeWidth(RenderNode node,
                                               float percentageBase,
                                               Graphics2D graphics) {
        if (node instanceof RenderTextRun run) {
            FontMetrics metrics = graphics.getFontMetrics(InlineLayouter.fontFor(run.style()));
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
                text.height(), text.font(), text.color(), effective);
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
                               ClipRect clip) implements InlineFragment {
        public TextFragment(String text, float x, float width, float baseline, float top,
                            float height, Font font, CssColor color) {
            this(text, x, width, baseline, top, height, font, color, null);
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

    private record IntrinsicWidths(float preferred, float minimum) {
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
            this.line = new LineBuilder(graphics, activeBoxes);
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
            BlockLayout block = layoutBlock(
                    inlineBlock.box(), 0, 0, width, containingHeight, true,
                    graphics, atomicLines);
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
                    line.addText(word.substring(offset), font, metrics, style.color());
                    return;
                }

                float remaining = Math.max(1, width - line.width());
                int end = longestFittingEnd(word, offset, metrics, remaining);
                line.addText(word.substring(offset, end), font, metrics, style.color());
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
                line.addText(" ", font, metrics, pendingSpaceStyle.color());
            }
            pendingSpace = false;
            pendingSpaceStyle = null;
        }

        private void flushLine(boolean force, RenderStyle fallbackStyle) {
            if (!line.hasContent()) {
                if (force && fallbackStyle != null) {
                    line.addStrut(fontFor(fallbackStyle));
                } else {
                    line = new LineBuilder(graphics, activeBoxes);
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
            line = new LineBuilder(graphics, activeBoxes);
        }

        private static Font fontFor(RenderStyle style) {
            int awtStyle = Font.PLAIN;
            if (style.bold()) {
                awtStyle |= Font.BOLD;
            }
            if (style.italic()) {
                awtStyle |= Font.ITALIC;
            }
            return new Font(Font.SANS_SERIF, awtStyle, Math.max(1, Math.round(style.fontSizePx())));
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
                            CssColor color) implements LineItem {
        @Override public float ascent() { return metrics.getAscent(); }
        @Override public float descent() { return metrics.getDescent() + metrics.getLeading(); }
    }

    private record StrutItem(FontMetrics metrics) implements LineItem {
        @Override public float ascent() { return metrics.getAscent(); }
        @Override public float descent() { return metrics.getDescent() + metrics.getLeading(); }
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

    private static final class BoxItem implements LineItem {
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
            FontMetrics ownMetrics = graphics.getFontMetrics(InlineLayouter.fontFor(box.style()));
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

    private static final class LineBuilder {
        private final Graphics2D graphics;
        private final List<LineItem> roots = new ArrayList<>();
        private final List<BoxItem> active = new ArrayList<>();
        private float width;
        private boolean placedContent;
        private boolean structuralContent;

        LineBuilder(Graphics2D graphics, List<RenderInlineBox> continuingBoxes) {
            this.graphics = graphics;
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
                     CssColor color) {
            float itemWidth = metrics.stringWidth(text);
            addItem(new TextItem(text, width, itemWidth, font, metrics, color));
            width += itemWidth;
            placedContent = true;
        }

        void addStrut(Font font) {
            addItem(new StrutItem(graphics.getFontMetrics(font)));
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
            collectBoxFragments(roots, fragments, lineX, baseline);
            collectTextFragments(roots, fragments, lineX, baseline);
            collectImageFragments(roots, fragments, lineX, lineY, height, baseline);
            List<PaintFragment> atomicFragments = new ArrayList<>();
            List<LineBox> atomicLines = new ArrayList<>();
            collectAtomicFragments(
                    roots, atomicFragments, atomicLines, lineX, lineY, height, baseline);
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

        private static void collectBoxFragments(List<LineItem> items,
                                                List<InlineFragment> fragments,
                                                float lineX,
                                                float baseline) {
            for (LineItem item : items) {
                if (item instanceof BoxItem box) {
                    fragments.add(new InlineBoxFragment(
                            box.box,
                            lineX + box.x,
                            baseline - box.ascent,
                            box.width,
                            box.ascent + box.descent,
                            box.firstFragment,
                            box.lastFragment));
                    collectBoxFragments(box.children, fragments, lineX, baseline);
                }
            }
        }

        private static void collectTextFragments(List<LineItem> items,
                                                 List<InlineFragment> fragments,
                                                 float lineX,
                                                 float baseline) {
            for (LineItem item : items) {
                if (item instanceof TextItem text) {
                    fragments.add(new TextFragment(
                            text.text,
                            lineX + text.x,
                            text.width,
                            baseline,
                            baseline - text.metrics.getAscent(),
                            text.metrics.getHeight(),
                            text.font,
                            text.color));
                } else if (item instanceof BoxItem box) {
                    collectTextFragments(box.children, fragments, lineX, baseline);
                }
            }
        }

        private static void collectImageFragments(List<LineItem> items,
                                                  List<InlineFragment> fragments,
                                                  float lineX,
                                                  float lineY,
                                                  float lineHeight,
                                                  float baseline) {
            for (LineItem item : items) {
                if (item instanceof ImageItem image) {
                    float top = switch (image.verticalAlign()) {
                        case TOP -> lineY;
                        case BOTTOM -> lineY + lineHeight - image.height();
                        case BASELINE, MIDDLE -> baseline - image.ascent();
                    };
                    fragments.add(new ImageFragment(image.image(), image.layout().bitmap(),
                            lineX + image.x(), top,
                            image.layout().width(), image.layout().height()));
                } else if (item instanceof BoxItem box) {
                    collectImageFragments(box.children, fragments, lineX, lineY,
                            lineHeight, baseline);
                }
            }
        }

        private static void collectAtomicFragments(List<LineItem> items,
                                                   List<PaintFragment> fragments,
                                                   List<LineBox> lines,
                                                   float lineX,
                                                   float lineY,
                                                   float lineHeight,
                                                   float baseline) {
            for (LineItem item : items) {
                if (item instanceof AtomicItem atomic) {
                    float dx = lineX + atomic.x();
                    float dy = switch (atomic.verticalAlign()) {
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
                    collectAtomicFragments(box.children, fragments, lines, lineX, lineY,
                            lineHeight, baseline);
                }
            }
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
                    text.color(), translate(text.clip(), dx, dy));
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

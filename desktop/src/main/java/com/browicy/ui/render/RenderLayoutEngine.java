package com.browicy.ui.render;

import com.browicy.engine.render.BoxEdges;
import com.browicy.engine.render.CssColor;
import com.browicy.engine.render.RenderBox;
import com.browicy.engine.render.RenderInlineBox;
import com.browicy.engine.render.RenderLineBreak;
import com.browicy.engine.render.RenderNode;
import com.browicy.engine.render.RenderStyle;
import com.browicy.engine.render.RenderTextRun;
import com.browicy.engine.render.RenderTree;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/** Performs width-dependent block and inline layout without painting anything. */
public final class RenderLayoutEngine {

    public LayoutResult layout(RenderTree tree, int viewportWidth, Insets insets, Graphics2D graphics) {
        float availableWidth = Math.max(1, viewportWidth - insets.left - insets.right);
        List<LineBox> lineBoxes = new ArrayList<>();
        BlockLayout root = layoutBlock(
                tree.root(), insets.left, insets.top, availableWidth, graphics, lineBoxes);
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
                                    Graphics2D graphics,
                                    List<LineBox> lineBoxes) {
        RenderStyle style = box.style();
        BoxEdges margin = style.margin();
        BoxEdges padding = style.padding();
        BoxEdges border = style.borderWidth();

        float borderX = containingX + margin.left();
        float borderY = y + margin.top();
        float borderBoxWidth = Math.max(1, availableWidth - margin.horizontal());
        float contentX = borderX + border.left() + padding.left();
        float contentY = borderY + border.top() + padding.top();
        float contentWidth = Math.max(1,
                borderBoxWidth - border.horizontal() - padding.horizontal());

        List<PaintFragment> childFragments = new ArrayList<>();
        List<RenderNode> inlineBuffer = new ArrayList<>();
        float currentY = contentY;

        for (RenderNode child : box.children()) {
            if (child instanceof RenderBox childBox) {
                currentY += flushInline(inlineBuffer, contentX, currentY, contentWidth,
                        graphics, childFragments, lineBoxes);
                BlockLayout childLayout = layoutBlock(
                        childBox, contentX, currentY, contentWidth, graphics, lineBoxes);
                childFragments.addAll(childLayout.fragments());
                currentY += childLayout.outerHeight();
            } else {
                inlineBuffer.add(child);
            }
        }
        currentY += flushInline(inlineBuffer, contentX, currentY, contentWidth,
                graphics, childFragments, lineBoxes);

        float contentHeight = Math.max(0, currentY - contentY);
        float borderBoxHeight = border.top() + padding.top() + contentHeight
                + padding.bottom() + border.bottom();
        float outerHeight = Math.max(0, margin.top() + borderBoxHeight + margin.bottom());

        List<PaintFragment> fragments = new ArrayList<>(childFragments.size() + 1);
        fragments.add(new BoxFragment(box, borderX, borderY, borderBoxWidth, borderBoxHeight));
        fragments.addAll(childFragments);
        return new BlockLayout(outerHeight, List.copyOf(fragments));
    }

    private float flushInline(List<RenderNode> inlineNodes,
                              float x,
                              float y,
                              float width,
                              Graphics2D graphics,
                              List<PaintFragment> target,
                              List<LineBox> lineBoxes) {
        if (inlineNodes.isEmpty()) {
            return 0;
        }
        InlineLayouter layouter = new InlineLayouter(
                x, y, width, graphics, target, lineBoxes);
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
    }

    public interface InlineFragment extends PaintFragment {
        float x();
        float width();
    }

    public record BoxFragment(RenderBox box, float x, float y, float width, float height)
            implements PaintFragment {
        @Override public float top() { return y; }
        @Override public float bottom() { return y + height; }
    }

    public record InlineBoxFragment(RenderInlineBox box,
                                    float x,
                                    float y,
                                    float width,
                                    float height,
                                    boolean firstFragment,
                                    boolean lastFragment) implements InlineFragment {
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
                               CssColor color) implements InlineFragment {
        @Override public float bottom() { return top + height; }
    }

    /** A concrete line in an inline formatting context. */
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

    private sealed interface InlineToken
            permits OpenBoxToken, CloseBoxToken, WordToken, SpaceToken, BreakToken {
    }

    private record OpenBoxToken(RenderInlineBox box) implements InlineToken {
    }

    private record CloseBoxToken(RenderInlineBox box) implements InlineToken {
    }

    private record WordToken(String text, RenderStyle style) implements InlineToken {
    }

    private record SpaceToken(RenderStyle style) implements InlineToken {
    }

    private record BreakToken(RenderStyle style) implements InlineToken {
    }

    private static final class InlineLayouter {
        private final float startY;
        private final float x;
        private final float width;
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
                       Graphics2D graphics,
                       List<PaintFragment> target,
                       List<LineBox> lineTarget) {
            this.x = x;
            this.y = y;
            this.startY = y;
            this.width = width;
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
                }
            }
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

            LineBox lineBox = line.finish(x, y);
            lineTarget.add(lineBox);
            target.addAll(lineBox.fragments());
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

    private sealed interface LineItem permits TextItem, BoxItem, StrutItem {
        float ascent();
        float descent();
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

        LineBox finish(float lineX, float lineY) {
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
            for (LineItem item : roots) {
                ascent = Math.max(ascent, item.ascent());
                descent = Math.max(descent, item.descent());
            }
            float baseline = lineY + ascent;
            float height = ascent + descent;

            List<InlineFragment> fragments = new ArrayList<>();
            collectBoxFragments(roots, fragments, lineX, baseline);
            collectTextFragments(roots, fragments, lineX, baseline);
            return new LineBox(lineX, lineY, width, height, baseline, fragments);
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
    }
}

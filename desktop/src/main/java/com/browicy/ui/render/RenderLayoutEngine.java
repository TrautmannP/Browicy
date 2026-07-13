package com.browicy.ui.render;

import com.browicy.engine.render.BoxBorders;
import com.browicy.engine.render.BoxEdges;
import com.browicy.engine.render.CssColor;
import com.browicy.engine.render.RenderBox;
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
        BlockLayout root = layoutBlock(tree.root(), insets.left, insets.top, availableWidth, graphics);
        float height = insets.top + root.outerHeight() + insets.bottom;
        return new LayoutResult(viewportWidth, Math.max(insets.top + insets.bottom, height), root.fragments());
    }

    private BlockLayout layoutBlock(RenderBox box,
                                    float containingX,
                                    float y,
                                    float availableWidth,
                                    Graphics2D graphics) {
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
                        graphics, childFragments);
                BlockLayout childLayout = layoutBlock(
                        childBox, contentX, currentY, contentWidth, graphics);
                childFragments.addAll(childLayout.fragments());
                currentY += childLayout.outerHeight();
            } else {
                inlineBuffer.add(child);
            }
        }
        currentY += flushInline(inlineBuffer, contentX, currentY, contentWidth,
                graphics, childFragments);

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
                              List<PaintFragment> target) {
        if (inlineNodes.isEmpty()) {
            return 0;
        }
        InlineLayouter layouter = new InlineLayouter(x, y, width, graphics, target);
        for (RenderNode node : inlineNodes) {
            if (node instanceof RenderTextRun run) {
                layouter.addText(run.text(), run.style());
            } else if (node instanceof RenderLineBreak lineBreak) {
                layouter.lineBreak(lineBreak.style());
            }
        }
        inlineNodes.clear();
        return layouter.finish();
    }

    public record LayoutResult(int width, float height, List<PaintFragment> fragments) {
        public LayoutResult {
            fragments = List.copyOf(fragments);
        }
    }

    public interface PaintFragment {
        float top();
        float bottom();
    }

    public record BoxFragment(RenderBox box, float x, float y, float width, float height)
            implements PaintFragment {
        @Override public float top() { return y; }
        @Override public float bottom() { return y + height; }
    }

    public record TextFragment(String text,
                               float x,
                               float baseline,
                               float top,
                               float height,
                               Font font,
                               CssColor color) implements PaintFragment {
        @Override public float bottom() { return top + height; }
    }

    private record BlockLayout(float outerHeight, List<PaintFragment> fragments) {
    }

    private static final class InlineLayouter {
        private final float startY;
        private final float x;
        private final float width;
        private final Graphics2D graphics;
        private final List<PaintFragment> target;
        private final List<LineItem> lineItems = new ArrayList<>();
        private float y;
        private float lineWidth;
        private int maxAscent;
        private int maxDescent;
        private int maxLeading;
        private boolean pendingSpace;

        InlineLayouter(float x,
                       float y,
                       float width,
                       Graphics2D graphics,
                       List<PaintFragment> target) {
            this.x = x;
            this.y = y;
            this.startY = y;
            this.width = width;
            this.graphics = graphics;
            this.target = target;
        }

        void addText(String text, RenderStyle style) {
            int offset = 0;
            while (offset < text.length()) {
                int codePoint = text.codePointAt(offset);
                if (Character.isWhitespace(codePoint)) {
                    pendingSpace = true;
                    offset += Character.charCount(codePoint);
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
                addWord(text.substring(offset, end), style);
                offset = end;
            }
        }

        void lineBreak(RenderStyle style) {
            flushLine(true, style);
            pendingSpace = false;
        }

        float finish() {
            flushLine(false, null);
            return y - startY;
        }

        private void addWord(String word, RenderStyle style) {
            Font font = fontFor(style);
            FontMetrics metrics = graphics.getFontMetrics(font);
            String prefix = pendingSpace && !lineItems.isEmpty() ? " " : "";
            pendingSpace = false;

            String candidate = prefix + word;
            if (!lineItems.isEmpty() && lineWidth + metrics.stringWidth(candidate) > width) {
                flushLine(false, null);
                prefix = "";
                candidate = word;
            }
            if (metrics.stringWidth(candidate) <= width - lineWidth) {
                addItem(candidate, font, metrics, style.color());
                return;
            }

            int offset = 0;
            while (offset < word.length()) {
                if (!lineItems.isEmpty() && lineWidth >= width) {
                    flushLine(false, null);
                }
                float remaining = Math.max(1, width - lineWidth);
                int end = longestFittingEnd(word, offset, metrics, remaining);
                addItem(word.substring(offset, end), font, metrics, style.color());
                offset = end;
                if (offset < word.length()) {
                    flushLine(false, null);
                }
            }
        }

        private void addItem(String text, Font font, FontMetrics metrics, CssColor color) {
            lineItems.add(new LineItem(text, lineWidth, font, metrics, color));
            lineWidth += metrics.stringWidth(text);
            maxAscent = Math.max(maxAscent, metrics.getAscent());
            maxDescent = Math.max(maxDescent, metrics.getDescent());
            maxLeading = Math.max(maxLeading, metrics.getLeading());
        }

        private void flushLine(boolean force, RenderStyle fallbackStyle) {
            if (lineItems.isEmpty()) {
                if (force && fallbackStyle != null) {
                    FontMetrics metrics = graphics.getFontMetrics(fontFor(fallbackStyle));
                    y += metrics.getHeight();
                }
                resetLine();
                return;
            }

            float baseline = y + maxAscent;
            for (LineItem item : lineItems) {
                target.add(new TextFragment(
                        item.text(),
                        x + item.offsetX(),
                        baseline,
                        baseline - item.metrics().getAscent(),
                        item.metrics().getHeight(),
                        item.font(),
                        item.color()));
            }
            y += maxAscent + maxDescent + maxLeading;
            resetLine();
        }

        private void resetLine() {
            lineItems.clear();
            lineWidth = 0;
            maxAscent = 0;
            maxDescent = 0;
            maxLeading = 0;
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

    private record LineItem(String text,
                            float offsetX,
                            Font font,
                            FontMetrics metrics,
                            CssColor color) {
    }
}

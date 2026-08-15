package com.browicy.ui.render;

import com.browicy.engine.render.CssColor;
import com.browicy.engine.render.RenderBox;
import com.browicy.engine.render.RenderImage;
import com.browicy.engine.render.RenderInlineBlock;
import com.browicy.engine.render.RenderInlineBox;
import com.browicy.engine.render.RenderLineBreak;
import com.browicy.engine.render.RenderNode;
import com.browicy.engine.render.RenderStyle;
import com.browicy.engine.render.RenderTextRun;
import com.browicy.ui.render.FloatExclusionSpace.LineSlot;
import com.browicy.ui.render.RenderLayoutEngine.ImageFragment;
import com.browicy.ui.render.RenderLayoutEngine.InlineBoxFragment;
import com.browicy.ui.render.RenderLayoutEngine.InlineFragment;
import com.browicy.ui.render.RenderLayoutEngine.LineBox;
import com.browicy.ui.render.RenderLayoutEngine.PaintFragment;
import com.browicy.ui.render.RenderLayoutEngine.TextFragment;

import static com.browicy.ui.render.RenderLayoutEngine.textWidth;
import static com.browicy.ui.render.PositionedLayout.translate;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

final class InlineLayout {

    interface Host {
        Font fontFor(RenderStyle style);

        ImageLayout imageLayout(RenderImage image, float percentageBase,
                                Float containingHeight);

        AtomicLayout layoutAtomic(RenderInlineBlock inlineBlock, float width,
                                  Float containingHeight, Graphics2D graphics);

        float relativeHorizontalOffset(RenderStyle style, float containingWidth);

        float relativeVerticalOffset(RenderStyle style, Float containingHeight);
    }

    static final class LineConstraints {
        private final FloatExclusionSpace floats;
        private final float contentX;
        private final float contentWidth;

        LineConstraints(FloatExclusionSpace floats, float contentX, float contentWidth) {
            this.floats = floats;
            this.contentX = contentX;
            this.contentWidth = contentWidth;
        }

        LineSlot slot(float y, float minHeight, float minWidth) {
            return floats.lineSlot(contentX, contentWidth, y, minHeight, minWidth);
        }
    }

    private final Host host;
    private final LineConstraints constraints;
    private final float startY;
    private final float contentWidth;
    private final Float containingHeight;
    private final RenderStyle.TextAlign textAlign;
    private final Graphics2D graphics;
    private final List<PaintFragment> target;
    private final List<LineBox> lineTarget;
    private final List<RenderInlineBox> activeBoxes = new ArrayList<>();
    private final List<InlineToken> tokens = new ArrayList<>();
    private LineBuilder line;
    private float y;
    private float lineX;
    private float lineWidth;
    private boolean pendingSpace;
    private String pendingSpaceText = " ";
    private RenderStyle pendingSpaceStyle;

    InlineLayout(Host host,
                 LineConstraints constraints,
                 float y,
                 float contentWidth,
                 Float containingHeight,
                 RenderStyle.TextAlign textAlign,
                 Graphics2D graphics,
                 List<PaintFragment> target,
                 List<LineBox> lineTarget) {
        this.host = host;
        this.constraints = constraints;
        this.y = y;
        this.startY = y;
        this.contentWidth = contentWidth;
        this.containingHeight = containingHeight;
        this.textAlign = textAlign;
        this.graphics = graphics;
        this.target = target;
        this.lineTarget = lineTarget;
        this.lineX = 0;
        this.lineWidth = contentWidth;
        this.line = new LineBuilder(graphics, activeBoxes, contentWidth, containingHeight);
    }

    private void placeLine(float minHeight, float minWidth) {
        LineSlot slot = constraints.slot(y, minHeight, minWidth);
        y = slot.y();
        lineX = slot.x();
        lineWidth = slot.width();
    }

    void layout(List<RenderNode> nodes) {
        appendTokens(nodes);
        for (int index = 0; index < tokens.size(); index++) {
            InlineToken token = tokens.get(index);
            if (token instanceof SpaceToken(String text, RenderStyle style)) {
                pendingSpace = true;
                pendingSpaceText = text;
                if (pendingSpaceStyle == null) {
                    pendingSpaceStyle = style;
                }
            } else if (token instanceof OpenBoxToken(RenderInlineBox box)) {
                openBox(box);
            } else if (token instanceof CloseBoxToken(RenderInlineBox box)) {
                closeBox(box);
            } else if (token instanceof AtomicBlockToken(RenderInlineBlock block)) {
                addAtomicBlock(block);
            } else if (token instanceof ImageToken(RenderImage image1)) {
                addImage(image1);
            } else if (token instanceof BreakToken(RenderStyle style)) {
                pendingSpace = false;
                pendingSpaceStyle = null;
                flushLine(true, style);
            } else if (token instanceof WordToken(String text, RenderStyle style)) {
                addWord(text, style, closingDecorationWidthAfter(index));
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
            } else if (node instanceof RenderLineBreak(RenderStyle style)) {
                tokens.add(new BreakToken(style));
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
        ImageLayout layout = host.imageLayout(image, contentWidth, containingHeight);
        if (!line.hasPlacedContent()) {
            placeLine(layout.height(), layout.width());
        }
        float pendingWidth = pendingSpaceWidth();
        if (line.hasPlacedContent()
                && line.width() + pendingWidth + layout.width() > lineWidth) {
            pendingSpace = false;
            pendingSpaceStyle = null;
            flushLine(false, null);
            placeLine(layout.height(), layout.width());
        } else {
            materializePendingSpace();
        }
        line.addImage(image, layout);
    }

    private void addAtomicBlock(RenderInlineBlock inlineBlock) {
        AtomicLayout atomic = host.layoutAtomic(inlineBlock, contentWidth, containingHeight, graphics);
        if (!line.hasPlacedContent()) {
            placeLine(atomic.height(), atomic.width());
        }
        float pendingWidth = pendingSpaceWidth();
        if (line.hasPlacedContent()
                && line.width() + pendingWidth + atomic.width() > lineWidth) {
            pendingSpace = false;
            pendingSpaceStyle = null;
            flushLine(false, null);
            placeLine(atomic.height(), atomic.width());
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
        if (!line.hasContent()) {
            placeLine(0, openingWidth);
        }
        if (line.hasPlacedContent()
                && line.width() + pendingWidth + openingWidth > lineWidth) {
            pendingSpace = false;
            pendingSpaceStyle = null;
            flushLine(false, null);
            placeLine(0, openingWidth);
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
        Font font = host.fontFor(style);
        FontMetrics metrics = graphics.getFontMetrics(font);
        float spaceWidth = pendingSpaceWidth();
        float wordWidth = textWidth(word, metrics, style.letterSpacingPx());
        boolean wrapAllowed = switch (style.whiteSpace()) {
            case NOWRAP, PRE -> false;
            default -> true;
        };
        float lineHeight = style.usedLineHeightPx() > 0
                ? style.usedLineHeightPx() : metrics.getHeight();

        if (!line.hasPlacedContent()) {
            lineHeight = style.usedLineHeightPx() > 0
                    ? style.usedLineHeightPx() : metrics.getHeight();
            placeLine(lineHeight, wordWidth + trailingDecorationWidth);
        }
        if (wrapAllowed && line.hasPlacedContent()
                && line.width() + spaceWidth + wordWidth + trailingDecorationWidth > lineWidth) {
            pendingSpace = false;
            pendingSpaceStyle = null;
            flushLine(false, null);
            placeLine(lineHeight, wordWidth + trailingDecorationWidth);
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
            if (finalWidth + trailingDecorationWidth <= lineWidth - line.width()) {
                line.addText(word.substring(offset), font, metrics, style);
                return;
            }

            float remaining = Math.max(1, lineWidth - line.width());
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
        FontMetrics metrics = graphics.getFontMetrics(host.fontFor(pendingSpaceStyle));
        return textWidth(pendingSpaceText, metrics, pendingSpaceStyle.letterSpacingPx());
    }

    private void materializePendingSpace() {
        if (pendingSpace && line.hasPlacedContent() && pendingSpaceStyle != null) {
            Font font = host.fontFor(pendingSpaceStyle);
            FontMetrics metrics = graphics.getFontMetrics(font);
            line.addText(pendingSpaceText, font, metrics, pendingSpaceStyle);
        }
        pendingSpace = false;
        pendingSpaceStyle = null;
    }

    private void flushLine(boolean force, RenderStyle fallbackStyle) {
        if (!line.hasContent()) {
            if (force && fallbackStyle != null) {
                line.addStrut(host.fontFor(fallbackStyle), fallbackStyle);
            } else {
                line = new LineBuilder(graphics, activeBoxes, contentWidth, containingHeight);
                return;
            }
        }

        float alignmentOffset = switch (textAlign) {
            case CENTER -> Math.max(0, lineWidth - line.width()) / 2f;
            case RIGHT -> Math.max(0, lineWidth - line.width());
            case LEFT -> 0;
        };
        FinishedLine finished = line.finish(lineX + alignmentOffset, y);
        LineBox lineBox = finished.line();
        lineTarget.add(lineBox);
        lineTarget.addAll(finished.atomicLines());
        target.addAll(lineBox.fragments());
        target.addAll(finished.atomicFragments());
        y += lineBox.height();
        line = new LineBuilder(graphics, activeBoxes, contentWidth, containingHeight);
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

        @Override
        public float ascent() {
            return Math.max(0, metrics.getAscent() + adjustment());
        }

        @Override
        public float descent() {
            return Math.max(0, metrics.getDescent() + metrics.getLeading() + adjustment());
        }
    }

    private record StrutItem(FontMetrics metrics, float usedLineHeight) implements LineItem {
        private float adjustment() {
            return usedLineHeight <= 0 ? 0 : (usedLineHeight - metrics.getHeight()) / 2f;
        }

        @Override
        public float ascent() {
            return Math.max(0, metrics.getAscent() + adjustment());
        }

        @Override
        public float descent() {
            return Math.max(0, metrics.getDescent() + metrics.getLeading() + adjustment());
        }
    }

    private record AtomicItem(AtomicLayout layout, float x) implements LineItem {
        @Override
        public float ascent() {
            if (layout.verticalAlign() == RenderStyle.VerticalAlign.MIDDLE) {
                return Math.min(layout.height(), layout.height() / 2f + layout.fontSize() / 4f);
            }
            return layout.baselineOffset();
        }

        @Override
        public float descent() {
            return layout.height() - ascent();
        }

        @Override
        public RenderStyle.VerticalAlign verticalAlign() {
            return layout.verticalAlign();
        }

        @Override
        public float height() {
            return layout.height();
        }
    }

    private record ImageItem(RenderImage image, ImageLayout layout, float x) implements LineItem {
        @Override
        public float ascent() {
            if (layout.verticalAlign() == RenderStyle.VerticalAlign.MIDDLE) {
                return Math.min(layout.height(), layout.height() / 2f + layout.fontSize() / 4f);
            }
            return layout.height();
        }

        @Override
        public float descent() {
            return layout.height() - ascent();
        }

        @Override
        public RenderStyle.VerticalAlign verticalAlign() {
            return layout.verticalAlign();
        }

        @Override
        public float height() {
            return layout.height();
        }
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
            FontMetrics ownMetrics = graphics.getFontMetrics(host.fontFor(box.style()));
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

        @Override
        public float ascent() {
            return ascent;
        }

        @Override
        public float descent() {
            return descent;
        }
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
                        lines.add(translateLineBox(line, dx, dy));
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
                    ? host.relativeHorizontalOffset(style, containingWidth) : 0;
        }

        private float inlineOffsetY(RenderStyle style, Float containingHeight) {
            return style.position() == RenderStyle.Position.RELATIVE
                    ? host.relativeVerticalOffset(style, containingHeight) : 0;
        }

        private static LineBox translateLineBox(LineBox line, float dx, float dy) {
            List<InlineFragment> fragments = line.fragments().stream()
                    .map(fragment -> (InlineFragment) translate(fragment, dx, dy))
                    .toList();
            return new LineBox(line.x() + dx, line.y() + dy, line.width(), line.height(),
                    line.baseline() + dy, fragments);
        }
    }
}

record AtomicLayout(RenderLayoutEngine.BlockLayout block,
                    List<LineBox> lines,
                    float width,
                    float height,
                    RenderStyle.VerticalAlign verticalAlign,
                    float fontSize,
                    float baselineOffset) {
}

record ImageLayout(java.awt.image.BufferedImage bitmap,
                   float width,
                   float height,
                   RenderStyle.VerticalAlign verticalAlign,
                   float fontSize) {
}

record FinishedLine(LineBox line,
                    List<PaintFragment> atomicFragments,
                    List<LineBox> atomicLines) {
}

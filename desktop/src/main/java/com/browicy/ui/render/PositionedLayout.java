package com.browicy.ui.render;

import com.browicy.engine.render.RenderBox;
import com.browicy.engine.render.RenderOffset;
import com.browicy.engine.render.RenderStyle;
import com.browicy.ui.render.RenderLayoutEngine.BlockLayout;
import com.browicy.ui.render.RenderLayoutEngine.BoxFragment;
import com.browicy.ui.render.RenderLayoutEngine.ClipRect;
import com.browicy.ui.render.RenderLayoutEngine.ImageFragment;
import com.browicy.ui.render.RenderLayoutEngine.InlineBoxFragment;
import com.browicy.ui.render.RenderLayoutEngine.InlineFragment;
import com.browicy.ui.render.RenderLayoutEngine.LineBox;
import com.browicy.ui.render.RenderLayoutEngine.PaintFragment;
import com.browicy.ui.render.RenderLayoutEngine.TextFragment;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class PositionedLayout {

    interface Host {
        BlockLayout layoutBlock(RenderBox box, float containingX, float y, float availableWidth,
                                Float containingHeight, boolean shrinkToFitAuto,
                                Graphics2D graphics, List<LineBox> lineBoxes,
                                PositionedContext positionedContext);

        float resolve(RenderOffset offset, float percentageBase);
    }

    private final Host host;

    PositionedLayout(Host host) {
        this.host = java.util.Objects.requireNonNull(host, "host");
    }

    List<PaintFragment> layoutAbsoluteRequests(PositionedContext context,
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
            float left = style.left().isAuto() ? 0 : host.resolve(style.left(), context.width);
            float right = style.right().isAuto() ? 0 : host.resolve(style.right(), context.width);
            boolean stretchAutoWidth = style.width().isAuto()
                    && !style.left().isAuto() && !style.right().isAuto();
            float availableWidth = stretchAutoWidth
                    ? Math.max(0, context.width - left - right)
                    : context.width;
            int firstLine = lineBoxes.size();
            BlockLayout layout = host.layoutBlock(
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
                desiredY = context.y + host.resolve(style.top(), context.height) + style.margin().top();
            } else if (!style.bottom().isAuto()) {
                desiredY = context.y + context.height - host.resolve(style.bottom(), context.height)
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

    BlockLayout positionRootInInitialContainingBlock(BlockLayout rootLayout,
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
        float left = style.left().isAuto() ? 0 : host.resolve(style.left(), icb.width);
        float right = style.right().isAuto() ? 0 : host.resolve(style.right(), icb.width);
        float top = style.top().isAuto() ? 0 : host.resolve(style.top(), icb.height);
        float bottom = style.bottom().isAuto() ? 0 : host.resolve(style.bottom(), icb.height);
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
            current = host.layoutBlock(root, icb.x, icb.y, availableWidth, icb.height,
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

    static boolean isHiddenScrollButton(RenderBox box, PositionedContext context) {
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

    static float fragmentLeft(PaintFragment fragment) {
        if (fragment instanceof InlineFragment inline) {
            return inline.x();
        }
        if (fragment instanceof BoxFragment box) {
            return box.x();
        }
        return Float.POSITIVE_INFINITY;
    }

    static float fragmentRight(PaintFragment fragment) {
        if (fragment instanceof InlineFragment inline) {
            return inline.x() + inline.width();
        }
        if (fragment instanceof BoxFragment box) {
            return box.x() + box.width();
        }
        return Float.NEGATIVE_INFINITY;
    }

    static int fragmentZIndex(PaintFragment fragment) {
        if (fragment instanceof BoxFragment box) {
            return box.box().style().zIndex();
        }
        if (fragment instanceof InlineBoxFragment box) {
            return box.box().style().zIndex();
        }
        return 0;
    }

    static List<PaintFragment> mergeElevated(List<PaintFragment> elevated,
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

    static void translateLines(List<LineBox> lines, int first, float dx, float dy) {
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

    static PaintFragment translate(PaintFragment fragment, float dx, float dy) {
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

    static PaintFragment withTransform(PaintFragment fragment,
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

    static java.awt.geom.AffineTransform compose(
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

    static ClipRect translate(ClipRect clip, float dx, float dy) {
        return clip == null ? null
                : new ClipRect(clip.x() + dx, clip.y() + dy, clip.width(), clip.height());
    }

    static PaintFragment withClip(PaintFragment fragment, ClipRect clip) {
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

    static ClipRect intersect(ClipRect first, ClipRect second) {
        if (first == null) {
            return second;
        }
        float left = Math.max(first.x(), second.x());
        float top = Math.max(first.y(), second.y());
        float right = Math.min(first.x() + first.width(), second.x() + second.width());
        float bottom = Math.min(first.y() + first.height(), second.y() + second.height());
        return new ClipRect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    static final class PositionedContext {
        final List<AbsoluteRequest> requests = new ArrayList<>();
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

    record AbsoluteRequest(RenderBox box, float staticX, float staticY) {
    }
}

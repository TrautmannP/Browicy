package com.browicy.ui;

import com.browicy.engine.InvalidationType;
import com.browicy.engine.ImageResourceRegistry;
import com.browicy.engine.PageSession;
import com.browicy.engine.PageUpdate;
import com.browicy.engine.css.StyleApplicator;
import com.browicy.engine.css.StyleSheetRegistry;
import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Event;
import com.browicy.engine.js.PageRuntime;
import com.browicy.engine.render.BoxBorders;
import com.browicy.engine.render.BoxEdges;
import com.browicy.engine.render.CssColor;
import com.browicy.engine.render.RenderStyle;
import com.browicy.engine.render.RenderTree;
import com.browicy.engine.render.RenderTreeBuilder;
import com.browicy.ui.render.RenderLayoutEngine;
import com.browicy.ui.render.RenderLayoutEngine.BoxFragment;
import com.browicy.ui.render.RenderLayoutEngine.ClipRect;
import com.browicy.ui.render.RenderLayoutEngine.InlineBoxFragment;
import com.browicy.ui.render.RenderLayoutEngine.ImageFragment;
import com.browicy.ui.render.RenderLayoutEngine.LayoutResult;
import com.browicy.ui.render.RenderLayoutEngine.PaintFragment;
import com.browicy.ui.render.RenderLayoutEngine.TextFragment;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.JViewport;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.imageio.ImageIO;

public final class DomViewPanel extends JPanel implements Scrollable {

    private static final int CONTENT_PADDING = 16;
    private static final int DEFAULT_LAYOUT_WIDTH = 800;
    private static final int DEFAULT_VIEWPORT_HEIGHT = 600;
    private static final int SCROLL_UNIT = 24;

    private final Document document;
    private final PageRuntime runtime;
    private final ImageResourceRegistry images;
    private final StyleSheetRegistry styleSheets;
    private final Map<URI, Optional<BufferedImage>> backgroundImages = new ConcurrentHashMap<>();
    private RenderTree renderTree;
    private Element pressedTarget;
    private List<Element> hoveredElements = List.of();
    private final RenderLayoutEngine layoutEngine = new RenderLayoutEngine();
    private LayoutResult layoutResult;
    private int layoutWidth = -1;
    private int renderViewportWidth = -1;
    private int renderViewportHeight = -1;

    public DomViewPanel(Document document) {
        this(document, PageRuntime.closed(), new ImageResourceRegistry(), null);
    }

    public DomViewPanel(PageSession session) {
        this(session.document(), session.runtime(), session.images(), session.styleSheets());
    }

    DomViewPanel(Document document, PageRuntime runtime, ImageResourceRegistry images) {
        this(document, runtime, images, null);
    }

    private DomViewPanel(Document document,
                         PageRuntime runtime,
                         ImageResourceRegistry images,
                         StyleSheetRegistry styleSheets) {
        this.document = document;
        this.runtime = runtime;
        this.images = images;
        this.styleSheets = styleSheets;
        setLayout(null);
        setOpaque(true);
        setFocusable(true);
        setBackground(UiTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(
                CONTENT_PADDING, CONTENT_PADDING, CONTENT_PADDING, CONTENT_PADDING));
        rebuildRenderTree();

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                if (layoutWidth != getWidth() || renderViewportHeight != currentViewportHeight()) {
                    invalidateReflow();
                    revalidate();
                    repaint();
                }
            }
        });

        MouseAdapter mouseEvents = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                pressedTarget = hitTest(event.getX(), event.getY());
                dispatchDomEvent(pressedTarget, "mousedown", true, true);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                Element releasedTarget = hitTest(event.getX(), event.getY());
                dispatchDomEvent(releasedTarget, "mouseup", true, true);
                if (releasedTarget != null && releasedTarget == pressedTarget) {
                    dispatchDomEvent(releasedTarget, "click", true, true);
                }
                pressedTarget = null;
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                Element target = hitTest(event.getX(), event.getY());
                updateHover(target);
                dispatchDomEvent(target, "mousemove", true, false);
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                mouseMoved(event);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                updateHover(null);
            }
        };
        addMouseListener(mouseEvents);
        addMouseMotionListener(mouseEvents);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                dispatchDomEvent(document.getBody(), "keydown", true, true);
            }

            @Override
            public void keyReleased(KeyEvent event) {
                dispatchDomEvent(document.getBody(), "keyup", true, true);
            }
        });
    }

    public void refreshFromDocument() {
        applyInvalidation(InvalidationType.RENDER_TREE);
    }

    public void applyPageUpdate(PageUpdate update) {
        if (update.document() != document) {
            return;
        }
        applyInvalidation(update.invalidation());
    }

    private void applyInvalidation(InvalidationType invalidation) {
        if (invalidation.requires(InvalidationType.RENDER_TREE)) {
            rebuildRenderTree();
            revalidate();
            repaint();
        } else if (invalidation.requires(InvalidationType.LAYOUT)) {
            invalidateReflow();
            revalidate();
            repaint();
        } else {
            repaint();
        }
    }

    private void rebuildRenderTree() {
        rebuildRenderTree(currentLayoutWidth(), currentViewportHeight());
    }

    private void rebuildRenderTree(int viewportWidth, int viewportHeight) {
        synchronized (document) {
            renderTree = new RenderTreeBuilder(element -> images.find(element)
                    .map(com.browicy.engine.net.BinaryResource::content)
                    .orElse(null)).build(document, viewportWidth, viewportHeight);
        }
        renderViewportWidth = viewportWidth;
        renderViewportHeight = viewportHeight;
        invalidateReflow();
    }

    private Element hitTest(int x, int y) {
        ensureLayoutForHitTesting();
        if (layoutResult == null) {
            return null;
        }
        List<PaintFragment> fragments = layoutResult.fragments();
        for (int index = fragments.size() - 1; index >= 0; index--) {
            PaintFragment fragment = fragments.get(index);
            Element source = null;
            float left;
            float width;
            if (fragment instanceof BoxFragment box) {
                source = box.box().source();
                left = box.x();
                width = box.width();
            } else if (fragment instanceof InlineBoxFragment inline) {
                source = inline.box().source();
                left = inline.x();
                width = inline.width();
            } else if (fragment instanceof ImageFragment image) {
                source = image.image().source();
                left = image.x();
                width = image.width();
            } else {
                continue;
            }
            ClipRect fragmentClip = fragment.clip();
            if (fragmentClip != null && (x < fragmentClip.x()
                    || x >= fragmentClip.x() + fragmentClip.width()
                    || y < fragmentClip.y()
                    || y >= fragmentClip.y() + fragmentClip.height())) {
                continue;
            }
            if (source != null && x >= left && x < left + width
                    && y >= fragment.top() && y < fragment.bottom()) {
                return source;
            }
        }
        return document.getBody();
    }

    private void ensureLayoutForHitTesting() {
        int width = Math.max(1, currentLayoutWidth());
        if (layoutWidth == width && layoutResult != null) {
            return;
        }
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            configureGraphics(graphics);
            ensureLayout(width, graphics);
        } finally {
            graphics.dispose();
        }
    }

    private void dispatchDomEvent(Element target, String type, boolean bubbles, boolean cancelable) {
        if (target != null && !runtime.isClosed()) {
            runtime.dispatchEvent(target, new Event(type, bubbles, cancelable));
        }
    }

    private void updateHover(Element target) {
        List<Element> next = new ArrayList<>();
        for (com.browicy.engine.dom.Node node = target;
             node instanceof Element element;
             node = node.getParent()) {
            next.add(element);
        }
        if (next.equals(hoveredElements)) return;
        Element previousTarget = hoveredElements.isEmpty() ? null : hoveredElements.getFirst();
        hoveredElements.forEach(element -> element.setHovered(false));
        next.forEach(element -> element.setHovered(true));
        hoveredElements = List.copyOf(next);
        synchronized (document) {
            StyleApplicator applicator = new StyleApplicator();
            if (styleSheets == null) applicator.apply(document);
            else applicator.apply(document, styleSheets);
        }
        rebuildRenderTree();
        revalidate();
        repaint();
        if (previousTarget != target) {
            dispatchDomEvent(previousTarget, "mouseout", true, false);
            dispatchDomEvent(target, "mouseover", true, false);
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            configureGraphics(g2d);
            boolean heightChanged = ensureLayout(Math.max(1, getWidth()), g2d);
            if (heightChanged) {
                revalidate();
            }

            Rectangle clip = g2d.getClipBounds();
            for (PaintFragment fragment : layoutResult.fragments()) {
                if (clip != null && (fragment.bottom() <= clip.y
                        || fragment.top() >= clip.y + clip.height)) {
                    continue;
                }
                Graphics2D fragmentGraphics = g2d;
                if (fragment.clip() != null) {
                    fragmentGraphics = (Graphics2D) g2d.create();
                    ClipRect fragmentClip = fragment.clip();
                    fragmentGraphics.clip(new Rectangle2D.Float(fragmentClip.x(), fragmentClip.y(),
                            fragmentClip.width(), fragmentClip.height()));
                }
                try {
                    if (fragment instanceof BoxFragment box) {
                        paintBox(fragmentGraphics, box);
                    } else if (fragment instanceof InlineBoxFragment inlineBox) {
                        paintInlineBox(fragmentGraphics, inlineBox);
                    } else if (fragment instanceof TextFragment text) {
                        fragmentGraphics.setFont(text.font());
                        fragmentGraphics.setColor(toAwtColor(text.color()));
                        fragmentGraphics.drawString(text.text(), text.x(), text.baseline());
                    } else if (fragment instanceof ImageFragment image) {
                        paintImage(fragmentGraphics, image);
                    }
                } finally {
                    if (fragmentGraphics != g2d) {
                        fragmentGraphics.dispose();
                    }
                }
            }
        } finally {
            g2d.dispose();
        }
    }

    private boolean ensureLayout(int width, Graphics2D graphics) {
        int viewportHeight = currentViewportHeight();
        if (renderViewportWidth != width || renderViewportHeight != viewportHeight) {
            rebuildRenderTree(width, viewportHeight);
        }
        if (layoutWidth == width && layoutResult != null) {
            return false;
        }
        float oldHeight = layoutResult == null ? -1 : layoutResult.height();
        layoutResult = layoutEngine.layout(renderTree, width, getInsets(), graphics);
        layoutWidth = width;
        return oldHeight != layoutResult.height();
    }

    private void paintBox(Graphics2D graphics, BoxFragment fragment) {
        paintStyledBox(graphics, fragment.box().style(),
                fragment.x(), fragment.y(), fragment.width(), fragment.height(), true, true);
    }

    private void paintInlineBox(Graphics2D graphics, InlineBoxFragment fragment) {
        paintStyledBox(graphics, fragment.box().style(),
                fragment.x(), fragment.y(), fragment.width(), fragment.height(),
                fragment.firstFragment(), fragment.lastFragment());
    }

    private static void paintImage(Graphics2D graphics, ImageFragment fragment) {
        if (fragment.bitmap() != null) {
            graphics.drawImage(fragment.bitmap(),
                    Math.round(fragment.x()), Math.round(fragment.y()),
                    Math.max(0, Math.round(fragment.width())),
                    Math.max(0, Math.round(fragment.height())), null);
        } else {
            graphics.setColor(new Color(0x9e, 0x9e, 0x9e));
            graphics.draw(new Rectangle2D.Float(fragment.x(), fragment.y(),
                    Math.max(0, fragment.width() - 1),
                    Math.max(0, fragment.height() - 1)));
        }
    }

    private void paintStyledBox(Graphics2D graphics,
                                       RenderStyle style,
                                       float x,
                                       float y,
                                       float width,
                                       float height,
                                       boolean paintLeft,
                                       boolean paintRight) {
        CssColor background = style.backgroundColor();
        if (background != null && !background.isTransparent()) {
            graphics.setColor(toAwtColor(background));
            graphics.fill(new Rectangle2D.Float(x, y, width, height));
        }
        paintBackgroundImage(graphics, style, x, y, width, height);

        BoxEdges widths = style.borderWidth();
        BoxBorders borders = style.borderStyle();
        float right = x + width;
        float bottom = y + height;
        if (borders.top() && widths.top() > 0) {
            fillBorder(graphics, style.borderColor().top(), style.color(),
                    x, y, width, widths.top());
        }
        if (paintRight && borders.right() && widths.right() > 0) {
            fillBorder(graphics, style.borderColor().right(), style.color(),
                    right - widths.right(), y, widths.right(), height);
        }
        if (borders.bottom() && widths.bottom() > 0) {
            fillBorder(graphics, style.borderColor().bottom(), style.color(),
                    x, bottom - widths.bottom(), width, widths.bottom());
        }
        if (paintLeft && borders.left() && widths.left() > 0) {
            fillBorder(graphics, style.borderColor().left(), style.color(),
                    x, y, widths.left(), height);
        }
    }

    private void paintBackgroundImage(Graphics2D graphics,
                                      RenderStyle style,
                                      float x,
                                      float y,
                                      float width,
                                      float height) {
        BufferedImage image = decodedBackground(style.backgroundImageUrl());
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) return;
        float imageX = switch (style.backgroundPositionX()) {
            case LEFT -> x;
            case CENTER -> x + (width - image.getWidth()) / 2f;
            case RIGHT -> x + width - image.getWidth();
        };
        float imageY = switch (style.backgroundPositionY()) {
            case TOP -> y;
            case CENTER -> y + (height - image.getHeight()) / 2f;
            case BOTTOM -> y + height - image.getHeight();
        };
        boolean repeatX = style.backgroundRepeat() == RenderStyle.BackgroundRepeat.REPEAT
                || style.backgroundRepeat() == RenderStyle.BackgroundRepeat.REPEAT_X;
        boolean repeatY = style.backgroundRepeat() == RenderStyle.BackgroundRepeat.REPEAT
                || style.backgroundRepeat() == RenderStyle.BackgroundRepeat.REPEAT_Y;
        float startX = repeatX ? tileStart(imageX, x, image.getWidth()) : imageX;
        float startY = repeatY ? tileStart(imageY, y, image.getHeight()) : imageY;
        float endX = repeatX ? x + width : imageX + 1;
        float endY = repeatY ? y + height : imageY + 1;
        Graphics2D clipped = (Graphics2D) graphics.create();
        try {
            clipped.clip(new Rectangle2D.Float(x, y, width, height));
            for (float tileY = startY; tileY < endY; tileY += image.getHeight()) {
                for (float tileX = startX; tileX < endX; tileX += image.getWidth()) {
                    clipped.drawImage(image, Math.round(tileX), Math.round(tileY), null);
                }
            }
        } finally {
            clipped.dispose();
        }
    }

    private static float tileStart(float origin, float edge, int tileSize) {
        while (origin > edge) origin -= tileSize;
        while (origin + tileSize <= edge) origin += tileSize;
        return origin;
    }

    private BufferedImage decodedBackground(String source) {
        if (source == null || source.isBlank()) return null;
        URI uri;
        try {
            uri = URI.create(document.getUrl()).resolve(source);
        } catch (IllegalArgumentException invalidUri) {
            return null;
        }
        return backgroundImages.computeIfAbsent(uri, key -> images.find(key).map(resource -> {
            try {
                return ImageIO.read(new ByteArrayInputStream(resource.content()));
            } catch (IOException | RuntimeException invalidImage) {
                return null;
            }
        })).orElse(null);
    }

    private static void fillBorder(Graphics2D graphics,
                                   CssColor borderColor,
                                   CssColor currentColor,
                                   float x,
                                   float y,
                                   float width,
                                   float height) {
        graphics.setColor(toAwtColor(borderColor == null ? currentColor : borderColor));
        graphics.fill(new Rectangle2D.Float(x, y, width, height));
    }

    private void invalidateReflow() {
        layoutWidth = -1;
        layoutResult = null;
    }

    private int currentLayoutWidth() {
        if (getWidth() > 0) {
            return getWidth();
        }
        if (getParent() != null && getParent().getWidth() > 0) {
            return getParent().getWidth();
        }
        return DEFAULT_LAYOUT_WIDTH;
    }

    private int currentViewportHeight() {
        for (Container ancestor = getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor instanceof JViewport viewport && viewport.getExtentSize().height > 0) {
                return viewport.getExtentSize().height;
            }
        }
        if (getHeight() > 0) {
            return getHeight();
        }
        if (getParent() != null && getParent().getHeight() > 0) {
            return getParent().getHeight();
        }
        return DEFAULT_VIEWPORT_HEIGHT;
    }

    @Override
    public Dimension getPreferredSize() {
        int width = currentLayoutWidth();
        if (layoutWidth != width || layoutResult == null) {
            BufferedImage metricsImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D metricsGraphics = metricsImage.createGraphics();
            try {
                configureGraphics(metricsGraphics);
                ensureLayout(width, metricsGraphics);
            } finally {
                metricsGraphics.dispose();
            }
        }
        return new Dimension(width, Math.max(1, (int) Math.ceil(layoutResult.height())));
    }

    RenderTree renderTreeForTesting() {
        return renderTree;
    }

    LayoutResult layoutForTesting(int width) {
        return layoutForTesting(width, DEFAULT_VIEWPORT_HEIGHT);
    }

    LayoutResult layoutForTesting(int width, int viewportHeight) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            configureGraphics(graphics);
            RenderTree testingTree = new RenderTreeBuilder(element -> images.find(element)
                    .map(com.browicy.engine.net.BinaryResource::content)
                    .orElse(null)).build(document, width, viewportHeight);
            return layoutEngine.layout(testingTree, width, getInsets(), graphics);
        } finally {
            graphics.dispose();
        }
    }

    private static void configureGraphics(Graphics2D graphics) {
        graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private static Color toAwtColor(CssColor color) {
        return new Color(color.red(), color.green(), color.blue(), color.alpha());
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return SCROLL_UNIT;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return visibleRect.height;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}

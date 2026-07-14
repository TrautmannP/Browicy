package com.browicy.ui;

import com.browicy.engine.InvalidationType;
import com.browicy.engine.ImageResourceRegistry;
import com.browicy.engine.FontResourceRegistry;
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
import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.parser.resources.ResourcePolicy;
import com.github.weisj.jsvg.view.ViewBox;
import java.awt.Color;
import java.awt.BasicStroke;
import java.awt.AlphaComposite;
import java.awt.Container;
import java.awt.Cursor;
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
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.JViewport;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public final class DomViewPanel extends JPanel implements Scrollable {

    private static final int CONTENT_PADDING = 16;
    private static final int DEFAULT_LAYOUT_WIDTH = 800;
    private static final int DEFAULT_VIEWPORT_HEIGHT = 600;
    private static final int SCROLL_UNIT = 24;
    private static final int MAX_BACKGROUND_IMAGE_DIMENSION = 8192;
    private static final long MAX_BACKGROUND_IMAGE_PIXELS = 32_000_000L;
    private static final int MAX_SCREENSHOT_DIMENSION = 32_767;
    private static final long MAX_SCREENSHOT_PIXELS = 100_000_000L;

    private static final System.Logger LOGGER = System.getLogger(DomViewPanel.class.getName());

    private final Document document;
    private final PageRuntime runtime;
    private final ImageResourceRegistry images;
    private final StyleSheetRegistry styleSheets;
    private final FontResourceRegistry fonts;
    private final Map<URI, Optional<BufferedImage>> backgroundImages = new ConcurrentHashMap<>();
    private final Map<String, Optional<SVGDocument>> svgDocuments = new ConcurrentHashMap<>();
    private Element pressedTarget;
    private List<Element> hoveredElements = List.of();
    private final RenderLayoutEngine layoutEngine;

    private final java.util.concurrent.ExecutorService renderExecutor;
    private final Object renderLock = new Object();
    private InvalidationType pendingInvalidation;
    private boolean renderPassRunning;
    private boolean disposed;
    private volatile RenderSnapshot snapshot;
    private volatile Boolean hoverStylesPresent;
    private volatile Boolean focusStylesPresent;
    private volatile Boolean activeStylesPresent;

    private record RenderSnapshot(RenderTree tree, LayoutResult layout,
                                  int viewportWidth, int viewportHeight) {
    }

    public DomViewPanel(Document document) {
        this(document, PageRuntime.closed(), new ImageResourceRegistry(),
                new FontResourceRegistry(), null);
    }

    public DomViewPanel(PageSession session) {
        this(session.document(), session.runtime(), session.images(), session.fonts(),
                session.styleSheets());
    }

    DomViewPanel(Document document, PageRuntime runtime, ImageResourceRegistry images) {
        this(document, runtime, images, new FontResourceRegistry(), null);
    }

    private DomViewPanel(Document document,
                         PageRuntime runtime,
                         ImageResourceRegistry images,
                         FontResourceRegistry fonts,
                         StyleSheetRegistry styleSheets) {
        this.document = document;
        this.runtime = runtime;
        this.images = images;
        this.fonts = fonts;
        this.layoutEngine = new RenderLayoutEngine(fonts::resolve);
        this.styleSheets = styleSheets;
        this.renderExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "browicy-render");
            thread.setDaemon(true);
            return thread;
        });
        setLayout(null);
        setOpaque(true);
        setFocusable(true);
        setBackground(UiTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(
                CONTENT_PADDING, CONTENT_PADDING, CONTENT_PADDING, CONTENT_PADDING));
        requestRender(InvalidationType.STYLE);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                RenderSnapshot current = snapshot;
                if (current == null || current.viewportWidth() != getWidth()
                        || current.viewportHeight() != currentViewportHeight()) {
                    requestRender(InvalidationType.LAYOUT);
                }
            }
        });

        MouseAdapter mouseEvents = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                pressedTarget = hitTest(event.getX(), event.getY());
                updateFocus(isFocusable(pressedTarget) ? pressedTarget : null);
                document.setActiveElement(pressedTarget);
                if (activeStylesPresent()) requestRender(InvalidationType.STYLE);
                dispatchDomEvent(pressedTarget, "mousedown", true, true);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                Element releasedTarget = hitTest(event.getX(), event.getY());
                dispatchDomEvent(releasedTarget, "mouseup", true, true);
                if (releasedTarget != null && releasedTarget == pressedTarget) {
                    dispatchDomEvent(releasedTarget, "click", true, true);
                }
                document.setActiveElement(null);
                if (activeStylesPresent()) requestRender(InvalidationType.STYLE);
                pressedTarget = null;
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                Element target = hitTest(event.getX(), event.getY());
                updateHover(target);
                updateCursor(target);
                dispatchDomEvent(target, "mousemove", true, false);
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                mouseMoved(event);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                updateHover(null);
                setCursor(Cursor.getDefaultCursor());
            }
        };
        addMouseListener(mouseEvents);
        addMouseMotionListener(mouseEvents);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                dispatchDomEvent(focusedOrBody(), "keydown", true, true);
            }

            @Override
            public void keyReleased(KeyEvent event) {
                dispatchDomEvent(focusedOrBody(), "keyup", true, true);
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
        if (invalidation.requires(InvalidationType.STYLE)
                || invalidation.requires(InvalidationType.RENDER_TREE)) {
            hoverStylesPresent = null;
            focusStylesPresent = null;
            activeStylesPresent = null;
        }
        if (invalidation.requires(InvalidationType.LAYOUT)) {
            requestRender(invalidation);
        } else {
            repaint();
        }
    }

    private void requestRender(InvalidationType invalidation) {
        if (!SwingUtilities.isEventDispatchThread()) {
            synchronized (renderLock) {
                if (disposed) {
                    return;
                }
            }
            renderPass(invalidation);
            return;
        }
        synchronized (renderLock) {
            if (disposed) {
                return;
            }
            pendingInvalidation = pendingInvalidation == null
                    ? invalidation : pendingInvalidation.merge(invalidation);
            if (renderPassRunning) {
                return;
            }
            renderPassRunning = true;
        }
        try {
            renderExecutor.execute(this::drainRenderRequests);
        } catch (java.util.concurrent.RejectedExecutionException alreadyDisposed) {
            synchronized (renderLock) {
                renderPassRunning = false;
            }
        }
    }

    private void drainRenderRequests() {
        while (true) {
            InvalidationType invalidation;
            synchronized (renderLock) {
                invalidation = pendingInvalidation;
                pendingInvalidation = null;
                if (invalidation == null || disposed) {
                    renderPassRunning = false;
                    return;
                }
            }
            try {
                renderPass(invalidation);
            } catch (RuntimeException failure) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Render-Durchlauf fehlgeschlagen", failure);
            }
        }
    }

    private synchronized void renderPass(InvalidationType invalidation) {
        RenderSnapshot current = snapshot;
        int width = Math.max(1, currentLayoutWidth());
        int viewportHeight = Math.max(1, currentViewportHeight());
        boolean sizeChanged = current == null
                || current.viewportWidth() != width
                || current.viewportHeight() != viewportHeight;
        if (!sizeChanged && !invalidation.requires(InvalidationType.LAYOUT)) {
            repaint();
            return;
        }

        RenderTree tree;
        if (sizeChanged || invalidation.requires(InvalidationType.RENDER_TREE)) {
            synchronized (document) {
                StyleApplicator applicator = new StyleApplicator();
                if (styleSheets == null) applicator.apply(document, width, viewportHeight);
                else applicator.apply(document, styleSheets, width, viewportHeight);
                tree = new RenderTreeBuilder(element -> images.find(element)
                        .map(com.browicy.engine.net.BinaryResource::content)
                        .orElse(null)).build(document, width, viewportHeight);
            }
        } else {
            tree = current.tree();
        }

        LayoutResult layout;
        BufferedImage metricsImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = metricsImage.createGraphics();
        try {
            configureGraphics(graphics);
            layout = layoutEngine.layout(tree, width, getInsets(), graphics);
        } finally {
            graphics.dispose();
        }

        boolean heightChanged = current == null || current.layout().height() != layout.height();
        snapshot = new RenderSnapshot(tree, layout, width, viewportHeight);
        if (heightChanged) {
            revalidate();
        }
        repaint();
    }

    private RenderSnapshot currentSnapshot() {
        RenderSnapshot current = snapshot;
        int width = Math.max(1, currentLayoutWidth());
        if (current == null || current.viewportWidth() != width) {
            if (SwingUtilities.isEventDispatchThread()) {
                requestRender(InvalidationType.LAYOUT);
            } else {
                renderPass(InvalidationType.STYLE);
                current = snapshot;
            }
        }
        return current;
    }

    public void dispose() {
        synchronized (renderLock) {
            disposed = true;
            pendingInvalidation = null;
        }
        renderExecutor.shutdownNow();
    }

    private Element hitTest(int x, int y) {
        RenderSnapshot current = currentSnapshot();
        if (current == null) {
            return document.getBody();
        }
        List<PaintFragment> fragments = current.layout().fragments();
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
        if (hoverStylesPresent()) {
            requestRender(InvalidationType.STYLE);
        }
        if (previousTarget != target) {
            dispatchDomEvent(previousTarget, "mouseout", true, false);
            dispatchDomEvent(target, "mouseover", true, false);
        }
    }

    private boolean hoverStylesPresent() {
        Boolean cached = hoverStylesPresent;
        if (cached == null) {
            cached = computeHoverStylesPresent();
            hoverStylesPresent = cached;
        }
        return cached;
    }

    private boolean computeHoverStylesPresent() {
        if (styleSheets == null) {
            return true;
        }
        for (com.browicy.engine.css.CssRule rule : styleSheets.rules()) {
            if (rule.selector().toString().contains(":hover")) {
                return true;
            }
        }
        return false;
    }

    private void updateFocus(Element target) {
        Element previous = document.getFocusedElement();
        if (previous == target) return;
        document.setFocusedElement(target);
        dispatchDomEvent(previous, "blur", false, false);
        dispatchDomEvent(target, "focus", false, false);
        if (focusStylesPresent()) requestRender(InvalidationType.STYLE);
    }

    private Element focusedOrBody() {
        Element focused = document.getFocusedElement();
        return focused == null ? document.getBody() : focused;
    }

    private static boolean isFocusable(Element element) {
        if (element == null || element.hasAttribute("disabled")) return false;
        if (element.hasAttribute("tabindex")) return true;
        return switch (element.getTagName()) {
            case "input", "button", "select", "textarea" -> true;
            case "a" -> element.hasAttribute("href");
            default -> false;
        };
    }

    private boolean focusStylesPresent() {
        Boolean cached = focusStylesPresent;
        if (cached == null) {
            cached = stateStylesPresent(":focus");
            focusStylesPresent = cached;
        }
        return cached;
    }

    private boolean activeStylesPresent() {
        Boolean cached = activeStylesPresent;
        if (cached == null) {
            cached = stateStylesPresent(":active");
            activeStylesPresent = cached;
        }
        return cached;
    }

    private boolean stateStylesPresent(String pseudoClass) {
        if (styleSheets == null) return true;
        for (com.browicy.engine.css.CssRule rule : styleSheets.rules()) {
            if (rule.selector().toString().contains(pseudoClass)) return true;
        }
        return false;
    }

    private void updateCursor(Element target) {
        String cursor = null;
        for (com.browicy.engine.dom.Node node = target;
             node instanceof Element element;
             node = node.getParent()) {
            cursor = element.getComputedStyles().get("cursor");
            if (cursor != null) break;
        }
        int type = switch (cursor == null ? "default" : cursor) {
            case "pointer" -> Cursor.HAND_CURSOR;
            case "text" -> Cursor.TEXT_CURSOR;
            default -> Cursor.DEFAULT_CURSOR;
        };
        setCursor(Cursor.getPredefinedCursor(type));
    }

    public synchronized BufferedImage captureScreenshot(int viewportWidth,
                                                        int viewportHeight,
                                                        boolean fullPage) {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            throw new IllegalArgumentException("Viewport-Abmessungen müssen positiv sein");
        }
        setSize(viewportWidth, viewportHeight);
        renderPass(InvalidationType.STYLE);
        RenderSnapshot current = snapshot;
        if (current == null) {
            throw new IllegalStateException("Die Webseite konnte nicht gerendert werden");
        }
        int imageHeight = fullPage
                ? Math.max(viewportHeight, (int) Math.ceil(current.layout().height()))
                : viewportHeight;
        if (viewportWidth > MAX_SCREENSHOT_DIMENSION
                || imageHeight > MAX_SCREENSHOT_DIMENSION
                || (long) viewportWidth * imageHeight > MAX_SCREENSHOT_PIXELS) {
            throw new IllegalArgumentException(
                    "Screenshot ist größer als 32767 Pixel bzw. 100 Megapixel");
        }
        BufferedImage image = new BufferedImage(
                viewportWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(getBackground());
            graphics.fillRect(0, 0, viewportWidth, imageHeight);
            paintComponent(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        RenderSnapshot current = currentSnapshot();
        if (current == null) {
            return;
        }
        Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            configureGraphics(g2d);
            Rectangle clip = g2d.getClipBounds();
            for (PaintFragment fragment : current.layout().fragments()) {
                if (clip != null && (fragment.bottom() <= clip.y
                        || fragment.top() >= clip.y + clip.height)) {
                    continue;
                }
                Graphics2D fragmentGraphics = (Graphics2D) g2d.create();
                if (fragment.clip() != null) {
                    ClipRect fragmentClip = fragment.clip();
                    fragmentGraphics.clip(new Rectangle2D.Float(fragmentClip.x(), fragmentClip.y(),
                            fragmentClip.width(), fragmentClip.height()));
                }
                try {
                    float opacity = fragmentOpacity(fragment);
                    if (opacity < 1) {
                        fragmentGraphics.setComposite(
                                AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
                    }
                    if (fragment instanceof BoxFragment box) {
                        paintBox(fragmentGraphics, box);
                    } else if (fragment instanceof InlineBoxFragment inlineBox) {
                        paintInlineBox(fragmentGraphics, inlineBox);
                    } else if (fragment instanceof TextFragment text) {
                        fragmentGraphics.setFont(text.font());
                        fragmentGraphics.setColor(toAwtColor(text.color()));
                        fragmentGraphics.drawString(text.text(), text.x(), text.baseline());
                        if (text.underline()) {
                            fragmentGraphics.setColor(toAwtColor(text.decorationColor()));
                            float underlineY = text.baseline() + Math.max(1f, text.font().getSize2D() / 12f);
                            fragmentGraphics.draw(new java.awt.geom.Line2D.Float(
                                    text.x(), underlineY, text.x() + text.width(), underlineY));
                        }
                    } else if (fragment instanceof ImageFragment image) {
                        paintImage(fragmentGraphics, image);
                    }
                } finally {
                    fragmentGraphics.dispose();
                }
            }
        } finally {
            g2d.dispose();
        }
    }

    private static float fragmentOpacity(PaintFragment fragment) {
        if (fragment instanceof BoxFragment box) return box.box().style().opacity();
        if (fragment instanceof InlineBoxFragment box) return box.box().style().opacity();
        if (fragment instanceof ImageFragment image) return image.image().style().opacity();
        return ((TextFragment) fragment).opacity();
    }

    private void paintBox(Graphics2D graphics, BoxFragment fragment) {
        paintStyledBox(graphics, fragment.box().style(),
                fragment.x(), fragment.y(), fragment.width(), fragment.height(), true, true);
        paintListMarker(graphics, fragment);
    }

    private static void paintListMarker(Graphics2D graphics, BoxFragment fragment) {
        if (!"li".equals(fragment.box().tagName())) return;
        RenderStyle style = fragment.box().style();
        if (style.listStyleType() == RenderStyle.ListStyleType.NONE) return;
        float size = Math.max(4, style.fontSizePx() / 3f);
        float markerX = fragment.x() - size - 6;
        float markerY = fragment.y() + Math.max(2, (style.usedLineHeightPx() - size) / 2f);
        graphics.setColor(toAwtColor(style.color()));
        var marker = style.listStyleType() == RenderStyle.ListStyleType.SQUARE
                ? new Rectangle2D.Float(markerX, markerY, size, size)
                : new java.awt.geom.Ellipse2D.Float(markerX, markerY, size, size);
        if (style.listStyleType() == RenderStyle.ListStyleType.CIRCLE) graphics.draw(marker);
        else graphics.fill(marker);
    }

    private void paintInlineBox(Graphics2D graphics, InlineBoxFragment fragment) {
        paintStyledBox(graphics, fragment.box().style(),
                fragment.x(), fragment.y(), fragment.width(), fragment.height(),
                fragment.firstFragment(), fragment.lastFragment());
    }

    private void paintImage(Graphics2D graphics, ImageFragment fragment) {
        if (fragment.bitmap() != null) {
            graphics.drawImage(fragment.bitmap(),
                    Math.round(fragment.x()), Math.round(fragment.y()),
                    Math.max(0, Math.round(fragment.width())),
                    Math.max(0, Math.round(fragment.height())), null);
        } else if (fragment.image().svg() != null) {
            SVGDocument svg = svgDocument(fragment.image().svg().source());
            if (svg != null) {
                svg.render(this, graphics, new ViewBox(
                        fragment.x(), fragment.y(), fragment.width(), fragment.height()));
            }
        } else {
            graphics.setColor(new Color(0x9e, 0x9e, 0x9e));
            graphics.draw(new Rectangle2D.Float(fragment.x(), fragment.y(),
                    Math.max(0, fragment.width() - 1),
                    Math.max(0, fragment.height() - 1)));
        }
    }

    private SVGDocument svgDocument(String source) {
        return svgDocuments.computeIfAbsent(source, ignored -> {
            LoaderContext context = LoaderContext.builder()
                    .externalResourcePolicy(ResourcePolicy.DENY_ALL)
                    .build();
            try (ByteArrayInputStream input = new ByteArrayInputStream(
                    source.getBytes(StandardCharsets.UTF_8))) {
                return Optional.ofNullable(new SVGLoader().load(
                        input, URI.create("about:blank"), context));
            } catch (IOException | RuntimeException invalidSvg) {
                return Optional.empty();
            }
        }).orElse(null);
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
        float radius = Math.min(style.borderRadius(), Math.min(width, height) / 2f);
        var boxShape = radius > 0
                ? new RoundRectangle2D.Float(x, y, width, height, radius * 2, radius * 2)
                : new Rectangle2D.Float(x, y, width, height);
        if (background != null && !background.isTransparent()) {
            graphics.setColor(toAwtColor(background));
            graphics.fill(boxShape);
        }
        var oldClip = graphics.getClip();
        if (radius > 0) graphics.clip(boxShape);
        paintBackgroundImage(graphics, style, x, y, width, height);
        graphics.setClip(oldClip);

        BoxEdges widths = style.borderWidth();
        BoxBorders borders = style.borderStyle();
        if (radius > 0 && (borders.top() || borders.right() || borders.bottom() || borders.left())) {
            float strokeWidth = Math.max(Math.max(widths.top(), widths.right()),
                    Math.max(widths.bottom(), widths.left()));
            if (strokeWidth > 0) {
                graphics.setColor(toAwtColor(style.borderColor().top()));
                graphics.setStroke(new BasicStroke(strokeWidth));
                float inset = strokeWidth / 2f;
                graphics.draw(new RoundRectangle2D.Float(x + inset, y + inset,
                        Math.max(0, width - strokeWidth), Math.max(0, height - strokeWidth),
                        Math.max(0, radius * 2 - strokeWidth),
                        Math.max(0, radius * 2 - strokeWidth)));
                graphics.setStroke(new BasicStroke());
            }
        } else {
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
        if (style.outlineVisible()) {
            float outline = style.outlineWidth();
            graphics.setColor(toAwtColor(style.outlineColor()));
            graphics.setStroke(new BasicStroke(outline));
            float inset = outline / 2f;
            graphics.draw(new RoundRectangle2D.Float(x - inset, y - inset,
                    width + outline, height + outline,
                    Math.max(0, radius * 2 + outline), Math.max(0, radius * 2 + outline)));
            graphics.setStroke(new BasicStroke());
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
            try (ImageInputStream input = ImageIO.createImageInputStream(
                    new ByteArrayInputStream(resource.content()))) {
                if (input == null) return null;
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) return null;
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width <= 0 || height <= 0
                            || width > MAX_BACKGROUND_IMAGE_DIMENSION
                            || height > MAX_BACKGROUND_IMAGE_DIMENSION
                            || (long) width * height > MAX_BACKGROUND_IMAGE_PIXELS) {
                        return null;
                    }
                    return reader.read(0);
                } finally {
                    reader.dispose();
                }
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
        RenderSnapshot current = currentSnapshot();
        if (current == null) {
            return new Dimension(width, DEFAULT_VIEWPORT_HEIGHT);
        }
        return new Dimension(width, Math.max(1, (int) Math.ceil(current.layout().height())));
    }

    RenderTree renderTreeForTesting() {
        RenderSnapshot current = snapshot;
        return current == null ? null : current.tree();
    }

    synchronized LayoutResult layoutForTesting(int width) {
        return layoutForTesting(width, DEFAULT_VIEWPORT_HEIGHT);
    }

    synchronized LayoutResult layoutForTesting(int width, int viewportHeight) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            configureGraphics(graphics);
            StyleApplicator applicator = new StyleApplicator();
            if (styleSheets == null) applicator.apply(document, width, viewportHeight);
            else applicator.apply(document, styleSheets, width, viewportHeight);
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

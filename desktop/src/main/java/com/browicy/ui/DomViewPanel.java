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
import com.browicy.engine.render.BoxShadow;
import com.browicy.engine.render.CornerRadii;
import com.browicy.engine.render.CssColor;
import com.browicy.engine.render.RenderLength;
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
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
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
import java.util.Locale;
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
    private final Object snapshotLock = new Object();
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
            synchronized (snapshotLock) {
                if (disposed) {
                    return;
                }
            }
            renderPass(invalidation);
            return;
        }
        synchronized (snapshotLock) {
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
            synchronized (snapshotLock) {
                renderPassRunning = false;
            }
        }
    }

    private void drainRenderRequests() {
        while (true) {
            InvalidationType invalidation;
            synchronized (snapshotLock) {
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

    private void renderPass(InvalidationType invalidation) {
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

        boolean heightChanged;
        synchronized (snapshotLock) {
            heightChanged = snapshot == null || snapshot.layout().height() != layout.height();
            snapshot = new RenderSnapshot(tree, layout, width, viewportHeight);
        }
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
        synchronized (snapshotLock) {
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
                if (source instanceof Element element
                        && !element.getComputedStyles().getOrDefault(
                                "pointer-events", "auto").equals("none")) {
                    return element;
                }
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
            case "text", "vertical-text" -> Cursor.TEXT_CURSOR;
            case "crosshair" -> Cursor.CROSSHAIR_CURSOR;
            case "move", "all-scroll" -> Cursor.MOVE_CURSOR;
            case "wait", "progress" -> Cursor.WAIT_CURSOR;
            case "n-resize", "s-resize", "ns-resize", "row-resize"
                    -> Cursor.N_RESIZE_CURSOR;
            case "e-resize", "w-resize", "ew-resize", "col-resize"
                    -> Cursor.E_RESIZE_CURSOR;
            case "ne-resize", "sw-resize" -> Cursor.NE_RESIZE_CURSOR;
            case "nw-resize", "se-resize" -> Cursor.NW_RESIZE_CURSOR;
            default -> Cursor.DEFAULT_CURSOR;
        };
        setCursor(Cursor.getPredefinedCursor(type));
    }

    public BufferedImage captureScreenshot(int viewportWidth,
                                           int viewportHeight,
                                           boolean fullPage) {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            throw new IllegalArgumentException("Viewport-Abmessungen müssen positiv sein");
        }
        setSize(viewportWidth, viewportHeight);
        renderPass(InvalidationType.STYLE);
        RenderSnapshot current;
        synchronized (snapshotLock) {
            current = snapshot;
        }
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
                if (fragment.transform() != null) {
                    fragmentGraphics.transform(fragment.transform());
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
                        if (text.visible()) {
                            fragmentGraphics.setFont(text.font());
                            fragmentGraphics.setColor(toAwtColor(text.color()));
                            paintText(fragmentGraphics, text);
                            if (text.underline()) {
                                fragmentGraphics.setColor(toAwtColor(text.decorationColor()));
                                float underlineY = text.baseline() + Math.max(1f, text.font().getSize2D() / 12f);
                                fragmentGraphics.draw(new java.awt.geom.Line2D.Float(
                                        text.x(), underlineY, text.x() + text.width(), underlineY));
                            }
                            if (text.lineThrough()) {
                                fragmentGraphics.setColor(toAwtColor(text.decorationColor()));
                                float strikeY = text.baseline() - text.font().getSize2D() * 0.3f;
                                fragmentGraphics.draw(new java.awt.geom.Line2D.Float(
                                        text.x(), strikeY, text.x() + text.width(), strikeY));
                            }
                        }
                    } else if (fragment instanceof ImageFragment image) {
                        if (image.image().style().visible()) {
                            paintImage(fragmentGraphics, image);
                        }
                    }
                } finally {
                    fragmentGraphics.dispose();
                }
            }
        } finally {
            g2d.dispose();
        }
    }

    private static void paintText(Graphics2D graphics, TextFragment text) {
        String content = text.text();
        float x = text.x();
        if (text.ellipsis() && text.clip() != null
                && x + text.width() > text.clip().x() + text.clip().width()) {
            float available = Math.max(0, text.clip().x() + text.clip().width() - x);
            FontMetrics metrics = graphics.getFontMetrics(text.font());
            String ellipsis = "\u2026";
            float ellipsisWidth = metrics.stringWidth(ellipsis);
            String visible = "";
            for (int index = 0; index < content.length(); index++) {
                String candidate = visible + content.charAt(index);
                if (metrics.stringWidth(candidate) + ellipsisWidth > available) {
                    break;
                }
                visible = candidate;
            }
            content = visible + ellipsis;
        }
        if (text.shadow() != null) {
            RenderStyle.TextShadow shadow = text.shadow();
            graphics.setColor(toAwtColor(shadow.color()));
            graphics.drawString(content, x + shadow.offsetX(), text.baseline() + shadow.offsetY());
            graphics.setColor(toAwtColor(text.color()));
        }
        if (text.letterSpacingPx() == 0 || content.length() <= 1) {
            graphics.drawString(content, x, text.baseline());
            return;
        }
        float spacing = text.letterSpacingPx();
        FontMetrics metrics = graphics.getFontMetrics(text.font());
        float cursor = x;
        for (int index = 0; index < content.length(); index++) {
            String character = content.substring(index, index + 1);
            graphics.drawString(character, cursor, text.baseline());
            cursor += metrics.stringWidth(character) + spacing;
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
            paintBitmap(graphics, fragment);
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

    private static void paintBitmap(Graphics2D graphics, ImageFragment fragment) {
        BufferedImage bitmap = fragment.bitmap();
        int destinationX = Math.round(fragment.x());
        int destinationY = Math.round(fragment.y());
        int destinationWidth = Math.max(0, Math.round(fragment.width()));
        int destinationHeight = Math.max(0, Math.round(fragment.height()));
        RenderStyle.ObjectFit fit = fragment.image().style().objectFit();
        if (fit == RenderStyle.ObjectFit.FILL || destinationWidth == 0 || destinationHeight == 0) {
            graphics.drawImage(bitmap, destinationX, destinationY,
                    destinationWidth, destinationHeight, null);
            return;
        }

        double scaleX = destinationWidth / (double) bitmap.getWidth();
        double scaleY = destinationHeight / (double) bitmap.getHeight();
        double scale = switch (fit) {
            case COVER -> Math.max(scaleX, scaleY);
            case CONTAIN -> Math.min(scaleX, scaleY);
            case NONE -> 1;
            case SCALE_DOWN -> Math.min(1, Math.min(scaleX, scaleY));
            default -> throw new IllegalStateException("Unexpected object-fit: " + fit);
        };
        int paintedWidth = Math.max(1, (int) Math.round(bitmap.getWidth() * scale));
        int paintedHeight = Math.max(1, (int) Math.round(bitmap.getHeight() * scale));
        int paintedX = destinationX + (destinationWidth - paintedWidth) / 2;
        int paintedY = destinationY + (destinationHeight - paintedHeight) / 2;
        Graphics2D clipped = (Graphics2D) graphics.create();
        try {
            clipped.clip(new Rectangle2D.Float(destinationX, destinationY,
                    destinationWidth, destinationHeight));
            clipped.drawImage(bitmap, paintedX, paintedY, paintedWidth, paintedHeight, null);
        } finally {
            clipped.dispose();
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
        if (!style.visible()) {
            return;
        }
        CssColor background = style.backgroundColor();
        CornerRadii radii = style.borderRadius();
        boolean rounded = radii.topLeft() > 0 || radii.topRight() > 0
                || radii.bottomRight() > 0 || radii.bottomLeft() > 0;
        var boxShape = boxPath(x, y, width, height, radii);
        for (BoxShadow shadow : style.boxShadows()) {
            if (!shadow.inset()) {
                paintOuterShadow(graphics, shadow, x, y, width, height, radii);
            }
        }
        if (background != null && !background.isTransparent()) {
            graphics.setColor(toAwtColor(background));
            graphics.fill(boxShape);
        }
        var oldClip = graphics.getClip();
        if (rounded) graphics.clip(boxShape);
        paintBackgroundImage(graphics, style, x, y, width, height);
        graphics.setClip(oldClip);

        BoxEdges widths = style.borderWidth();
        BoxBorders borders = style.borderStyle();
        if (rounded
                && (borders.top() || borders.right() || borders.bottom() || borders.left())) {
            float strokeWidth = Math.max(Math.max(widths.top(), widths.right()),
                    Math.max(widths.bottom(), widths.left()));
            if (strokeWidth > 0) {
                graphics.setColor(toAwtColor(style.borderColor().top()));
                graphics.setStroke(new BasicStroke(strokeWidth));
                float inset = strokeWidth / 2f;
                float shrink = Math.max(0, strokeWidth / 2f);
                graphics.draw(boxPath(x + inset, y + inset,
                        Math.max(0, width - strokeWidth), Math.max(0, height - strokeWidth),
                        shrunk(radii, shrink)));
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
        for (BoxShadow shadow : style.boxShadows()) {
            if (shadow.inset()) {
                paintInsetShadow(graphics, shadow, x, y, width, height, boxShape);
            }
        }
        if (style.outlineVisible()) {
            float outline = style.outlineWidth();
            float offset = style.outlineOffset();
            graphics.setColor(toAwtColor(style.outlineColor()));
            graphics.setStroke(new BasicStroke(outline));
            float inset = outline / 2f + offset;
            graphics.draw(boxPath(x - inset, y - inset,
                    width + outline + 2 * offset, height + outline + 2 * offset,
                    grown(radii, outline / 2f + offset)));
            graphics.setStroke(new BasicStroke());
        }
    }

    private void paintOuterShadow(Graphics2D graphics, BoxShadow shadow,
                                  float x, float y, float width, float height,
                                  CornerRadii radii) {
        float spread = shadow.spread();
        float shadowX = x + shadow.xOffset() - spread;
        float shadowY = y + shadow.yOffset() - spread;
        Shape shadowShape = boxPath(shadowX, shadowY,
                width + 2 * spread, height + 2 * spread,
                grown(radii, spread));
        Color color = toAwtColor(shadow.color());
        if (shadow.blur() <= 0.5f) {
            graphics.setColor(color);
            graphics.fill(shadowShape);
            return;
        }
        int radius = Math.min(24, Math.max(1, Math.round(shadow.blur())));
        paintBlurred(graphics, shadowShape, color, radius);
    }

    private void paintInsetShadow(Graphics2D graphics, BoxShadow shadow,
                                  float x, float y, float width, float height,
                                  Shape boxShape) {
        Shape oldClip = graphics.getClip();
        graphics.clip(boxShape);
        graphics.setColor(toAwtColor(shadow.color()));
        float stroke = Math.max(2f, Math.max(shadow.blur(), Math.abs(shadow.xOffset())
                + Math.abs(shadow.yOffset())));
        graphics.setStroke(new BasicStroke(stroke));
        graphics.draw(boxPath(x - shadow.xOffset(), y - shadow.yOffset(),
                width + 2 * shadow.xOffset(), height + 2 * shadow.yOffset(),
                CornerRadii.ZERO));
        graphics.setStroke(new BasicStroke());
        graphics.setClip(oldClip);
    }

    private static void paintBlurred(Graphics2D graphics, Shape shape,
                                     Color color, int radius) {
        Rectangle bounds = shape.getBounds();
        int padding = radius * 2 + 4;
        int imageWidth = Math.max(1, bounds.width + 2 * padding);
        int imageHeight = Math.max(1, bounds.height + 2 * padding);
        BufferedImage mask = new BufferedImage(imageWidth, imageHeight,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D maskGraphics = mask.createGraphics();
        maskGraphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 255));
        maskGraphics.translate(-bounds.x + padding, -bounds.y + padding);
        maskGraphics.fill(shape);
        maskGraphics.dispose();

        float[] kernel = gaussianKernel(radius);
        BufferedImage horizontal = new BufferedImage(imageWidth, imageHeight,
                BufferedImage.TYPE_INT_ARGB);
        new java.awt.image.ConvolveOp(new java.awt.image.Kernel(kernel.length, 1, kernel),
                java.awt.image.ConvolveOp.EDGE_NO_OP, null).filter(mask, horizontal);
        BufferedImage blurred = new BufferedImage(imageWidth, imageHeight,
                BufferedImage.TYPE_INT_ARGB);
        new java.awt.image.ConvolveOp(new java.awt.image.Kernel(1, kernel.length, kernel),
                java.awt.image.ConvolveOp.EDGE_NO_OP, null).filter(horizontal, blurred);

        float alpha = color.getAlpha() / 255f;
        if (alpha >= 1f) {
            graphics.drawImage(blurred, bounds.x - padding, bounds.y - padding, null);
        } else {
            graphics.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, alpha));
            graphics.drawImage(blurred, bounds.x - padding, bounds.y - padding, null);
            graphics.setComposite(AlphaComposite.SrcOver);
        }
    }

    private static float[] gaussianKernel(int radius) {
        float[] kernel = new float[radius * 2 + 1];
        float sigma = Math.max(0.5f, radius / 2f);
        float sum = 0;
        for (int index = 0; index < kernel.length; index++) {
            int distance = index - radius;
            kernel[index] = (float) Math.exp(-(distance * distance) / (2 * sigma * sigma));
            sum += kernel[index];
        }
        for (int index = 0; index < kernel.length; index++) {
            kernel[index] /= sum;
        }
        return kernel;
    }

    private static CornerRadii shrunk(CornerRadii radii, float amount) {
        return new CornerRadii(
                Math.max(0, radii.topLeft() - amount),
                Math.max(0, radii.topRight() - amount),
                Math.max(0, radii.bottomRight() - amount),
                Math.max(0, radii.bottomLeft() - amount));
    }

    private static CornerRadii grown(CornerRadii radii, float amount) {
        return new CornerRadii(
                radii.topLeft() + amount,
                radii.topRight() + amount,
                radii.bottomRight() + amount,
                radii.bottomLeft() + amount);
    }

    private static java.awt.Shape boxPath(float x, float y, float width, float height,
                                          CornerRadii radii) {
        if (width <= 0 || height <= 0) {
            return new Rectangle2D.Float(x, y, Math.max(0, width), Math.max(0, height));
        }
        float maxRadius = Math.min(width, height) / 2f;
        float topLeft = Math.min(radii.topLeft(), maxRadius);
        float topRight = Math.min(radii.topRight(), maxRadius);
        float bottomRight = Math.min(radii.bottomRight(), maxRadius);
        float bottomLeft = Math.min(radii.bottomLeft(), maxRadius);
        if (topLeft == topRight && topRight == bottomRight && bottomRight == bottomLeft) {
            return topLeft > 0
                    ? new RoundRectangle2D.Float(x, y, width, height, topLeft * 2, topLeft * 2)
                    : new Rectangle2D.Float(x, y, width, height);
        }
        Path2D.Float path = new Path2D.Float();
        path.moveTo(x + topLeft, y);
        path.lineTo(x + width - topRight, y);
        path.quadTo(x + width, y, x + width, y + topRight);
        path.lineTo(x + width, y + height - bottomRight);
        path.quadTo(x + width, y + height, x + width - bottomRight, y + height);
        path.lineTo(x + bottomLeft, y + height);
        path.quadTo(x, y + height, x, y + height - bottomLeft);
        path.lineTo(x, y + topLeft);
        path.quadTo(x, y, x + topLeft, y);
        path.closePath();
        return path;
    }

    private void paintBackgroundImage(Graphics2D graphics,
                                      RenderStyle style,
                                      float x,
                                      float y,
                                      float width,
                                      float height) {
        String source = style.backgroundImageUrl();
        if (source != null && source.startsWith("linear-gradient(")) {
            paintLinearGradient(graphics, source, x, y, width, height);
            return;
        }
        BufferedImage image = decodedBackground(source);
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) return;
        float imageWidth = image.getWidth();
        float imageHeight = image.getHeight();
        if (!style.backgroundSizeX().isAuto() && !style.backgroundSizeY().isAuto()) {
            imageWidth = resolveBackgroundLength(style.backgroundSizeX(), width);
            imageHeight = resolveBackgroundLength(style.backgroundSizeY(), height);
        } else if (!style.backgroundSizeX().isAuto()) {
            imageWidth = resolveBackgroundLength(style.backgroundSizeX(), width);
            imageHeight *= imageWidth / image.getWidth();
        } else if (!style.backgroundSizeY().isAuto()) {
            imageHeight = resolveBackgroundLength(style.backgroundSizeY(), height);
            imageWidth *= imageHeight / image.getHeight();
        }
        imageWidth = Math.max(1, imageWidth);
        imageHeight = Math.max(1, imageHeight);
        if (!(imageWidth > 0) || !(imageHeight > 0)) return;
        float freeX = width - imageWidth;
        float freeY = height - imageHeight;
        float offsetX = resolveBackgroundLength(style.backgroundPositionOffsetX(), freeX);
        float offsetY = resolveBackgroundLength(style.backgroundPositionOffsetY(), freeY);
        float imageX = switch (style.backgroundPositionX()) {
            case LEFT -> x + offsetX;
            case CENTER -> x + freeX / 2f + offsetX;
            case RIGHT -> x + freeX - offsetX;
        };
        float imageY = switch (style.backgroundPositionY()) {
            case TOP -> y + offsetY;
            case CENTER -> y + freeY / 2f + offsetY;
            case BOTTOM -> y + freeY - offsetY;
        };
        boolean repeatX = style.backgroundRepeat() == RenderStyle.BackgroundRepeat.REPEAT
                || style.backgroundRepeat() == RenderStyle.BackgroundRepeat.REPEAT_X;
        boolean repeatY = style.backgroundRepeat() == RenderStyle.BackgroundRepeat.REPEAT
                || style.backgroundRepeat() == RenderStyle.BackgroundRepeat.REPEAT_Y;
        float startX = repeatX ? tileStart(imageX, x, imageWidth) : imageX;
        float startY = repeatY ? tileStart(imageY, y, imageHeight) : imageY;
        float endX = repeatX ? x + width : imageX + 1;
        float endY = repeatY ? y + height : imageY + 1;
        Graphics2D clipped = (Graphics2D) graphics.create();
        try {
            clipped.clip(new Rectangle2D.Float(x, y, width, height));
            for (float tileY = startY; tileY < endY; tileY += imageHeight) {
                for (float tileX = startX; tileX < endX; tileX += imageWidth) {
                    clipped.drawImage(image, Math.round(tileX), Math.round(tileY),
                            Math.max(1, Math.round(imageWidth)),
                            Math.max(1, Math.round(imageHeight)), null);
                }
            }
        } finally {
            clipped.dispose();
        }
    }

    private void paintLinearGradient(Graphics2D graphics, String source,
                                     float x, float y, float width, float height) {
        String body = source.substring("linear-gradient(".length(),
                source.length() - 1);
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < body.length(); index++) {
            char current = body.charAt(index);
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
            } else if (current == ',' && depth == 0) {
                parts.add(body.substring(start, index).strip());
                start = index + 1;
            }
        }
        parts.add(body.substring(start).strip());

        float angleDeg = 180;
        int firstStop = 0;
        String first = parts.isEmpty() ? "" : parts.get(0).toLowerCase(Locale.ROOT);
        if (first.equals("to top") || first.equals("to bottom") || first.equals("to left")
                || first.equals("to right") || first.equals("to top left")
                || first.equals("to top right") || first.equals("to bottom left")
                || first.equals("to bottom right")) {
            angleDeg = switch (first) {
                case "to top" -> 0;
                case "to right" -> 90;
                case "to bottom" -> 180;
                case "to left" -> 270;
                case "to top right" -> 45;
                case "to top left" -> 315;
                case "to bottom right" -> 135;
                default -> 225;
            };
            firstStop = 1;
        } else if (!parts.isEmpty() && parts.get(0).matches("[-+]?[0-9]*\\.?[0-9]+(deg|turn|rad|grad)")) {
            String angle = parts.get(0);
            if (angle.endsWith("turn")) {
                angleDeg = Float.parseFloat(angle.replace("turn", "")) * 360;
            } else if (angle.endsWith("rad")) {
                angleDeg = (float) Math.toDegrees(Float.parseFloat(angle.replace("rad", "")));
            } else if (angle.endsWith("grad")) {
                angleDeg = Float.parseFloat(angle.replace("grad", "")) * 0.9f;
            } else {
                angleDeg = Float.parseFloat(angle.replace("deg", ""));
            }
            firstStop = 1;
        }

        List<CssColor> colors = new ArrayList<>();
        List<Float> positions = new ArrayList<>();
        int stops = parts.size() - firstStop;
        for (int index = firstStop; index < parts.size(); index++) {
            String[] tokens = parts.get(index).split("\\s+");
            CssColor color = CssColor.parse(tokens[0]);
            if (color == null) {
                return;
            }
            colors.add(color);
            if (tokens.length == 2 && tokens[1].endsWith("%")) {
                positions.add(Float.parseFloat(tokens[1].replace("%", "")) / 100f);
            } else {
                positions.add(null);
            }
        }
        if (colors.size() < 2) {
            if (colors.size() == 1) {
                graphics.setColor(toAwtColor(colors.get(0)));
                graphics.fill(new Rectangle2D.Float(x, y, width, height));
            }
            return;
        }
        for (int index = 0; index < positions.size(); index++) {
            if (positions.get(index) == null) {
                int previous = index;
                while (previous >= 0 && positions.get(previous) == null) previous--;
                int next = index;
                while (next < positions.size() && positions.get(next) == null) next++;
                float before = previous < 0 ? 0 : positions.get(previous);
                float after = next >= positions.size() ? 1 : positions.get(next);
                int span = next - previous - 1;
                float step = (after - before) / (span + 1);
                positions.set(index, before + step * (index - previous));
            }
        }

        float cx = x + width / 2f;
        float cy = y + height / 2f;
        double radians = Math.toRadians(angleDeg);
        float vx = (float) Math.sin(radians);
        float vy = (float) -Math.cos(radians);
        float half = (Math.abs(vx) * width + Math.abs(vy) * height) / 2f;
        if (half <= 0) {
            // Nullgroße Box: nichts zu malen (LinearGradientPaint würde werfen).
            return;
        }
        java.awt.Paint paint;
        try {
            paint = new java.awt.LinearGradientPaint(
                    cx - vx * half, cy - vy * half, cx + vx * half, cy + vy * half,
                    toFloatArray(positions), toAwtColors(colors));
        } catch (IllegalArgumentException degenerate) {
            // Nicht streng monoton steigende Stops (harte Farbwechsel) oder
            // andere degenerierte Verläufe: feste Fläche mit der Endfarbe.
            graphics.setColor(toAwtColor(colors.getLast()));
            graphics.fill(new Rectangle2D.Float(x, y, width, height));
            return;
        }
        Graphics2D gradientGraphics = (Graphics2D) graphics.create();
        try {
            gradientGraphics.clip(new Rectangle2D.Float(x, y, width, height));
            gradientGraphics.setPaint(paint);
            gradientGraphics.fill(new Rectangle2D.Float(x, y, width, height));
        } finally {
            gradientGraphics.dispose();
        }
    }

    private static float[] toFloatArray(List<Float> values) {
        float[] result = new float[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index);
        }
        return result;
    }

    private static java.awt.Color[] toAwtColors(List<CssColor> colors) {
        java.awt.Color[] result = new java.awt.Color[colors.size()];
        for (int index = 0; index < colors.size(); index++) {
            result[index] = toAwtColor(colors.get(index));
        }
        return result;
    }

    private float resolveBackgroundLength(RenderLength length, float percentageBase) {
        if (length.isAuto()
                || length.unit() == RenderLength.Unit.MAX_CONTENT
                || length.unit() == RenderLength.Unit.MIN_CONTENT) {
            return 0;
        }
        return length.resolve(percentageBase, 16,
                Math.max(1, currentLayoutWidth()), Math.max(1, currentViewportHeight()));
    }

    private static float tileStart(float origin, float edge, float tileSize) {
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

    LayoutResult layoutForTesting(int width) {
        return layoutForTesting(width, DEFAULT_VIEWPORT_HEIGHT);
    }

    LayoutResult layoutForTesting(int width, int viewportHeight) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            configureGraphics(graphics);
            // Gleiche Synchronisation wie renderPass: der asynchrone Render-Thread
            // wendet Styles an und baut Bäume unter dem Dokument-Lock.
            synchronized (document) {
                StyleApplicator applicator = new StyleApplicator();
                if (styleSheets == null) applicator.apply(document, width, viewportHeight);
                else applicator.apply(document, styleSheets, width, viewportHeight);
                RenderTree testingTree = new RenderTreeBuilder(element -> images.find(element)
                        .map(com.browicy.engine.net.BinaryResource::content)
                        .orElse(null)).build(document, width, viewportHeight);
                return layoutEngine.layout(testingTree, width, getInsets(), graphics);
            }
        } finally {
            graphics.dispose();
        }
    }

    void renderPassForTesting() {
        renderPass(InvalidationType.STYLE);
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

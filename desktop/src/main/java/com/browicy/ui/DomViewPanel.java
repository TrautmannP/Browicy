package com.browicy.ui;

import com.browicy.engine.dom.Document;
import com.browicy.engine.render.BoxBorders;
import com.browicy.engine.render.BoxEdges;
import com.browicy.engine.render.CssColor;
import com.browicy.engine.render.RenderStyle;
import com.browicy.engine.render.RenderTree;
import com.browicy.engine.render.RenderTreeBuilder;
import com.browicy.ui.render.RenderLayoutEngine;
import com.browicy.ui.render.RenderLayoutEngine.BoxFragment;
import com.browicy.ui.render.RenderLayoutEngine.InlineBoxFragment;
import com.browicy.ui.render.RenderLayoutEngine.LayoutResult;
import com.browicy.ui.render.RenderLayoutEngine.PaintFragment;
import com.browicy.ui.render.RenderLayoutEngine.TextFragment;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.Scrollable;

public final class DomViewPanel extends JPanel implements Scrollable {

    private static final int CONTENT_PADDING = 16;
    private static final int DEFAULT_LAYOUT_WIDTH = 800;
    private static final int SCROLL_UNIT = 24;

    private final Document document;
    private RenderTree renderTree;
    private final RenderLayoutEngine layoutEngine = new RenderLayoutEngine();
    private LayoutResult layoutResult;
    private int layoutWidth = -1;

    public DomViewPanel(Document document) {
        this.document = document;
        setLayout(null);
        setOpaque(true);
        setBackground(UiTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(
                CONTENT_PADDING, CONTENT_PADDING, CONTENT_PADDING, CONTENT_PADDING));
        rebuildRenderTree();

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                if (layoutWidth != getWidth()) {
                    invalidateReflow();
                    revalidate();
                    repaint();
                }
            }
        });
    }

    public void refreshFromDocument() {
        rebuildRenderTree();
        revalidate();
        repaint();
    }

    private void rebuildRenderTree() {
        renderTree = new RenderTreeBuilder().build(document);
        invalidateReflow();
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
                if (fragment instanceof BoxFragment box) {
                    paintBox(g2d, box);
                } else if (fragment instanceof InlineBoxFragment inlineBox) {
                    paintInlineBox(g2d, inlineBox);
                } else if (fragment instanceof TextFragment text) {
                    g2d.setFont(text.font());
                    g2d.setColor(toAwtColor(text.color()));
                    g2d.drawString(text.text(), text.x(), text.baseline());
                }
            }
        } finally {
            g2d.dispose();
        }
    }

    private boolean ensureLayout(int width, Graphics2D graphics) {
        if (layoutWidth == width && layoutResult != null) {
            return false;
        }
        float oldHeight = layoutResult == null ? -1 : layoutResult.height();
        layoutResult = layoutEngine.layout(renderTree, width, getInsets(), graphics);
        layoutWidth = width;
        return oldHeight != layoutResult.height();
    }

    private static void paintBox(Graphics2D graphics, BoxFragment fragment) {
        paintStyledBox(graphics, fragment.box().style(),
                fragment.x(), fragment.y(), fragment.width(), fragment.height(), true, true);
    }

    private static void paintInlineBox(Graphics2D graphics, InlineBoxFragment fragment) {
        paintStyledBox(graphics, fragment.box().style(),
                fragment.x(), fragment.y(), fragment.width(), fragment.height(),
                fragment.firstFragment(), fragment.lastFragment());
    }

    private static void paintStyledBox(Graphics2D graphics,
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
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            configureGraphics(graphics);
            return layoutEngine.layout(renderTree, width, getInsets(), graphics);
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

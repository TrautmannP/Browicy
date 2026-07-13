package com.browicy.ui;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import com.browicy.engine.dom.TextNode;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.Scrollable;

/**
 * Rendert einen DOM-Baum direkt mit Java2D auf einer einzigen Swing-Komponente.
 *
 * <p>Der DOM-Baum wird beim Aufbau in einfache Textblöcke überführt. Ein
 * breitenabhängiger Reflow-Pass berechnet daraus Zeilen und die Gesamthöhe für
 * das umgebende {@code JScrollPane}. Der Paint-Pass zeichnet anschließend nur
 * die im sichtbaren Bereich liegenden Zeilen.</p>
 */
public final class DomViewPanel extends JPanel implements Scrollable {

    private static final int CONTENT_PADDING = 16;
    private static final int BLOCK_GAP = 8;
    private static final int DEFAULT_LAYOUT_WIDTH = 800;
    private static final int SCROLL_UNIT = 24;

    /** Elemente, deren Inhalt nicht dargestellt wird. */
    private static final Set<String> HIDDEN_TAGS =
            Set.of("head", "title", "meta", "link", "script", "style");

    /** Tags, die als eigener Block (eigene Zeile) gerendert werden. */
    private static final Set<String> BLOCK_TAGS = Set.of(
            "p", "div", "section", "article", "main", "header", "footer",
            "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote");

    private final List<TextBlock> textBlocks = new ArrayList<>();
    private List<RenderedLine> renderedLines = List.of();
    private int layoutWidth = -1;
    private int layoutHeight = CONTENT_PADDING * 2;

    public DomViewPanel(Document document) {
        setLayout(null);
        setOpaque(true);
        setBackground(UiTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(
                CONTENT_PADDING, CONTENT_PADDING, CONTENT_PADDING, CONTENT_PADDING));

        Element body = document.getBody();
        if (body != null) {
            collectChildren(body);
        }

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

    private void collectChildren(Node node) {
        for (Node child : node.getChildren()) {
            if (child instanceof TextNode text) {
                if (!text.isBlank()) {
                    addTextBlock(collapseWhitespace(text.getData()), UiTheme.BODY, Map.of());
                }
            } else if (child instanceof Element element) {
                collectElement(element);
            }
        }
    }

    private void collectElement(Element element) {
        if (HIDDEN_TAGS.contains(element.getTagName())) {
            return;
        }
        boolean containsBlocks = element.getChildElements().stream()
                .anyMatch(child -> BLOCK_TAGS.contains(child.getTagName()));
        if (containsBlocks) {
            collectChildren(element);
            return;
        }
        String text = collapseWhitespace(element.getTextContent());
        if (!text.isBlank()) {
            addTextBlock(text, UiTheme.fontFor(element.getTagName()), element.getComputedStyles());
        }
    }

    private void addTextBlock(String text, Font font, Map<String, String> styles) {
        textBlocks.add(new TextBlock(
                text,
                applyFontSize(font, styles.get("font-size")),
                parseColor(styles.get("color"))));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            configureGraphics(g2d);
            boolean heightChanged = ensureReflow(Math.max(1, getWidth()), g2d);
            if (heightChanged) {
                revalidate();
            }

            Rectangle clip = g2d.getClipBounds();
            for (RenderedLine line : renderedLines) {
                if (clip != null && !line.intersectsVertically(clip)) {
                    continue;
                }
                g2d.setFont(line.font());
                g2d.setColor(line.color());
                g2d.drawString(line.text(), line.x(), line.baseline());
            }
        } finally {
            g2d.dispose();
        }
    }

    private boolean ensureReflow(int width, Graphics2D graphics) {
        if (layoutWidth == width) {
            return false;
        }

        int previousHeight = layoutHeight;
        reflow(width, graphics);
        return previousHeight != layoutHeight;
    }

    private void reflow(int width, Graphics2D graphics) {
        Insets insets = getInsets();
        int availableWidth = Math.max(1, width - insets.left - insets.right);
        int y = insets.top;
        List<RenderedLine> lines = new ArrayList<>();

        for (TextBlock block : textBlocks) {
            FontMetrics metrics = graphics.getFontMetrics(block.font());
            for (String line : wrapText(block.text(), metrics, availableWidth)) {
                int baseline = y + metrics.getAscent();
                lines.add(new RenderedLine(
                        line,
                        insets.left,
                        baseline,
                        metrics.getHeight(),
                        block.font(),
                        block.color()));
                y += metrics.getHeight();
            }
            y += BLOCK_GAP;
        }

        renderedLines = List.copyOf(lines);
        layoutWidth = width;
        layoutHeight = Math.max(insets.top + insets.bottom, y + insets.bottom);
    }

    private static List<String> wrapText(String text, FontMetrics metrics, int availableWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();

        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (currentLine.isEmpty()) {
                appendWordOrChunks(word, currentLine, lines, metrics, availableWidth);
                continue;
            }

            String candidate = currentLine + " " + word;
            if (metrics.stringWidth(candidate) <= availableWidth) {
                currentLine.append(' ').append(word);
            } else {
                lines.add(currentLine.toString());
                currentLine.setLength(0);
                appendWordOrChunks(word, currentLine, lines, metrics, availableWidth);
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    /**
     * Zerlegt auch einzelne überbreite Wörter, damit keine gezeichnete Zeile
     * über die verfügbare Inhaltsbreite hinausragt.
     */
    private static void appendWordOrChunks(String word,
                                           StringBuilder currentLine,
                                           List<String> lines,
                                           FontMetrics metrics,
                                           int availableWidth) {
        int offset = 0;
        while (offset < word.length()) {
            int end = longestFittingEnd(word, offset, metrics, availableWidth);
            String chunk = word.substring(offset, end);
            if (end < word.length()) {
                lines.add(chunk);
            } else {
                currentLine.append(chunk);
            }
            offset = end;
        }
    }

    private static int longestFittingEnd(String text,
                                         int start,
                                         FontMetrics metrics,
                                         int availableWidth) {
        int codePointCount = text.codePointCount(start, text.length());
        int low = 1;
        int high = codePointCount;
        int bestCodePoints = 1;

        while (low <= high) {
            int middleCodePoints = (low + high) >>> 1;
            int end = text.offsetByCodePoints(start, middleCodePoints);
            if (metrics.stringWidth(text.substring(start, end)) <= availableWidth) {
                bestCodePoints = middleCodePoints;
                low = middleCodePoints + 1;
            } else {
                high = middleCodePoints - 1;
            }
        }
        return text.offsetByCodePoints(start, bestCodePoints);
    }

    private void invalidateReflow() {
        layoutWidth = -1;
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
        if (layoutWidth != width) {
            BufferedImage metricsImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D metricsGraphics = metricsImage.createGraphics();
            try {
                configureGraphics(metricsGraphics);
                ensureReflow(width, metricsGraphics);
            } finally {
                metricsGraphics.dispose();
            }
        }
        return new Dimension(width, layoutHeight);
    }

    private static void configureGraphics(Graphics2D graphics) {
        graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static Font applyFontSize(Font font, String value) {
        if (value == null) {
            return font;
        }
        try {
            String normalized = value.strip().toLowerCase(Locale.ROOT);
            float number = Float.parseFloat(normalized.substring(0, normalized.length() - 2));
            float size = normalized.endsWith("em") ? font.getSize2D() * number : number;
            return size > 0 ? font.deriveFont(size) : font;
        } catch (RuntimeException ignored) {
            return font;
        }
    }

    private static Color parseColor(String value) {
        if (value == null) {
            return UiTheme.TEXT_PRIMARY;
        }
        if (value.startsWith("#")) {
            try {
                String hex = value.substring(1);
                if (hex.length() == 3) {
                    hex = "" + hex.charAt(0) + hex.charAt(0)
                            + hex.charAt(1) + hex.charAt(1)
                            + hex.charAt(2) + hex.charAt(2);
                }
                return new Color(Integer.parseInt(hex, 16));
            } catch (RuntimeException ignored) {
                return UiTheme.TEXT_PRIMARY;
            }
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "black" -> Color.BLACK;
            case "white" -> Color.WHITE;
            case "red" -> Color.RED;
            case "green" -> Color.GREEN;
            case "blue" -> Color.BLUE;
            case "yellow" -> Color.YELLOW;
            case "gray", "grey" -> Color.GRAY;
            case "orange" -> Color.ORANGE;
            case "pink" -> Color.PINK;
            case "cyan" -> Color.CYAN;
            case "magenta" -> Color.MAGENTA;
            default -> UiTheme.TEXT_PRIMARY;
        };
    }

    /** HTML-Whitespace-Verhalten: aufeinanderfolgende Leerzeichen/Umbrüche zusammenfassen. */
    private static String collapseWhitespace(String text) {
        return text.replaceAll("\\s+", " ").strip();
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

    private record TextBlock(String text, Font font, Color color) {
    }

    private record RenderedLine(String text,
                                int x,
                                int baseline,
                                int height,
                                Font font,
                                Color color) {

        boolean intersectsVertically(Rectangle clip) {
            int top = baseline - height;
            return top < clip.y + clip.height && baseline > clip.y;
        }
    }
}

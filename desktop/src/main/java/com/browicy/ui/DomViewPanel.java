package com.browicy.ui;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import com.browicy.engine.dom.TextNode;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.Scrollable;

/**
 * Rendert einen DOM-Baum der Browicy-Engine als einfache Textblöcke.
 * Ohne CSS-Unterstützung gibt es nur die "Browser-Default"-Darstellung:
 * Überschriften größer, Absätze normal.
 */
public final class DomViewPanel extends JPanel implements Scrollable {

    /** Elemente, deren Inhalt nicht dargestellt wird. */
    private static final Set<String> HIDDEN_TAGS =
            Set.of("head", "title", "meta", "link", "script", "style");

    /** Tags, die als eigener Block (eigene Zeile) gerendert werden. */
    private static final Set<String> BLOCK_TAGS = Set.of(
            "p", "div", "section", "article", "main", "header", "footer",
            "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote");

    public DomViewPanel(Document document) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(UiTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        Element body = document.getBody();
        if (body != null) {
            renderChildren(body);
        }
    }

    private void renderChildren(Node node) {
        for (Node child : node.getChildren()) {
            if (child instanceof TextNode text) {
                if (!text.isBlank()) {
                    addTextBlock(collapseWhitespace(text.getData()), UiTheme.BODY);
                }
            } else if (child instanceof Element element) {
                renderElement(element);
            }
        }
    }

    private void renderElement(Element element) {
        if (HIDDEN_TAGS.contains(element.getTagName())) {
            return;
        }
        boolean containsBlocks = element.getChildElements().stream()
                .anyMatch(child -> BLOCK_TAGS.contains(child.getTagName()));
        if (containsBlocks) {
            // Container mit Block-Kindern: Kinder einzeln als Blöcke rendern
            renderChildren(element);
            return;
        }
        String text = collapseWhitespace(element.getTextContent());
        if (!text.isBlank()) {
            addTextBlock(text, UiTheme.fontFor(element.getTagName()));
        }
    }

    private void addTextBlock(String text, Font font) {
        JTextArea area = new JTextArea(text);
        area.setFont(font);
        area.setForeground(UiTheme.TEXT_PRIMARY);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setOpaque(false);
        area.setAlignmentX(LEFT_ALIGNMENT);
        add(area);
        add(Box.createVerticalStrut(8));
    }

    /** HTML-Whitespace-Verhalten: aufeinanderfolgende Leerzeichen/Umbrüche zu einem Leerzeichen zusammenfassen. */
    private static String collapseWhitespace(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }

    // Scrollable: Breite an den Viewport binden, damit Text umbricht.

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 24;
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

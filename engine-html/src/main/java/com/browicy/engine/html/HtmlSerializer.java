package com.browicy.engine.html;

import com.browicy.engine.dom.CommentNode;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import com.browicy.engine.dom.TextNode;

import java.util.Locale;

/**
 * Serialisiert DOM-Knoten als HTML-Markup, z.B. für {@code innerHTML} und
 * {@code outerHTML}. Die Ausgabe ist bewusst schlicht gehalten (HTML5-Syntax,
 * Attributwerte in doppelten Anführungszeichen) und reicht für den
 * Roundtrip über den {@link HtmlParser} aus.
 */
public final class HtmlSerializer {

    private HtmlSerializer() {
    }

    /** Serialisiert die Kindknoten eines Elements ({@code element.innerHTML}). */
    public static String innerHtml(Element element) {
        StringBuilder html = new StringBuilder();
        for (Node child : element.getChildren()) {
            appendNode(html, child);
        }
        return html.toString();
    }

    /** Serialisiert ein Element inklusive seines Start-Tags ({@code element.outerHTML}). */
    public static String outerHtml(Element element) {
        StringBuilder html = new StringBuilder();
        appendNode(html, element);
        return html.toString();
    }

    private static void appendNode(StringBuilder html, Node node) {
        switch (node.getNodeType()) {
            case Node.TEXT_NODE -> appendText(html, ((TextNode) node).getData());
            case Node.COMMENT_NODE -> html.append("<!--").append(((CommentNode) node).getData())
                    .append("-->");
            case Node.ELEMENT_NODE -> appendElement(html, (Element) node);
            default -> {
                // Dokumente und Fragmente serialisieren ihre Kinder direkt.
                for (Node child : node.getChildren()) {
                    appendNode(html, child);
                }
            }
        }
    }

    private static void appendElement(StringBuilder html, Element element) {
        html.append('<').append(element.getTagName());
        element.getAttributes().forEach((name, value) -> {
            html.append(' ').append(name).append("=\"");
            appendEscaped(html, value, true);
            html.append('"');
        });
        html.append('>');
        if (!HtmlTreeConstructionRules.isVoidElement(
                element.getTagName().toLowerCase(Locale.ROOT))) {
            for (Node child : element.getChildren()) {
                appendNode(html, child);
            }
            html.append("</").append(element.getTagName()).append('>');
        }
    }

    private static void appendText(StringBuilder html, String text) {
        appendEscaped(html, text, false);
    }

    private static void appendEscaped(StringBuilder html, String value, boolean attribute) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '&' -> html.append("&amp;");
                case '<' -> html.append("&lt;");
                case '>' -> html.append("&gt;");
                case '"' -> html.append(attribute ? "&quot;" : "\"");
                default -> html.append(character);
            }
        }
    }
}

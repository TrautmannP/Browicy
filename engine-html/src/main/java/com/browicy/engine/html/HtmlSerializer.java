package com.browicy.engine.html;

import com.browicy.engine.dom.CommentNode;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import com.browicy.engine.dom.TextNode;

import java.util.Locale;

public final class HtmlSerializer {

    private HtmlSerializer() {
    }

    public static String innerHtml(Element element) {
        StringBuilder html = new StringBuilder();
        for (Node child : element.getChildren()) {
            appendNode(html, child);
        }
        return html.toString();
    }

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
        int last = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '&' -> {
                    html.append(value, last, index).append("&amp;");
                    last = index + 1;
                }
                case '<' -> {
                    html.append(value, last, index).append("&lt;");
                    last = index + 1;
                }
                case '>' -> {
                    html.append(value, last, index).append("&gt;");
                    last = index + 1;
                }
                case '"' -> {
                    html.append(value, last, index).append(attribute ? "&quot;" : "\"");
                    last = index + 1;
                }
                default -> {
                }
            }
        }
        html.append(value, last, value.length());
    }
}

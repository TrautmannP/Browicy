package com.browicy.engine.dom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Basisklasse aller DOM-Knoten. Hält die Baumstruktur (Eltern/Kinder),
 * konkrete Knotentypen sind {@link Element}, {@link TextNode} und {@link Document}.
 */
public abstract class Node {

    private Node parent;
    private final List<Node> children = new ArrayList<>();

    public Node getParent() {
        return parent;
    }

    public List<Node> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public void appendChild(Node child) {
        if (child.parent != null) {
            throw new IllegalArgumentException("Node hat bereits einen Elternknoten");
        }
        child.parent = this;
        children.add(child);
    }

    /**
     * Entfernt alle Kindknoten und löst deren Eltern-Verweis
     * (z.B. für {@code element.textContent = "..."} aus JavaScript).
     */
    public void clearChildren() {
        for (Node child : children) {
            child.parent = null;
        }
        children.clear();
    }

    /**
     * Ersetzt den gesamten Inhalt dieses Teilbaums durch einen einzelnen Textknoten.
     */
    public void setTextContent(String text) {
        clearChildren();
        appendChild(new TextNode(text == null ? "" : text));
    }

    /**
     * Liefert den gesamten Textinhalt dieses Teilbaums (Textknoten in Dokumentreihenfolge).
     */
    public String getTextContent() {
        StringBuilder sb = new StringBuilder();
        collectText(this, sb);
        return sb.toString();
    }

    private static void collectText(Node node, StringBuilder sb) {
        if (node instanceof TextNode text) {
            sb.append(text.getData());
        }
        for (Node child : node.children) {
            collectText(child, sb);
        }
    }
}

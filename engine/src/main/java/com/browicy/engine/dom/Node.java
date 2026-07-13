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
        for (Node ancestor = this; ancestor != null; ancestor = ancestor.parent) {
            if (ancestor == child) {
                throw new IllegalArgumentException("Node kann nicht in einen eigenen Nachfahren eingefügt werden");
            }
        }
        if (child.parent != null) {
            child.parent.removeChild(child);
        }
        child.parent = this;
        children.add(child);
    }

    public void removeChild(Node child) {
        if (!children.remove(child)) {
            throw new IllegalArgumentException("Node ist kein Kind dieses Knotens");
        }
        child.parent = null;
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

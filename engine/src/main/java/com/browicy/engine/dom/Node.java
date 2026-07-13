package com.browicy.engine.dom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Basisklasse aller DOM-Knoten. Hält die Baumstruktur (Eltern/Kinder),
 * konkrete Knotentypen sind {@link Element}, {@link TextNode} und {@link Document}.
 * Jeder Knoten ist zugleich ein {@link EventTarget}.
 */
public abstract class Node implements EventTarget {

    public static final short ELEMENT_NODE = 1;
    public static final short TEXT_NODE = 3;
    public static final short COMMENT_NODE = 8;
    public static final short DOCUMENT_NODE = 9;
    public static final short DOCUMENT_TYPE_NODE = 10;
    public static final short DOCUMENT_FRAGMENT_NODE = 11;

    private Node parent;
    private final List<Node> children = new ArrayList<>();
    private final Map<String, List<RegisteredEventListener>> eventListeners = new HashMap<>();

    public Node getParent() {
        return parent;
    }

    public List<Node> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public abstract short getNodeType();

    public abstract String getNodeName();

    public String getNodeValue() {
        return null;
    }

    public void setNodeValue(String value) {
        // Element, Document und DocumentFragment haben per DOM-Spezifikation keinen nodeValue.
    }

    public Node getFirstChild() {
        return children.isEmpty() ? null : children.get(0);
    }

    public Node getLastChild() {
        return children.isEmpty() ? null : children.get(children.size() - 1);
    }

    public Node getPreviousSibling() {
        if (parent == null) return null;
        int index = parent.children.indexOf(this);
        return index > 0 ? parent.children.get(index - 1) : null;
    }

    public Node getNextSibling() {
        if (parent == null) return null;
        int index = parent.children.indexOf(this);
        return index >= 0 && index + 1 < parent.children.size() ? parent.children.get(index + 1) : null;
    }

    public boolean hasChildNodes() {
        return !children.isEmpty();
    }

    public boolean contains(Node other) {
        for (Node node = other; node != null; node = node.parent) {
            if (node == this) return true;
        }
        return false;
    }

    public void appendChild(Node child) {
        insertBefore(child, null);
    }

    public void insertBefore(Node child, Node reference) {
        if (this instanceof TextNode || this instanceof CommentNode) {
            throw new IllegalArgumentException("Dieser Knotentyp kann keine Kinder enthalten");
        }
        if (child instanceof Document) {
            throw new IllegalArgumentException("Ein Document kann nicht eingefügt werden");
        }
        if (child instanceof DocumentFragment fragment) {
            for (Node fragmentChild : List.copyOf(fragment.getChildren())) {
                insertBefore(fragmentChild, reference);
            }
            return;
        }
        for (Node ancestor = this; ancestor != null; ancestor = ancestor.parent) {
            if (ancestor == child) {
                throw new IllegalArgumentException("Node kann nicht in einen eigenen Nachfahren eingefügt werden");
            }
        }
        int index = reference == null ? children.size() : children.indexOf(reference);
        if (index < 0) {
            throw new IllegalArgumentException("Referenz-Node ist kein Kind dieses Knotens");
        }
        if (child.parent != null) {
            if (child.parent == this && children.indexOf(child) < index) {
                index--;
            }
            child.parent.removeChild(child);
        }
        child.parent = this;
        children.add(index, child);
    }

    public void removeChild(Node child) {
        if (!children.remove(child)) {
            throw new IllegalArgumentException("Node ist kein Kind dieses Knotens");
        }
        child.parent = null;
    }

    public void replaceChild(Node replacement, Node oldChild) {
        insertBefore(replacement, oldChild);
        removeChild(oldChild);
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

    @Override
    public void addEventListener(String type, EventListener listener, boolean useCapture) {
        String eventType = requireEventType(type);
        if (listener == null) {
            return;
        }
        List<RegisteredEventListener> listeners =
                eventListeners.computeIfAbsent(eventType, ignored -> new ArrayList<>());
        boolean alreadyRegistered = listeners.stream().anyMatch(registered ->
                registered.capture == useCapture && registered.listener.equals(listener));
        if (!alreadyRegistered) {
            listeners.add(new RegisteredEventListener(listener, useCapture));
        }
    }

    @Override
    public void removeEventListener(String type, EventListener listener, boolean useCapture) {
        if (type == null || type.isEmpty() || listener == null) {
            return;
        }
        List<RegisteredEventListener> listeners = eventListeners.get(type);
        if (listeners == null) {
            return;
        }
        for (int index = 0; index < listeners.size(); index++) {
            RegisteredEventListener registered = listeners.get(index);
            if (registered.capture == useCapture && registered.listener.equals(listener)) {
                listeners.remove(index);
                break;
            }
        }
        if (listeners.isEmpty()) {
            eventListeners.remove(type);
        }
    }

    @Override
    public boolean dispatchEvent(Event event) {
        Objects.requireNonNull(event, "event");
        event.beginDispatch(this);

        List<Node> ancestors = new ArrayList<>();
        for (Node ancestor = parent; ancestor != null; ancestor = ancestor.parent) {
            ancestors.add(ancestor);
        }

        try {
            for (int index = ancestors.size() - 1;
                 index >= 0 && !event.isPropagationStopped();
                 index--) {
                ancestors.get(index).invokeEventListeners(event, Event.CAPTURING_PHASE, true);
            }

            if (!event.isPropagationStopped()) {
                invokeEventListeners(event, Event.AT_TARGET, null);
            }

            if (event.isBubbles() && !event.isPropagationStopped()) {
                for (Node ancestor : ancestors) {
                    ancestor.invokeEventListeners(event, Event.BUBBLING_PHASE, false);
                    if (event.isPropagationStopped()) {
                        break;
                    }
                }
            }
        } finally {
            event.finishDispatch();
        }
        return !event.isDefaultPrevented();
    }

    /**
     * {@code captureFilter == null} bedeutet Target-Phase: dort werden
     * Capturing- und Bubbling-Listener in Registrierungsreihenfolge aufgerufen.
     */
    private void invokeEventListeners(Event event, short phase, Boolean captureFilter) {
        List<RegisteredEventListener> current = eventListeners.get(event.getType());
        if (current == null || current.isEmpty()) {
            return;
        }
        event.enter(this, phase);
        for (RegisteredEventListener registered : List.copyOf(current)) {
            // Ein während des Dispatches entfernter Listener darf nicht mehr laufen;
            // ein neu hinzugefügter Listener ist nicht Teil des Snapshots.
            List<RegisteredEventListener> live = eventListeners.get(event.getType());
            if (live == null || !live.contains(registered)) {
                continue;
            }
            if (captureFilter != null && registered.capture != captureFilter) {
                continue;
            }
            registered.listener.handleEvent(event);
            if (event.isImmediatePropagationStopped()) {
                break;
            }
        }
    }

    private static String requireEventType(String type) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("Event-Typ darf nicht leer sein");
        }
        return type;
    }

    private static void collectText(Node node, StringBuilder sb) {
        if (node instanceof TextNode text) {
            sb.append(text.getData());
        }
        for (Node child : node.children) {
            collectText(child, sb);
        }
    }

    private static final class RegisteredEventListener {
        private final EventListener listener;
        private final boolean capture;

        private RegisteredEventListener(EventListener listener, boolean capture) {
            this.listener = listener;
            this.capture = capture;
        }
    }
}

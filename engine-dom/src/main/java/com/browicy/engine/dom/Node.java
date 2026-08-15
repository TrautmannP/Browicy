package com.browicy.engine.dom;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public abstract class Node implements EventTarget {

    private static final AtomicLong NEXT_NODE_ORDER = new AtomicLong();

    public static final short ELEMENT_NODE = 1;
    public static final short TEXT_NODE = 3;
    public static final short COMMENT_NODE = 8;
    public static final short DOCUMENT_NODE = 9;
    public static final short DOCUMENT_TYPE_NODE = 10;
    public static final short DOCUMENT_FRAGMENT_NODE = 11;

    public static final short DOCUMENT_POSITION_DISCONNECTED = 0x01;
    public static final short DOCUMENT_POSITION_PRECEDING = 0x02;
    public static final short DOCUMENT_POSITION_FOLLOWING = 0x04;
    public static final short DOCUMENT_POSITION_CONTAINS = 0x08;
    public static final short DOCUMENT_POSITION_CONTAINED_BY = 0x10;
    public static final short DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC = 0x20;

    private final long nodeOrder = NEXT_NODE_ORDER.getAndIncrement();
    @Getter
    private Node parent;
    @Getter
    private Document ownerDocument;
    private final List<Node> children = new ArrayList<>();
    private final Map<String, List<RegisteredEventListener>> eventListeners = new HashMap<>();

    public List<Node> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public abstract short getNodeType();

    public abstract String getNodeName();

    public String getNodeValue() {
        return null;
    }

    public void setNodeValue(String value) {
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

    public short compareDocumentPosition(Node other) {
        Objects.requireNonNull(other, "other");
        if (this == other) {
            return 0;
        }

        Node thisRoot = rootOf(this);
        Node otherRoot = rootOf(other);
        if (thisRoot != otherRoot) {
            short direction = nodeOrder < other.nodeOrder
                    ? DOCUMENT_POSITION_FOLLOWING
                    : DOCUMENT_POSITION_PRECEDING;
            return (short) (DOCUMENT_POSITION_DISCONNECTED
                    | DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC
                    | direction);
        }

        if (contains(other)) {
            return (short) (DOCUMENT_POSITION_FOLLOWING | DOCUMENT_POSITION_CONTAINED_BY);
        }
        if (other.contains(this)) {
            return (short) (DOCUMENT_POSITION_PRECEDING | DOCUMENT_POSITION_CONTAINS);
        }

        List<Node> thisPath = pathFromRoot(this);
        List<Node> otherPath = pathFromRoot(other);
        int index = 0;
        int commonLength = Math.min(thisPath.size(), otherPath.size());
        while (index < commonLength && thisPath.get(index) == otherPath.get(index)) {
            index++;
        }
        Node commonParent = thisPath.get(index - 1);
        Node thisBranch = thisPath.get(index);
        Node otherBranch = otherPath.get(index);
        return commonParent.children.indexOf(thisBranch) < commonParent.children.indexOf(otherBranch)
                ? DOCUMENT_POSITION_FOLLOWING
                : DOCUMENT_POSITION_PRECEDING;
    }

    public boolean isSameNode(Node other) {
        return this == other;
    }

    public boolean isEqualNode(Node other) {
        if (other == null || getNodeType() != other.getNodeType()
                || !Objects.equals(getNodeName(), other.getNodeName())
                || !Objects.equals(getNodeValue(), other.getNodeValue())) {
            return false;
        }
        if (this instanceof Element element) {
            if (!(other instanceof Element otherElement)
                    || !element.getAttributes().equals(otherElement.getAttributes())) {
                return false;
            }
        }
        if (children.size() != other.children.size()) {
            return false;
        }
        for (int index = 0; index < children.size(); index++) {
            if (!children.get(index).isEqualNode(other.children.get(index))) {
                return false;
            }
        }
        return true;
    }

    public Node cloneNode(boolean deep) {
        Node clone = switch (this) {
            case Element element -> element.copyShallow();
            case TextNode text -> new TextNode(text.getData());
            case CommentNode comment -> new CommentNode(comment.getData());
            case DocumentFragment ignored -> new DocumentFragment();
            case DocumentType type -> new DocumentType(type.getName(), type.getPublicId(), type.getSystemId());
            case Document document -> document.copyShallow();
            default -> throw new IllegalStateException("Unbekannter Knotentyp: " + getClass());
        };
        clone.setOwnerDocument(this instanceof Document ? null : ownerDocument);
        if (deep) {
            for (Node child : children) clone.appendChild(child.cloneNode(true));
        }
        return clone;
    }

    public void appendChild(Node child) {
        insertBefore(child, null);
    }

    public void insertBefore(Node child, Node reference) {
        insertBeforeInternal(child, reference, true, true);
    }

    void insertBeforeWithoutRangeAdjustment(Node child, Node reference) {
        insertBeforeInternal(child, reference, false, true);
    }

    private void insertBeforeInternal(Node child, Node reference,
                                      boolean updateRanges, boolean notifyObservers) {
        Objects.requireNonNull(child, "child");
        if (this instanceof TextNode || this instanceof CommentNode) {
            throw DomException.hierarchyRequest("Dieser Knotentyp kann keine Kinder enthalten");
        }
        if (child instanceof Document) {
            throw DomException.hierarchyRequest("Ein Document kann nicht eingefügt werden");
        }
        if (child instanceof DocumentFragment fragment) {
            for (Node fragmentChild : List.copyOf(fragment.getChildren())) {
                insertBeforeInternal(fragmentChild, reference, updateRanges, notifyObservers);
            }
            return;
        }
        for (Node ancestor = this; ancestor != null; ancestor = ancestor.parent) {
            if (ancestor == child) {
                throw DomException.hierarchyRequest("Node kann nicht in einen eigenen Nachfahren eingefügt werden");
            }
        }
        int index = reference == null ? children.size() : children.indexOf(reference);
        if (index < 0) {
            throw DomException.notFound("Referenz-Node ist kein Kind dieses Knotens");
        }
        validateChildInsertion(child);
        if (child.parent != null) {
            if (child.parent == this && children.indexOf(child) < index) {
                index--;
            }
            child.parent.removeChildInternal(child, notifyObservers);
        }
        child.parent = this;
        Document newOwner = this instanceof Document document ? document : ownerDocument;
        child.setOwnerDocument(newOwner);
        children.add(index, child);
        if (updateRanges) {
            Range.nodeInserted(this, index, 1);
        }
        if (notifyObservers) {
            Node previousSibling = index > 0 ? children.get(index - 1) : null;
            Node nextSibling = index + 1 < children.size() ? children.get(index + 1) : null;
            notifyMutation(new DomMutation.ChildListChanged(
                    this, List.of(child), List.of(), previousSibling, nextSibling));
        }
    }

    public void removeChild(Node child) {
        removeChildInternal(child, true);
    }

    private void removeChildInternal(Node child, boolean notifyObservers) {
        int index = children.indexOf(child);
        if (index < 0) {
            throw DomException.notFound("Node ist kein Kind dieses Knotens");
        }
        Node previousSibling = index > 0 ? children.get(index - 1) : null;
        Node nextSibling = index + 1 < children.size() ? children.get(index + 1) : null;
        Range.nodeRemoved(this, index, child);
        children.remove(index);
        child.parent = null;
        if (notifyObservers) {
            notifyMutation(new DomMutation.ChildListChanged(
                    this, List.of(), List.of(child), previousSibling, nextSibling));
        }
    }

    public void replaceChild(Node replacement, Node oldChild) {
        insertBefore(replacement, oldChild);
        removeChild(oldChild);
    }

    public void before(Object... nodesOrStrings) {
        if (parent == null) {
            return;
        }
        parent.insertNodesBefore(this, convertNodesIntoNode(this, nodesOrStrings));
    }

    public void after(Object... nodesOrStrings) {
        if (parent == null) {
            return;
        }
        parent.insertNodesAfter(this, convertNodesIntoNode(this, nodesOrStrings));
    }

    public void replaceWith(Object... nodesOrStrings) {
        if (parent == null) {
            return;
        }
        parent.replaceNodeWith(this, convertNodesIntoNode(this, nodesOrStrings));
    }

    public void remove() {
        if (parent == null) {
            return;
        }
        parent.removeChild(this);
    }

    static List<Node> convertNodesIntoNode(Node context, Object... nodesOrStrings) {
        List<Node> result = new ArrayList<>();
        if (nodesOrStrings == null) {
            return result;
        }
        for (Object value : nodesOrStrings) {
            if (value == null) {
                continue;
            }
            if (value instanceof Node node) {
                if (node instanceof DocumentFragment fragment) {
                    result.addAll(fragment.getChildren());
                } else {
                    result.add(node);
                }
            } else {
                result.add(textNodeFor(context, String.valueOf(value)));
            }
        }
        return result;
    }

    private static TextNode textNodeFor(Node context, String data) {
        Document owner = context instanceof Document document ? document : context.getOwnerDocument();
        return owner == null ? new TextNode(data) : owner.createTextNode(data);
    }

    final void appendNodes(List<Node> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        validateBatchInsertable(nodes);
        Node previousSibling = getLastChild();
        for (Node node : nodes) {
            insertBeforeInternal(node, null, true, false);
        }
        notifyMutation(new DomMutation.ChildListChanged(
                this, nodes, List.of(), previousSibling, null));
    }

    final void prependNodes(List<Node> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        validateBatchInsertable(nodes);
        Node reference = getFirstChild();
        for (Node node : nodes) {
            insertBeforeInternal(node, reference, true, false);
        }
        notifyMutation(new DomMutation.ChildListChanged(
                this, nodes, List.of(), null, reference));
    }

    final void replaceAllChildren(List<Node> nodes) {
        List<Node> removed = new ArrayList<>(children);
        validateBatchSequence(nodes);
        Node previousSibling = removed.isEmpty() ? null : removed.get(removed.size() - 1);
        for (Node child : removed) {
            removeChildInternal(child, false);
        }
        for (Node node : nodes) {
            insertBeforeInternal(node, null, true, false);
        }
        if (!removed.isEmpty() || !nodes.isEmpty()) {
            notifyMutation(new DomMutation.ChildListChanged(
                    this, nodes, removed, previousSibling, null));
        }
    }

    void validateBatchSequence(List<Node> nodes) {
    }

    final void insertNodesBefore(Node reference, List<Node> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        if (children.indexOf(reference) < 0) {
            throw DomException.notFound("Node ist kein Kind dieses Knotens");
        }
        validateBatchInsertable(nodes);
        Node previousSibling = reference.getPreviousSibling();
        for (Node node : nodes) {
            insertBeforeInternal(node, reference, true, false);
        }
        notifyMutation(new DomMutation.ChildListChanged(
                this, nodes, List.of(), previousSibling, reference));
    }

    final void insertNodesAfter(Node reference, List<Node> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        if (children.indexOf(reference) < 0) {
            throw DomException.notFound("Node ist kein Kind dieses Knotens");
        }
        validateBatchInsertable(nodes);
        Node nextSibling = reference.getNextSibling();
        for (Node node : nodes) {
            insertBeforeInternal(node, nextSibling, true, false);
        }
        notifyMutation(new DomMutation.ChildListChanged(
                this, nodes, List.of(), reference, nextSibling));
    }

    final void replaceNodeWith(Node oldChild, List<Node> nodes) {
        if (children.indexOf(oldChild) < 0) {
            throw DomException.notFound("Node ist kein Kind dieses Knotens");
        }
        validateBatchInsertable(nodes);
        Node previousSibling = oldChild.getPreviousSibling();
        Node nextSibling = oldChild.getNextSibling();
        for (Node node : nodes) {
            insertBeforeInternal(node, oldChild, true, false);
        }
        removeChildInternal(oldChild, false);
        notifyMutation(new DomMutation.ChildListChanged(
                this, nodes, List.of(oldChild), previousSibling, nextSibling));
    }

    private void validateBatchInsertable(List<Node> nodes) {
        for (Node node : nodes) {
            if (node instanceof Document) {
                throw DomException.hierarchyRequest("Ein Document kann nicht eingefügt werden");
            }
            for (Node ancestor = this; ancestor != null; ancestor = ancestor.parent) {
                if (ancestor == node) {
                    throw DomException.hierarchyRequest(
                            "Node kann nicht in einen eigenen Nachfahren eingefügt werden");
                }
            }
        }
    }

    protected void validateChildInsertion(Node child) {
    }

    public void clearChildren() {
        for (Node child : List.copyOf(children)) {
            removeChild(child);
        }
    }

    public void setTextContent(String text) {
        clearChildren();
        appendChild(new TextNode(text == null ? "" : text));
    }

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

    private void invokeEventListeners(Event event, short phase, Boolean captureFilter) {
        List<RegisteredEventListener> current = eventListeners.get(event.getType());
        if (current == null || current.isEmpty()) {
            return;
        }
        event.enter(this, phase);
        for (RegisteredEventListener registered : List.copyOf(current)) {
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

    final void notifyCharacterDataChanged(String oldValue, String newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            notifyMutation(new DomMutation.CharacterDataChanged(this, oldValue, newValue));
        }
    }

    final void notifyMutation(DomMutation mutation) {
        Node root = rootOf(this);
        if (root instanceof Document document) {
            document.dispatchMutation(mutation);
        }
    }

    static Node rootOf(Node node) {
        Node root = node;
        while (root.parent != null) {
            root = root.parent;
        }
        return root;
    }

    static int indexInParent(Node node) {
        return node.parent == null ? -1 : node.parent.children.indexOf(node);
    }

    private static List<Node> pathFromRoot(Node node) {
        List<Node> path = new ArrayList<>();
        for (Node current = node; current != null; current = current.parent) {
            path.add(current);
        }
        Collections.reverse(path);
        return path;
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

    final void setOwnerDocument(Document document) {
        if (!(this instanceof Document)) {
            ownerDocument = document;
        }
        for (Node child : children) {
            child.setOwnerDocument(document);
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

package com.browicy.engine.dom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Live-DOM-Range mit Grenzpunkten aus Container und Offset.
 *
 * <p>Die Implementierung deckt die zentralen DOM-Level-2-Operationen ab und
 * passt aktive Grenzpunkte bei Einfüge-, Entfernungs- und Text-Split-Mutationen
 * automatisch an.</p>
 */
public final class Range {

    public static final short START_TO_START = 0;
    public static final short START_TO_END = 1;
    public static final short END_TO_END = 2;
    public static final short END_TO_START = 3;

    private static final Map<Range, Boolean> ACTIVE_RANGES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final Document document;
    private Node startContainer;
    private int startOffset;
    private Node endContainer;
    private int endOffset;

    public Range(Document document) {
        this.document = Objects.requireNonNull(document, "document");
        startContainer = document;
        endContainer = document;
        ACTIVE_RANGES.put(this, Boolean.TRUE);
    }

    public Document getDocument() {
        return document;
    }

    public Node getStartContainer() {
        return startContainer;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public Node getEndContainer() {
        return endContainer;
    }

    public int getEndOffset() {
        return endOffset;
    }

    public boolean isCollapsed() {
        return startContainer == endContainer && startOffset == endOffset;
    }

    public Node getCommonAncestorContainer() {
        Node common = startContainer;
        while (common != null && !common.contains(endContainer)) {
            common = common.getParent();
        }
        return common;
    }

    public void setStart(Node node, int offset) {
        ensureActive();
        validateBoundary(node, offset);
        if (Node.rootOf(node) != Node.rootOf(endContainer)
                || comparePoints(node, offset, endContainer, endOffset) > 0) {
            endContainer = node;
            endOffset = offset;
        }
        startContainer = node;
        startOffset = offset;
    }

    public void setEnd(Node node, int offset) {
        ensureActive();
        validateBoundary(node, offset);
        if (Node.rootOf(node) != Node.rootOf(startContainer)
                || comparePoints(startContainer, startOffset, node, offset) > 0) {
            startContainer = node;
            startOffset = offset;
        }
        endContainer = node;
        endOffset = offset;
    }

    public void setStartBefore(Node node) {
        Node parent = requireParent(node);
        setStart(parent, Node.indexInParent(node));
    }

    public void setStartAfter(Node node) {
        Node parent = requireParent(node);
        setStart(parent, Node.indexInParent(node) + 1);
    }

    public void setEndBefore(Node node) {
        Node parent = requireParent(node);
        setEnd(parent, Node.indexInParent(node));
    }

    public void setEndAfter(Node node) {
        Node parent = requireParent(node);
        setEnd(parent, Node.indexInParent(node) + 1);
    }

    public void collapse(boolean toStart) {
        ensureActive();
        if (toStart) {
            endContainer = startContainer;
            endOffset = startOffset;
        } else {
            startContainer = endContainer;
            startOffset = endOffset;
        }
    }

    public void selectNode(Node node) {
        ensureActive();
        Node parent = requireParent(node);
        int index = Node.indexInParent(node);
        startContainer = parent;
        startOffset = index;
        endContainer = parent;
        endOffset = index + 1;
    }

    public void selectNodeContents(Node node) {
        ensureActive();
        validateContainer(node);
        startContainer = node;
        startOffset = 0;
        endContainer = node;
        endOffset = boundaryLength(node);
    }

    public void insertNode(Node node) {
        ensureActive();
        Objects.requireNonNull(node, "node");
        if (startContainer instanceof TextNode text) {
            Node parent = requireParent(text);
            TextNode tail = text.splitText(startOffset);
            parent.insertBefore(node, tail);
            return;
        }
        if (startContainer instanceof CommentNode) {
            throw DomException.hierarchyRequest("In einen Kommentar kann kein Knoten eingefügt werden");
        }
        Node reference = startOffset < startContainer.getChildren().size()
                ? startContainer.getChildren().get(startOffset)
                : null;
        startContainer.insertBefore(node, reference);
    }

    public DocumentFragment extractContents() {
        ensureActive();
        DocumentFragment fragment = document.createDocumentFragment();
        if (isCollapsed()) {
            return fragment;
        }

        if (startContainer == endContainer) {
            if (isCharacterData(startContainer)) {
                String selected = characterData(startContainer)
                        .substring(startOffset, endOffset);
                fragment.appendChild(characterDataClone(startContainer, selected));
                replaceCharacterData(startContainer, startOffset, endOffset - startOffset, "");
                collapse(true);
                return fragment;
            }
            List<Node> selected = new ArrayList<>(
                    startContainer.getChildren().subList(startOffset, endOffset));
            for (Node child : selected) {
                fragment.appendChild(child);
            }
            collapse(true);
            return fragment;
        }

        Node common = getCommonAncestorContainer();
        Node firstPartial = startContainer == common ? null : childBelow(common, startContainer);
        Node lastPartial = endContainer == common ? null : childBelow(common, endContainer);

        int containedStart = startContainer == common
                ? startOffset
                : Node.indexInParent(firstPartial) + 1;
        int containedEnd = endContainer == common
                ? endOffset
                : Node.indexInParent(lastPartial);
        List<Node> contained = containedStart <= containedEnd
                ? new ArrayList<>(common.getChildren().subList(containedStart, containedEnd))
                : List.of();

        Node originalStartContainer = startContainer;
        int originalStartOffset = startOffset;
        Node originalEndContainer = endContainer;
        int originalEndOffset = endOffset;

        if (firstPartial != null) {
            fragment.appendChild(extractPartialStart(
                    firstPartial, originalStartContainer, originalStartOffset));
        }
        for (Node child : contained) {
            fragment.appendChild(child);
        }
        if (lastPartial != null) {
            fragment.appendChild(extractPartialEnd(
                    lastPartial, originalEndContainer, originalEndOffset));
        }

        collapse(true);
        return fragment;
    }

    public DocumentFragment cloneContents() {
        ensureActive();
        DocumentFragment fragment = document.createDocumentFragment();
        if (isCollapsed()) {
            return fragment;
        }

        if (startContainer == endContainer) {
            if (isCharacterData(startContainer)) {
                fragment.appendChild(characterDataClone(startContainer,
                        characterData(startContainer).substring(startOffset, endOffset)));
                return fragment;
            }
            for (Node child : startContainer.getChildren().subList(startOffset, endOffset)) {
                fragment.appendChild(deepClone(child));
            }
            return fragment;
        }

        Node common = getCommonAncestorContainer();
        Node firstPartial = startContainer == common ? null : childBelow(common, startContainer);
        Node lastPartial = endContainer == common ? null : childBelow(common, endContainer);
        int containedStart = startContainer == common
                ? startOffset
                : Node.indexInParent(firstPartial) + 1;
        int containedEnd = endContainer == common
                ? endOffset
                : Node.indexInParent(lastPartial);

        if (firstPartial != null) {
            fragment.appendChild(clonePartialStart(firstPartial, startContainer, startOffset));
        }
        for (Node child : common.getChildren().subList(containedStart, containedEnd)) {
            fragment.appendChild(deepClone(child));
        }
        if (lastPartial != null) {
            fragment.appendChild(clonePartialEnd(lastPartial, endContainer, endOffset));
        }
        return fragment;
    }

    public void deleteContents() {
        extractContents();
    }

    public Range cloneRange() {
        ensureActive();
        Range clone = new Range(document);
        clone.startContainer = startContainer;
        clone.startOffset = startOffset;
        clone.endContainer = endContainer;
        clone.endOffset = endOffset;
        return clone;
    }

    public short compareBoundaryPoints(short how, Range sourceRange) {
        ensureActive();
        Objects.requireNonNull(sourceRange, "sourceRange").ensureActive();
        Node leftContainer;
        int leftOffset;
        Node rightContainer;
        int rightOffset;
        switch (how) {
            case START_TO_START -> {
                leftContainer = startContainer;
                leftOffset = startOffset;
                rightContainer = sourceRange.startContainer;
                rightOffset = sourceRange.startOffset;
            }
            case START_TO_END -> {
                leftContainer = startContainer;
                leftOffset = startOffset;
                rightContainer = sourceRange.endContainer;
                rightOffset = sourceRange.endOffset;
            }
            case END_TO_END -> {
                leftContainer = endContainer;
                leftOffset = endOffset;
                rightContainer = sourceRange.endContainer;
                rightOffset = sourceRange.endOffset;
            }
            case END_TO_START -> {
                leftContainer = endContainer;
                leftOffset = endOffset;
                rightContainer = sourceRange.startContainer;
                rightOffset = sourceRange.startOffset;
            }
            default -> throw new IllegalArgumentException("Unbekannter Vergleichstyp: " + how);
        }
        return (short) Integer.signum(comparePoints(
                leftContainer, leftOffset, rightContainer, rightOffset));
    }

    /**
     * Umschließt den Bereich mit {@code newParent}. Teilweise ausgewählte
     * Nicht-Text-Knoten werden abgelehnt.
     */
    public void surroundContents(Node newParent) {
        ensureActive();
        Objects.requireNonNull(newParent, "newParent");
        Node common = getCommonAncestorContainer();
        Node firstPartial = startContainer == common ? null : childBelow(common, startContainer);
        Node lastPartial = endContainer == common ? null : childBelow(common, endContainer);
        if (firstPartial != null && !(firstPartial instanceof TextNode)) {
            throw DomException.invalidState("Der Bereich schneidet den Startknoten nur teilweise");
        }
        if (lastPartial != null && !(lastPartial instanceof TextNode)) {
            throw DomException.invalidState("Der Bereich schneidet den Endknoten nur teilweise");
        }
        newParent.clearChildren();
        DocumentFragment contents = extractContents();
        insertNode(newParent);
        for (Node child : List.copyOf(contents.getChildren())) {
            newParent.appendChild(child);
        }
        selectNode(newParent);
    }

    public void detach() {
        // Legacy no-op per DOM Standard. A detached Range remains fully usable and live.
    }

    @Override
    public String toString() {
        return textContentExcludingComments(cloneContents());
    }

    private static String textContentExcludingComments(Node node) {
        if (node instanceof TextNode text) {
            return text.getData();
        }
        if (node instanceof CommentNode) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (Node child : node.getChildren()) {
            result.append(textContentExcludingComments(child));
        }
        return result.toString();
    }

    private Node extractPartialStart(Node partial, Node container, int offset) {
        if (isCharacterData(partial)) {
            String data = characterData(partial);
            Node clone = characterDataClone(partial, data.substring(offset));
            replaceCharacterData(partial, offset, data.length() - offset, "");
            return clone;
        }
        Node clone = shallowClone(partial);
        Range subrange = new Range(document);
        try {
            subrange.setStart(container, offset);
            subrange.setEnd(partial, boundaryLength(partial));
            DocumentFragment extracted = subrange.extractContents();
            moveChildren(extracted, clone);
            return clone;
        } finally {
            subrange.detach();
        }
    }

    private Node extractPartialEnd(Node partial, Node container, int offset) {
        if (isCharacterData(partial)) {
            String data = characterData(partial);
            Node clone = characterDataClone(partial, data.substring(0, offset));
            replaceCharacterData(partial, 0, offset, "");
            return clone;
        }
        Node clone = shallowClone(partial);
        Range subrange = new Range(document);
        try {
            subrange.setStart(partial, 0);
            subrange.setEnd(container, offset);
            DocumentFragment extracted = subrange.extractContents();
            moveChildren(extracted, clone);
            return clone;
        } finally {
            subrange.detach();
        }
    }

    private Node clonePartialStart(Node partial, Node container, int offset) {
        if (isCharacterData(partial)) {
            return characterDataClone(partial, characterData(partial).substring(offset));
        }
        Node clone = shallowClone(partial);
        Range subrange = new Range(document);
        try {
            subrange.setStart(container, offset);
            subrange.setEnd(partial, boundaryLength(partial));
            moveChildren(subrange.cloneContents(), clone);
            return clone;
        } finally {
            subrange.detach();
        }
    }

    private Node clonePartialEnd(Node partial, Node container, int offset) {
        if (isCharacterData(partial)) {
            return characterDataClone(partial, characterData(partial).substring(0, offset));
        }
        Node clone = shallowClone(partial);
        Range subrange = new Range(document);
        try {
            subrange.setStart(partial, 0);
            subrange.setEnd(container, offset);
            moveChildren(subrange.cloneContents(), clone);
            return clone;
        } finally {
            subrange.detach();
        }
    }

    private static void moveChildren(DocumentFragment source, Node target) {
        for (Node child : List.copyOf(source.getChildren())) {
            target.appendChild(child);
        }
    }

    private static Node childBelow(Node ancestor, Node descendant) {
        Node child = descendant;
        while (child.getParent() != ancestor) {
            child = child.getParent();
            if (child == null) {
                throw new IllegalArgumentException("Knoten liegen nicht im selben Teilbaum");
            }
        }
        return child;
    }

    private static Node requireParent(Node node) {
        Objects.requireNonNull(node, "node");
        if (node.getParent() == null) {
            throw DomException.invalidNodeType("Knoten besitzt keinen Elternknoten");
        }
        return node.getParent();
    }

    private static void validateBoundary(Node node, int offset) {
        validateContainer(node);
        int length = boundaryLength(node);
        if (offset < 0 || offset > length) {
            throw new IndexOutOfBoundsException(
                    "Range-Offset " + offset + " liegt außerhalb von 0.." + length);
        }
    }

    private static void validateContainer(Node node) {
        Objects.requireNonNull(node, "node");
        if (node instanceof DocumentType) {
            throw DomException.invalidNodeType("DocumentType ist kein gültiger Range-Container");
        }
    }

    private static int boundaryLength(Node node) {
        if (node instanceof TextNode text) {
            return text.getData().length();
        }
        if (node instanceof CommentNode comment) {
            return comment.getData().length();
        }
        return node.getChildren().size();
    }

    private static int comparePoints(Node firstContainer, int firstOffset,
                                     Node secondContainer, int secondOffset) {
        if (firstContainer == secondContainer) {
            return Integer.compare(firstOffset, secondOffset);
        }
        if (Node.rootOf(firstContainer) != Node.rootOf(secondContainer)) {
            throw new IllegalArgumentException("Range-Grenzpunkte liegen in getrennten Bäumen");
        }
        if (firstContainer.contains(secondContainer)) {
            Node child = childBelow(firstContainer, secondContainer);
            return firstOffset <= Node.indexInParent(child) ? -1 : 1;
        }
        if (secondContainer.contains(firstContainer)) {
            Node child = childBelow(secondContainer, firstContainer);
            return Node.indexInParent(child) < secondOffset ? -1 : 1;
        }

        List<Node> firstPath = pathFromRoot(firstContainer);
        List<Node> secondPath = pathFromRoot(secondContainer);
        int index = 0;
        while (firstPath.get(index) == secondPath.get(index)) {
            index++;
        }
        Node parent = firstPath.get(index - 1);
        return Integer.compare(Node.indexInParent(firstPath.get(index)),
                Node.indexInParent(secondPath.get(index)));
    }

    private static List<Node> pathFromRoot(Node node) {
        List<Node> path = new ArrayList<>();
        for (Node current = node; current != null; current = current.getParent()) {
            path.add(current);
        }
        Collections.reverse(path);
        return path;
    }

    private static boolean isCharacterData(Node node) {
        return node instanceof TextNode || node instanceof CommentNode;
    }

    private static String characterData(Node node) {
        if (node instanceof TextNode text) {
            return text.getData();
        }
        if (node instanceof CommentNode comment) {
            return comment.getData();
        }
        throw new IllegalArgumentException("Knoten enthält keine CharacterData");
    }

    private static Node characterDataClone(Node original, String data) {
        return original instanceof TextNode ? new TextNode(data) : new CommentNode(data);
    }

    private static void replaceCharacterData(Node node, int offset, int count, String replacement) {
        String oldData = characterData(node);
        int end = Math.min(offset + count, oldData.length());
        String newData = oldData.substring(0, offset) + replacement + oldData.substring(end);
        characterDataReplaced(node, offset, end - offset, replacement.length());
        if (node instanceof TextNode text) {
            text.setDataWithoutRangeAdjustment(newData);
        } else {
            ((CommentNode) node).setDataWithoutRangeAdjustment(newData);
        }
    }

    private static Node shallowClone(Node node) {
        if (node instanceof Element element) {
            return new Element(element.getTagName(), element.getAttributes());
        }
        if (node instanceof TextNode text) {
            return new TextNode(text.getData());
        }
        if (node instanceof CommentNode comment) {
            return new CommentNode(comment.getData());
        }
        if (node instanceof DocumentFragment) {
            return new DocumentFragment();
        }
        if (node instanceof DocumentType documentType) {
            return new DocumentType(documentType.getName());
        }
        throw new IllegalArgumentException("Knotentyp kann nicht geklont werden: " + node.getNodeName());
    }

    private static Node deepClone(Node node) {
        Node clone = shallowClone(node);
        for (Node child : node.getChildren()) {
            clone.appendChild(deepClone(child));
        }
        return clone;
    }

    private void ensureActive() {
        // Kept at call sites to document operations that require a live Range.
    }

    private static List<Range> activeSnapshot() {
        synchronized (ACTIVE_RANGES) {
            return List.copyOf(ACTIVE_RANGES.keySet());
        }
    }

    static void nodeInserted(Node parent, int index, int count) {
        for (Range range : activeSnapshot()) {
            range.adjustForInsertion(parent, index, count);
        }
    }

    static void nodeRemoved(Node parent, int index, Node removed) {
        for (Range range : activeSnapshot()) {
            range.adjustForRemoval(parent, index, removed);
        }
    }

    static void characterDataReset(Node node, int newLength) {
        for (Range range : activeSnapshot()) {
            if (range.startContainer == node && range.startOffset > newLength) {
                range.startOffset = newLength;
            }
            if (range.endContainer == node && range.endOffset > newLength) {
                range.endOffset = newLength;
            }
        }
    }

    static void characterDataReplaced(Node node, int offset, int removedLength, int insertedLength) {
        for (Range range : activeSnapshot()) {
            range.startOffset = adjustedCharacterOffset(
                    range.startContainer, range.startOffset, node,
                    offset, removedLength, insertedLength);
            range.endOffset = adjustedCharacterOffset(
                    range.endContainer, range.endOffset, node,
                    offset, removedLength, insertedLength);
        }
    }

    static void textSplit(TextNode original, TextNode tail, int offset,
                          Node parent, int originalIndex) {
        for (Range range : activeSnapshot()) {
            if (range.startContainer == original && range.startOffset > offset) {
                range.startContainer = tail;
                range.startOffset -= offset;
            }
            if (range.endContainer == original && range.endOffset > offset) {
                range.endContainer = tail;
                range.endOffset -= offset;
            }
            if (parent != null) {
                if (range.startContainer == parent && range.startOffset >= originalIndex + 1) {
                    range.startOffset++;
                }
                if (range.endContainer == parent && range.endOffset >= originalIndex + 1) {
                    range.endOffset++;
                }
            }
        }
    }

    private static int adjustedCharacterOffset(Node container, int currentOffset, Node changedNode,
                                               int offset, int removedLength, int insertedLength) {
        if (container != changedNode || currentOffset <= offset) {
            return currentOffset;
        }
        int removedEnd = offset + removedLength;
        if (currentOffset <= removedEnd) {
            return offset + insertedLength;
        }
        return currentOffset + insertedLength - removedLength;
    }

    private void adjustForInsertion(Node parent, int index, int count) {
        if (startContainer == parent && startOffset > index) {
            startOffset += count;
        }
        if (endContainer == parent && endOffset > index) {
            endOffset += count;
        }
    }

    private void adjustForRemoval(Node parent, int index, Node removed) {
        if (removed.contains(startContainer)) {
            startContainer = parent;
            startOffset = index;
        } else if (startContainer == parent && startOffset > index) {
            startOffset--;
        }
        if (removed.contains(endContainer)) {
            endContainer = parent;
            endOffset = index;
        } else if (endContainer == parent && endOffset > index) {
            endOffset--;
        }
    }
}

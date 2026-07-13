package com.browicy.engine.dom;

import lombok.Getter;

public final class TextNode extends Node {

    @Getter
    private String data;

    public TextNode(String data) {
        this.data = data == null ? "" : data;
    }

    @Override public short getNodeType() { return TEXT_NODE; }
    @Override public String getNodeName() { return "#text"; }
    @Override public String getNodeValue() { return data; }
    @Override public void setNodeValue(String value) { setData(value); }

    @Override
    public String getTextContent() {
        return data;
    }

    @Override
    public void setTextContent(String text) {
        setData(text);
    }

    public void setData(String data) {
        String normalized = data == null ? "" : data;
        this.data = normalized;
        Range.characterDataReset(this, normalized.length());
    }

    void setDataWithoutRangeAdjustment(String data) {
        this.data = data == null ? "" : data;
    }

    public TextNode splitText(int offset) {
        if (offset < 0 || offset > data.length()) {
            throw new IndexOutOfBoundsException("Text-Offset liegt außerhalb des Knotens");
        }
        TextNode tail = new TextNode(data.substring(offset));
        Node parent = getParent();
        int index = parent == null ? -1 : Node.indexInParent(this);
        Range.textSplit(this, tail, offset, parent, index);
        data = data.substring(0, offset);
        if (parent != null) {
            parent.insertBeforeWithoutRangeAdjustment(tail, getNextSibling());
        }
        return tail;
    }

    public boolean isBlank() {
        return data.isBlank();
    }

    @Override
    public String toString() {
        return "#text(" + data + ")";
    }
}

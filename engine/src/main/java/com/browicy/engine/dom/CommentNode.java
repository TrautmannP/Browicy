package com.browicy.engine.dom;

import lombok.Getter;

public final class CommentNode extends Node {
    @Getter
    private String data;

    public CommentNode(String data) {
        this.data = data == null ? "" : data;
    }

    public void setData(String data) {
        String normalized = data == null ? "" : data;
        this.data = normalized;
        Range.characterDataReset(this, normalized.length());
    }

    void setDataWithoutRangeAdjustment(String data) {
        this.data = data == null ? "" : data;
    }

    @Override public short getNodeType() { return COMMENT_NODE; }
    @Override public String getNodeName() { return "#comment"; }
    @Override public String getNodeValue() { return data; }
    @Override public void setNodeValue(String value) { setData(value); }
    @Override public String getTextContent() { return data; }
    @Override public void setTextContent(String text) { setData(text); }
}

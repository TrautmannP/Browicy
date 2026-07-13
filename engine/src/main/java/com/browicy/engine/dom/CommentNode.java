package com.browicy.engine.dom;

public final class CommentNode extends Node {
    private String data;

    public CommentNode(String data) {
        this.data = data == null ? "" : data;
    }

    public String getData() { return data; }
    public void setData(String data) { this.data = data == null ? "" : data; }
    @Override public short getNodeType() { return COMMENT_NODE; }
    @Override public String getNodeName() { return "#comment"; }
    @Override public String getNodeValue() { return data; }
    @Override public void setNodeValue(String value) { setData(value); }
    @Override public String getTextContent() { return data; }
    @Override public void setTextContent(String text) { setData(text); }
}

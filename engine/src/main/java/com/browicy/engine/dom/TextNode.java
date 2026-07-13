package com.browicy.engine.dom;

/**
 * Ein Textknoten zwischen bzw. innerhalb von Elementen.
 */
public final class TextNode extends Node {

    private String data;

    public TextNode(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
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
        this.data = data == null ? "" : data;
    }

    /**
     * {@code true}, wenn der Text nur aus Whitespace besteht
     * (typischerweise Formatierung des HTML-Quelltexts).
     */
    public boolean isBlank() {
        return data.isBlank();
    }

    @Override
    public String toString() {
        return "#text(" + data + ")";
    }
}

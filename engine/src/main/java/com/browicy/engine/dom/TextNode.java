package com.browicy.engine.dom;

/**
 * Ein Textknoten zwischen bzw. innerhalb von Elementen.
 */
public final class TextNode extends Node {

    private final String data;

    public TextNode(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
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

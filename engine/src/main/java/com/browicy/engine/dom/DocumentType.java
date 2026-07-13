package com.browicy.engine.dom;

public final class DocumentType extends Node {
    private final String name;

    public DocumentType(String declaration) {
        String value = declaration == null ? "" : declaration.strip();
        int separator = value.indexOf(' ');
        this.name = (separator < 0 ? value : value.substring(0, separator)).toLowerCase(java.util.Locale.ROOT);
    }

    public String getName() { return name; }
    @Override public short getNodeType() { return DOCUMENT_TYPE_NODE; }
    @Override public String getNodeName() { return name; }
}

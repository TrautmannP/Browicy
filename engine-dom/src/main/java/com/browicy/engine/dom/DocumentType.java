package com.browicy.engine.dom;

import lombok.Getter;

@Getter
public final class DocumentType extends Node {
    private final String name;
    private final String publicId;
    private final String systemId;

    public DocumentType(String declaration) {
        String value = declaration == null ? "" : declaration.strip();
        int separator = value.indexOf(' ');
        this.name = (separator < 0 ? value : value.substring(0, separator)).toLowerCase(java.util.Locale.ROOT);
        this.publicId = "";
        this.systemId = "";
    }

    public DocumentType(String name, String publicId, String systemId) {
        this.name = name;
        this.publicId = publicId == null ? "" : publicId;
        this.systemId = systemId == null ? "" : systemId;
    }

    @Override public short getNodeType() { return DOCUMENT_TYPE_NODE; }
    @Override public String getNodeName() { return name; }
}

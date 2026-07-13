package com.browicy.engine.dom;

public final class DocumentFragment extends Node implements ParentNode {
    @Override public short getNodeType() { return DOCUMENT_FRAGMENT_NODE; }
    @Override public String getNodeName() { return "#document-fragment"; }
}

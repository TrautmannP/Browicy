package com.browicy.engine.js.handlers;

import com.browicy.engine.dom.Node;

import java.util.Map;
import java.util.Set;

public final class JsNodeConstants {

    private static final Map<String, Integer> VALUES = Map.ofEntries(
            Map.entry("ELEMENT_NODE", (int) Node.ELEMENT_NODE),
            Map.entry("TEXT_NODE", (int) Node.TEXT_NODE),
            Map.entry("COMMENT_NODE", (int) Node.COMMENT_NODE),
            Map.entry("DOCUMENT_NODE", (int) Node.DOCUMENT_NODE),
            Map.entry("DOCUMENT_TYPE_NODE", (int) Node.DOCUMENT_TYPE_NODE),
            Map.entry("DOCUMENT_FRAGMENT_NODE", (int) Node.DOCUMENT_FRAGMENT_NODE),
            Map.entry("DOCUMENT_POSITION_DISCONNECTED", (int) Node.DOCUMENT_POSITION_DISCONNECTED),
            Map.entry("DOCUMENT_POSITION_PRECEDING", (int) Node.DOCUMENT_POSITION_PRECEDING),
            Map.entry("DOCUMENT_POSITION_FOLLOWING", (int) Node.DOCUMENT_POSITION_FOLLOWING),
            Map.entry("DOCUMENT_POSITION_CONTAINS", (int) Node.DOCUMENT_POSITION_CONTAINS),
            Map.entry("DOCUMENT_POSITION_CONTAINED_BY", (int) Node.DOCUMENT_POSITION_CONTAINED_BY),
            Map.entry("DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC",
                    (int) Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC));

    private JsNodeConstants() {
    }

    public static Integer valueOf(String key) {
        return VALUES.get(key);
    }

    public static Set<String> keys() {
        return VALUES.keySet();
    }
}

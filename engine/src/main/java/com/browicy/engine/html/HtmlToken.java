package com.browicy.engine.html;

import java.util.Map;

/**
 * Ein Token des HTML-Tokenizers. Vereinfachtes Abbild der Token-Typen
 * aus der HTML-Spezifikation (Start-Tag, End-Tag, Text, Kommentar, Doctype).
 */
public record HtmlToken(
        Type type,
        String name,
        String data,
        Map<String, String> attributes,
        boolean selfClosing
) {

    public enum Type {
        START_TAG,
        END_TAG,
        TEXT,
        COMMENT,
        DOCTYPE
    }

    public static HtmlToken startTag(String name, Map<String, String> attributes, boolean selfClosing) {
        return new HtmlToken(Type.START_TAG, name.toLowerCase(), null, attributes, selfClosing);
    }

    public static HtmlToken endTag(String name) {
        return new HtmlToken(Type.END_TAG, name.toLowerCase(), null, Map.of(), false);
    }

    public static HtmlToken text(String data) {
        return new HtmlToken(Type.TEXT, null, data, Map.of(), false);
    }

    public static HtmlToken comment(String data) {
        return new HtmlToken(Type.COMMENT, null, data, Map.of(), false);
    }

    public static HtmlToken doctype(String data) {
        return new HtmlToken(Type.DOCTYPE, null, data, Map.of(), false);
    }
}

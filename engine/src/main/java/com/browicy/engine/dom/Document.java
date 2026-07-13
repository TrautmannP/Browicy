package com.browicy.engine.dom;

import java.util.ArrayList;
import java.util.List;

/**
 * Wurzel eines geparsten HTML-Dokuments. Bietet bequemen Zugriff auf
 * {@code <html>}, {@code <head>}, {@code <body>} und den Titel.
 */
public final class Document extends Node {

    private final String url;

    public Document(String url) {
        this.url = url;
    }

    /**
     * Die URL, unter der das Dokument geladen wurde (informativ, z. B. für die Adressleiste).
     */
    public String getUrl() {
        return url;
    }

    @Override public short getNodeType() { return DOCUMENT_NODE; }
    @Override public String getNodeName() { return "#document"; }

    public Element createElement(String name) { return new Element(name); }
    public TextNode createTextNode(String data) { return new TextNode(data); }
    public CommentNode createComment(String data) { return new CommentNode(data); }
    public DocumentFragment createDocumentFragment() { return new DocumentFragment(); }

    public Element getDocumentElement() {
        return findFirst("html");
    }

    /**
     * Liefert das {@code <body>}-Element. Fehlt es (Fragment ohne Grundgerüst),
     * wird ersatzweise {@code <html>} bzw. das erste Wurzelelement geliefert,
     * damit Aufrufer immer rendern können.
     */
    public Element getBody() {
        Element body = findFirst("body");
        if (body != null) {
            return body;
        }
        Element html = getDocumentElement();
        if (html != null) {
            return html;
        }
        for (Node child : getChildren()) {
            if (child instanceof Element element) {
                return element;
            }
        }
        return null;
    }

    /**
     * Inhalt des {@code <title>}-Elements, oder ein leerer String, wenn keiner vorhanden ist.
     */
    public String getTitle() {
        Element title = findFirst("title");
        return title == null ? "" : title.getTextContent().strip();
    }

    /**
     * Sucht in Dokumentreihenfolge das erste Element mit dem angegebenen
     * {@code id}-Attribut, oder {@code null}.
     */
    public Element getElementById(String id) {
        if (id == null) {
            return null;
        }
        for (Element element : collectElements(null)) {
            if (id.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        return null;
    }

    /**
     * Liefert alle Elemente mit dem angegebenen Tag-Namen in Dokumentreihenfolge.
     */
    public List<Element> getElementsByTagName(String tag) {
        return collectElements(tag.toLowerCase());
    }

    /** Sammelt Elemente in Dokumentreihenfolge; {@code tag == null} sammelt alle. */
    private List<Element> collectElements(String tag) {
        List<Element> result = new ArrayList<>();
        collect(this, tag, result);
        return result;
    }

    private static void collect(Node node, String tag, List<Element> result) {
        for (Node child : node.getChildren()) {
            if (child instanceof Element element) {
                if (tag == null || element.getTagName().equals(tag)) {
                    result.add(element);
                }
                collect(element, tag, result);
            }
        }
    }

    private Element findFirst(String tag) {
        for (Node child : getChildren()) {
            if (child instanceof Element element) {
                Element found = element.findFirst(tag);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "#document(" + url + ")";
    }
}

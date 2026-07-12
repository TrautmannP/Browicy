package com.browicy.engine.dom;

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

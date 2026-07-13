package com.browicy.engine.dom;

import java.util.List;

/**
 * Gemeinsame Abfrage-API für Knotentypen, die Elementkinder enthalten können.
 * Die Ergebnisse werden in Dokumentreihenfolge als statischer Snapshot geliefert.
 */
public sealed interface ParentNode permits Document, DocumentFragment, Element {

    default Element querySelector(String selectors) {
        return SelectorQueries.querySelector((Node) this, selectors);
    }

    default List<Element> querySelectorAll(String selectors) {
        return SelectorQueries.querySelectorAll((Node) this, selectors);
    }
}

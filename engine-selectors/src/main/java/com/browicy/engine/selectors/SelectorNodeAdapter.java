package com.browicy.engine.selectors;

/**
 * Minimale Sicht des Selektor-Matchers auf einen Elementbaum.
 * Dadurch bleibt dieses Modul unabhängig von einer konkreten DOM-Implementierung.
 */
public interface SelectorNodeAdapter<N> {

    /** Liefert das direkte Elternelement oder {@code null}. */
    N parentElement(N element);

    /** Prüft einen Typselektor unter Beachtung der DOM-spezifischen Großschreibung. */
    boolean matchesType(N element, String typeName);

    String id(N element);

    boolean hasClass(N element, String className);
}

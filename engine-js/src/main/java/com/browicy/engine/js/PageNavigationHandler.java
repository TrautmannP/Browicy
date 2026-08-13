package com.browicy.engine.js;

/**
 * Empfängt Navigationswünsche aus dem JavaScript-Bootstrap
 * ({@code location.replace/assign/reload}, {@code location.href}-Setter,
 * {@code window.open}). Implementierungen müssen nicht blockieren: Die
 * Navigation wird aus dem Page-Runtime-Event-Loop heraus aufgerufen.
 */
@FunctionalInterface
public interface PageNavigationHandler {

    PageNavigationHandler NO_OP = (url, replace) -> { };

    /**
     * @param url     die (gegen die Dokument-URL aufgelöste) Ziel-URL
     * @param replace {@code true} für {@code location.replace} und
     *                {@code location.reload}, {@code false} für {@code assign}
     *                und {@code href}-Zuweisung
     */
    void navigate(String url, boolean replace);
}

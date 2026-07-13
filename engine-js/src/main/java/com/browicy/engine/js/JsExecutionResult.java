package com.browicy.engine.js;

import java.util.List;

/**
 * Ergebnis einer JavaScript-Ausführung: gesammelte Konsolenausgaben
 * (z.B. {@code console.log}) und aufgetretene Skriptfehler. Skriptfehler
 * sind wie im Browser nicht fatal — das Dokument bleibt nutzbar.
 */
public record JsExecutionResult(List<String> consoleMessages, List<String> errors) {

    /** Leeres Ergebnis für Seiten ohne Skripte. */
    public static final JsExecutionResult EMPTY = new JsExecutionResult(List.of(), List.of());

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}

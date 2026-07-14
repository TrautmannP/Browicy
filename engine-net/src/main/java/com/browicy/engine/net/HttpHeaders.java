package com.browicy.engine.net;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * HTTP-Header einer Nachricht. Header-Namen sind laut Spezifikation
 * case-insensitiv; die Namen werden intern kleingeschrieben abgelegt,
 * mehrfach gesetzte Header (z.&nbsp;B. {@code Set-Cookie}) bleiben erhalten.
 */
public final class HttpHeaders {

    private final Map<String, List<String>> values;
    private final boolean readOnly;

    public HttpHeaders() {
        this(new LinkedHashMap<>(), false);
    }

    private HttpHeaders(Map<String, List<String>> values, boolean readOnly) {
        this.values = values;
        this.readOnly = readOnly;
    }

    public void add(String name, String value) {
        requireMutable();
        values.computeIfAbsent(normalize(name), key -> new ArrayList<>())
                .add(normalizeValue(value));
    }

    /** Ersetzt alle bisherigen Werte des Headers durch genau einen Wert. */
    public void set(String name, String value) {
        requireMutable();
        List<String> replacement = new ArrayList<>();
        replacement.add(normalizeValue(value));
        values.put(normalize(name), replacement);
    }

    public void remove(String name) {
        requireMutable();
        values.remove(normalize(name));
    }

    /** Erster Wert des Headers oder {@code null}, wenn er nicht gesetzt ist. */
    public String first(String name) {
        List<String> all = values.get(normalize(name));
        return all == null ? null : all.get(0);
    }

    /** Alle Werte des Headers, in Empfangsreihenfolge. */
    public List<String> all(String name) {
        List<String> all = values.get(normalize(name));
        return all == null ? List.of() : List.copyOf(all);
    }

    public boolean contains(String name) {
        return values.containsKey(normalize(name));
    }

    /** Prüft case-insensitiv, ob der Header den angegebenen Wert hat (z.&nbsp;B. {@code chunked}). */
    public boolean hasValue(String name, String expected) {
        String actual = first(name);
        return actual != null && actual.equalsIgnoreCase(expected);
    }

    public Set<String> names() {
        return Collections.unmodifiableSet(values.keySet());
    }

    /** Erstellt eine unabhängige, weiterhin veränderbare Kopie. */
    public HttpHeaders copy() {
        return new HttpHeaders(copyValues(), false);
    }

    /** Erstellt eine unabhängige, unveränderliche Kopie. */
    public HttpHeaders immutableCopy() {
        return new HttpHeaders(copyValues(), true);
    }

    private Map<String, List<String>> copyValues() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        values.forEach((name, entries) -> copy.put(name, new ArrayList<>(entries)));
        return copy;
    }

    private void requireMutable() {
        if (readOnly) {
            throw new UnsupportedOperationException("HTTP-Header sind unveränderlich");
        }
    }

    private static String normalize(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        String normalized = name.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("HTTP-Headername darf nicht leer sein");
        }
        return normalized;
    }

    private static String normalizeValue(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        return value.strip();
    }
}

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

    private final Map<String, List<String>> values = new LinkedHashMap<>();

    public void add(String name, String value) {
        values.computeIfAbsent(normalize(name), key -> new ArrayList<>()).add(value.strip());
    }

    /** Erster Wert des Headers oder {@code null}, wenn er nicht gesetzt ist. */
    public String first(String name) {
        List<String> all = values.get(normalize(name));
        return all == null ? null : all.get(0);
    }

    /** Alle Werte des Headers, in Empfangsreihenfolge. */
    public List<String> all(String name) {
        return values.getOrDefault(normalize(name), List.of());
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

    private static String normalize(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}

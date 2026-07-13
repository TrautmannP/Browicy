package com.browicy.engine.selectors;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Zusammengesetzter Selektor ohne DOM-Beziehung, etwa
 * {@code div.card.active#main}, {@code .notice} oder {@code *}.
 */
public record CompoundSelector(String typeName, String id, List<String> classes) {

    public CompoundSelector {
        classes = List.copyOf(Objects.requireNonNull(classes, "classes"));
        if (typeName != null && typeName.isBlank()) {
            throw new IllegalArgumentException("Der Elementname darf nicht leer sein");
        }
        if (id != null && id.isBlank()) {
            throw new IllegalArgumentException("Die ID darf nicht leer sein");
        }
        if (classes.stream().anyMatch(className -> className == null || className.isBlank())) {
            throw new IllegalArgumentException("Klassennamen dürfen nicht leer sein");
        }
        if (typeName == null && id == null && classes.isEmpty()) {
            throw new IllegalArgumentException("Ein Selektor benötigt mindestens einen Bestandteil");
        }
    }

    public Specificity specificity() {
        return new Specificity(id == null ? 0 : 1, classes.size(),
                typeName == null || "*".equals(typeName) ? 0 : 1);
    }

    <N> boolean matches(N element, SelectorNodeAdapter<N> adapter) {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(adapter, "adapter");
        if (typeName != null && !"*".equals(typeName)
                && !adapter.matchesType(element, typeName)) {
            return false;
        }
        if (id != null && !id.equals(adapter.id(element))) {
            return false;
        }
        for (String className : classes) {
            if (!adapter.hasClass(element, className)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (typeName != null) {
            result.append(typeName.indexOf(':') < 0
                    ? typeName.toLowerCase(Locale.ROOT)
                    : typeName);
        }
        for (String className : classes) {
            result.append('.').append(className);
        }
        if (id != null) {
            result.append('#').append(id);
        }
        return result.toString();
    }
}

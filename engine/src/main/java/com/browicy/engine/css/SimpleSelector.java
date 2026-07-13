package com.browicy.engine.css;

import com.browicy.engine.dom.Element;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Zusammengesetzter einfacher Selektor ohne DOM-Beziehungen, zum Beispiel
 * {@code div.card.active#main}, {@code .notice} oder {@code *}.
 */
public record SimpleSelector(String tagName, String id, List<String> classes)
        implements CssSelector {

    public SimpleSelector {
        tagName = tagName == null ? null : tagName.toLowerCase(Locale.ROOT);
        classes = List.copyOf(Objects.requireNonNull(classes, "classes"));
        if (tagName != null && tagName.isBlank()) {
            throw new IllegalArgumentException("Der Elementname darf nicht leer sein");
        }
        if (id != null && id.isBlank()) {
            throw new IllegalArgumentException("Die ID darf nicht leer sein");
        }
        if (classes.stream().anyMatch(className -> className == null || className.isBlank())) {
            throw new IllegalArgumentException("Klassennamen dürfen nicht leer sein");
        }
        if (tagName == null && id == null && classes.isEmpty()) {
            throw new IllegalArgumentException("Ein Selektor benötigt mindestens einen Bestandteil");
        }
    }

    @Override
    public boolean matches(Element element) {
        Objects.requireNonNull(element, "element");
        if (tagName != null && !"*".equals(tagName)
                && !tagName.equals(element.getTagName())) {
            return false;
        }
        if (id != null && !id.equals(element.getId())) {
            return false;
        }
        for (String className : classes) {
            if (!element.hasClass(className)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Specificity specificity() {
        return new Specificity(id == null ? 0 : 1, classes.size(),
                tagName == null || "*".equals(tagName) ? 0 : 1);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (tagName != null) {
            result.append(tagName);
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

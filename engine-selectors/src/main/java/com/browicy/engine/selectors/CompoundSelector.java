package com.browicy.engine.selectors;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record CompoundSelector(String typeName, String id, List<String> classes,
                               List<AttributeSelector> attributes,
                               List<StructuralPseudoClass> pseudoClasses,
                               List<CompoundSelector> negations) {

    public CompoundSelector(String typeName, String id, List<String> classes) {
        this(typeName, id, classes, List.of(), List.of(), List.of());
    }

    public CompoundSelector(String typeName, String id, List<String> classes,
                            List<AttributeSelector> attributes,
                            List<StructuralPseudoClass> pseudoClasses) {
        this(typeName, id, classes, attributes, pseudoClasses, List.of());
    }

    public CompoundSelector {
        classes = List.copyOf(Objects.requireNonNull(classes, "classes"));
        attributes = List.copyOf(Objects.requireNonNull(attributes, "attributes"));
        pseudoClasses = List.copyOf(Objects.requireNonNull(pseudoClasses, "pseudoClasses"));
        negations = List.copyOf(Objects.requireNonNull(negations, "negations"));
        if (typeName != null && typeName.isBlank()) {
            throw new IllegalArgumentException("Der Elementname darf nicht leer sein");
        }
        if (id != null && id.isBlank()) {
            throw new IllegalArgumentException("Die ID darf nicht leer sein");
        }
        if (classes.stream().anyMatch(className -> className == null || className.isBlank())) {
            throw new IllegalArgumentException("Klassennamen dürfen nicht leer sein");
        }
        if (typeName == null && id == null && classes.isEmpty()
                && attributes.isEmpty() && pseudoClasses.isEmpty() && negations.isEmpty()) {
            throw new IllegalArgumentException("Ein Selektor benötigt mindestens einen Bestandteil");
        }
    }

    public Specificity specificity() {
        Specificity result = new Specificity(id == null ? 0 : 1,
                classes.size() + attributes.size() + pseudoClasses.size(),
                typeName == null || "*".equals(typeName) ? 0 : 1);
        for (CompoundSelector negation : negations) {
            result = result.add(negation.specificity());
        }
        return result;
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
        for (AttributeSelector attribute : attributes) {
            if (!attribute.matches(element, adapter)) {
                return false;
            }
        }
        for (StructuralPseudoClass pseudoClass : pseudoClasses) {
            if (!pseudoClass.matches(element, adapter)) {
                return false;
            }
        }
        for (CompoundSelector negation : negations) {
            if (negation.matches(element, adapter)) {
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
        for (AttributeSelector attribute : attributes) {
            result.append(attribute);
        }
        for (StructuralPseudoClass pseudoClass : pseudoClasses) {
            result.append(pseudoClass);
        }
        for (CompoundSelector negation : negations) {
            result.append(":not(").append(negation).append(')');
        }
        return result.toString();
    }
}

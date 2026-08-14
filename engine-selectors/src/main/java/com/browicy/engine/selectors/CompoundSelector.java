package com.browicy.engine.selectors;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Ein zusammengesetzter Selektor ohne Kombinatoren.
 *
 * <p>{@code typeNamespace} ist {@code null} für Typselektoren ohne
 * Namespace-Angabe (matcht jeden Namespace), {@code "*"} für {@code *|E}
 * (ebenfalls jeder Namespace), {@code ""} für {@code |E} (kein Namespace)
 * und sonst das Präfix aus {@code prefix|E} (matcht nur Namespace-Elemente;
 * die Präfix-auflösung über {@code @namespace} ist nicht implementiert).</p>
 */
public record CompoundSelector(String typeNamespace, String typeName, String id,
                               List<String> classes,
                               List<AttributeSelector> attributes,
                               List<StructuralPseudoClass> pseudoClasses,
                               List<String> statePseudoClasses,
                               List<PseudoClassFunction> functions,
                               String pseudoElement) {

    public CompoundSelector(String typeName, String id, List<String> classes) {
        this(null, typeName, id, classes, List.of(), List.of(), List.of(), List.of(), null);
    }

    public CompoundSelector(String typeName, String id, List<String> classes,
                            List<AttributeSelector> attributes,
                            List<StructuralPseudoClass> pseudoClasses) {
        this(null, typeName, id, classes, attributes, pseudoClasses, List.of(), List.of(), null);
    }

    public CompoundSelector {
        classes = List.copyOf(Objects.requireNonNull(classes, "classes"));
        attributes = List.copyOf(Objects.requireNonNull(attributes, "attributes"));
        pseudoClasses = List.copyOf(Objects.requireNonNull(pseudoClasses, "pseudoClasses"));
        statePseudoClasses = List.copyOf(
                Objects.requireNonNull(statePseudoClasses, "statePseudoClasses"));
        functions = List.copyOf(Objects.requireNonNull(functions, "functions"));
        if (pseudoElement != null && !pseudoElement.equals("before")
                && !pseudoElement.equals("after")
                && !pseudoElement.equals("first-letter")
                && !pseudoElement.equals("first-line")) {
            throw new IllegalArgumentException("Nicht unterstütztes Pseudoelement: " + pseudoElement);
        }
        if (typeNamespace != null && !typeNamespace.isEmpty() && typeNamespace.isBlank()) {
            throw new IllegalArgumentException("Der Typ-Namespace darf nicht leer sein");
        }
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
                && attributes.isEmpty() && pseudoClasses.isEmpty()
                && statePseudoClasses.isEmpty() && functions.isEmpty() && pseudoElement == null) {
            throw new IllegalArgumentException("Ein Selektor benötigt mindestens einen Bestandteil");
        }
    }

    public Specificity specificity() {
        Specificity result = new Specificity(id == null ? 0 : 1,
                classes.size() + attributes.size() + pseudoClasses.size()
                        + statePseudoClasses.size(),
                typeName == null || "*".equals(typeName) ? 0 : 1);
        if (pseudoElement != null) result = result.add(new Specificity(0, 0, 1));
        for (PseudoClassFunction function : functions) {
            result = result.add(function.specificity());
        }
        return result;
    }

    <N> boolean matches(N element, SelectorNodeAdapter<N> adapter) {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(adapter, "adapter");
        if (typeName != null) {
            if (!"*".equals(typeName) && !adapter.matchesType(element, typeName)) {
                return false;
            }
            if (typeNamespace != null) {
                if ("*".equals(typeNamespace)) {
                    // *|E: any namespace (including none) - already satisfied
                } else if ("".equals(typeNamespace)) {
                    if (adapter.namespaceUri(element) != null) {
                        return false;
                    }
                } else if (adapter.namespaceUri(element) == null) {
                    return false;
                }
            }
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
        for (String state : statePseudoClasses) {
            if (!adapter.matchesState(element, state)) return false;
        }
        for (PseudoClassFunction function : functions) {
            if (!function.matches(element, adapter)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (typeName != null) {
            if (typeNamespace != null) {
                result.append(typeNamespace).append('|');
            }
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
        for (String state : statePseudoClasses) result.append(':').append(state);
        for (PseudoClassFunction function : functions) {
            result.append(function);
        }
        if (pseudoElement != null) result.append("::").append(pseudoElement);
        return result.toString();
    }
}

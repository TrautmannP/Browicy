package com.browicy.engine.selectors;

import java.util.List;
import java.util.Objects;

public record ComplexSelector(List<SelectorStep> steps) implements Selector {

    public ComplexSelector {
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("Ein komplexer Selektor benötigt mindestens einen Teil");
        }
        if (steps.getFirst().relationToPrevious() != null) {
            throw new IllegalArgumentException("Der erste Selektorteil darf keinen Kombinator besitzen");
        }
        for (int index = 1; index < steps.size(); index++) {
            if (steps.get(index).relationToPrevious() == null) {
                throw new IllegalArgumentException(
                        "Jeder weitere Selektorteil benötigt einen Kombinator");
            }
        }
    }

    @Override
    public Specificity specificity() {
        Specificity result = Specificity.ZERO;
        for (SelectorStep step : steps) {
            result = result.add(step.selector().specificity());
        }
        return result;
    }

    @Override
    public <N> boolean matches(N element, SelectorNodeAdapter<N> adapter) {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(adapter, "adapter");
        return matchesStep(element, steps.size() - 1, adapter);
    }

    @Override
    public String pseudoElement() {
        return steps.getLast().selector().pseudoElement();
    }

    private <N> boolean matchesStep(N element, int index, SelectorNodeAdapter<N> adapter) {
        SelectorStep step = steps.get(index);
        if (!step.selector().matches(element, adapter)) {
            return false;
        }
        if (index == 0) {
            return true;
        }

        if (step.relationToPrevious() == Combinator.ADJACENT_SIBLING) {
            N sibling = adapter.previousElementSibling(element);
            return sibling != null && matchesStep(sibling, index - 1, adapter);
        }
        if (step.relationToPrevious() == Combinator.GENERAL_SIBLING) {
            N sibling = adapter.previousElementSibling(element);
            while (sibling != null) {
                if (matchesStep(sibling, index - 1, adapter)) {
                    return true;
                }
                sibling = adapter.previousElementSibling(sibling);
            }
            return false;
        }

        N parent = adapter.parentElement(element);
        if (step.relationToPrevious() == Combinator.CHILD) {
            return parent != null && matchesStep(parent, index - 1, adapter);
        }

        while (parent != null) {
            if (matchesStep(parent, index - 1, adapter)) {
                return true;
            }
            parent = adapter.parentElement(parent);
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(steps.getFirst().selector().toString());
        for (int index = 1; index < steps.size(); index++) {
            SelectorStep step = steps.get(index);
            if (step.relationToPrevious() == Combinator.DESCENDANT) {
                result.append(' ');
            } else {
                result.append(' ').append(step.relationToPrevious().css()).append(' ');
            }
            result.append(step.selector());
        }
        return result.toString();
    }
}

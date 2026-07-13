package com.browicy.engine.selectors;

import java.util.Objects;

/** Ein Teil eines komplexen Selektors und seine Beziehung zum vorherigen Teil. */
public record SelectorStep(CompoundSelector selector, Combinator relationToPrevious) {

    public SelectorStep {
        Objects.requireNonNull(selector, "selector");
    }
}

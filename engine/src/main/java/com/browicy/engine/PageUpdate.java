package com.browicy.engine;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.DomMutation;
import java.net.URI;
import java.util.List;
import java.util.Objects;

public sealed interface PageUpdate permits PageUpdate.DocumentChanged, PageUpdate.StylesChanged {

    Document document();

    InvalidationType invalidation();

    List<DomMutation> mutations();

    record DocumentChanged(Document document,
                           InvalidationType invalidation,
                           List<DomMutation> mutations) implements PageUpdate {
        public DocumentChanged {
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(invalidation, "invalidation");
            mutations = List.copyOf(mutations);
        }
    }

    record StylesChanged(Document document,
                         InvalidationType invalidation,
                         List<URI> stylesheets,
                         List<DomMutation> mutations) implements PageUpdate {
        public StylesChanged {
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(invalidation, "invalidation");
            stylesheets = List.copyOf(stylesheets);
            mutations = List.copyOf(mutations);
        }

        public StylesChanged(Document document, List<URI> stylesheets) {
            this(document, InvalidationType.STYLE, stylesheets, List.of());
        }
    }
}

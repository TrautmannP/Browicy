package com.browicy.engine;

import com.browicy.engine.dom.Document;
import java.net.URI;
import java.util.List;

public sealed interface PageUpdate {

    Document document();

    record StylesChanged(Document document, List<URI> stylesheets) implements PageUpdate {
        public StylesChanged {
            stylesheets = List.copyOf(stylesheets);
        }
    }
}

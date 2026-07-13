package com.browicy.engine.html;

import com.browicy.engine.dom.Element;
import java.net.URI;
import java.util.Objects;

public record ImageResource(Element element, URI uri) {
    public ImageResource {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(uri, "uri");
    }
}

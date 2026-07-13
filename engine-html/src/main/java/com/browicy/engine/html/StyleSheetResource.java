package com.browicy.engine.html;

import com.browicy.engine.dom.Element;
import java.net.URI;
import java.util.Objects;

public sealed interface StyleSheetResource {

    int sourceOrder();

    Element element();

    record Inline(int sourceOrder, Element element, String css) implements StyleSheetResource {
        public Inline {
            Objects.requireNonNull(element, "element");
            css = css == null ? "" : css;
        }
    }

    record External(int sourceOrder, Element element, URI uri, boolean renderBlocking)
            implements StyleSheetResource {
        public External {
            Objects.requireNonNull(element, "element");
            Objects.requireNonNull(uri, "uri");
        }
    }
}

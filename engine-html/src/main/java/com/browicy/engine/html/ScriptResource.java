package com.browicy.engine.html;

import com.browicy.engine.dom.Element;
import java.net.URI;
import java.util.Objects;

public sealed interface ScriptResource {

    int treeOrder();

    Element element();

    boolean async();

    record Inline(int treeOrder, Element element, String code) implements ScriptResource {
        public Inline {
            Objects.requireNonNull(element, "element");
            code = code == null ? "" : code;
        }

        @Override
        public boolean async() {
            return false;
        }
    }

    record External(int treeOrder, Element element, URI uri, boolean async)
            implements ScriptResource {
        public External {
            Objects.requireNonNull(element, "element");
            Objects.requireNonNull(uri, "uri");
        }
    }
}

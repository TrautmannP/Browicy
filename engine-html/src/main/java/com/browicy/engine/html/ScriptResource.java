package com.browicy.engine.html;

import com.browicy.engine.dom.Element;
import java.net.URI;
import java.util.Objects;

public sealed interface ScriptResource {

    int treeOrder();

    Element element();

    boolean async();

    boolean module();

    record Inline(int treeOrder, Element element, String code, boolean module) implements ScriptResource {
        public Inline(int treeOrder, Element element, String code) {
            this(treeOrder, element, code, false);
        }
        public Inline {
            Objects.requireNonNull(element, "element");
            code = code == null ? "" : code;
        }

        @Override
        public boolean async() {
            return false;
        }
    }

    record External(int treeOrder, Element element, URI uri, boolean async, boolean module)
            implements ScriptResource {
        public External(int treeOrder, Element element, URI uri, boolean async) {
            this(treeOrder, element, uri, async, false);
        }
        public External {
            Objects.requireNonNull(element, "element");
            Objects.requireNonNull(uri, "uri");
        }
    }
}

package com.browicy.engine.js;

import com.browicy.engine.dom.Element;
import java.net.URI;
import java.util.Objects;

public record JavaScriptSource(String code, Element element, String sourceName,
                               boolean module, URI moduleUri) {

    public JavaScriptSource {
        code = code == null ? "" : code;
        sourceName = sourceName == null || sourceName.isBlank() ? "script.js" : sourceName;
        Objects.requireNonNull(sourceName, "sourceName");
        if (module && moduleUri == null) {
            throw new IllegalArgumentException("Modul-Skripte benötigen eine Modul-URI");
        }
    }

    public JavaScriptSource(String code, Element element, String sourceName) {
        this(code, element, sourceName, false, null);
    }
}

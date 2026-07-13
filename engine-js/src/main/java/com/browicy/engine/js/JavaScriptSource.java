package com.browicy.engine.js;

import com.browicy.engine.dom.Element;
import java.util.Objects;

public record JavaScriptSource(String code, Element element, String sourceName) {

    public JavaScriptSource {
        code = code == null ? "" : code;
        sourceName = sourceName == null || sourceName.isBlank() ? "script.js" : sourceName;
        Objects.requireNonNull(sourceName, "sourceName");
    }
}

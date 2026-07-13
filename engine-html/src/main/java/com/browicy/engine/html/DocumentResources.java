package com.browicy.engine.html;

import java.util.List;

public record DocumentResources(List<StyleSheetResource> styleSheets,
                                List<ScriptResource> scripts) {

    public DocumentResources {
        styleSheets = List.copyOf(styleSheets);
        scripts = List.copyOf(scripts);
    }
}

package com.browicy.engine.html;

import java.util.List;

public record DocumentResources(List<StyleSheetResource> styleSheets,
                                List<ScriptResource> scripts,
                                List<ImageResource> images) {

    public DocumentResources {
        styleSheets = List.copyOf(styleSheets);
        scripts = List.copyOf(scripts);
        images = List.copyOf(images);
    }

    public DocumentResources(List<StyleSheetResource> styleSheets,
                             List<ScriptResource> scripts) {
        this(styleSheets, scripts, List.of());
    }
}

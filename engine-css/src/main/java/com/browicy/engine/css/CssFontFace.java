package com.browicy.engine.css;

import java.util.List;

public record CssFontFace(String family, List<Source> sources, int weight, boolean italic) {
    public CssFontFace {
        sources = List.copyOf(sources);
    }

    public record Source(String url, String format) { }
}

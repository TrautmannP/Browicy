package com.browicy.engine;

import java.awt.Font;
import com.browicy.engine.net.BinaryResource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FontResourceRegistry {
    private final ConcurrentHashMap<String, BinaryResource> resources = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Font> parsedFonts = new ConcurrentHashMap<>();

    public void register(String family, BinaryResource resource, Font font) {
        resources.put(family, resource);
        parsedFonts.put(family, font);
    }

    public Map<String, BinaryResource> resources() {
        return Map.copyOf(new LinkedHashMap<>(resources));
    }

    public Font resolve(String family) {
        if (family == null) return null;
        Font exact = parsedFonts.get(family);
        if (exact != null) return exact;
        for (var entry : parsedFonts.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(family)) return entry.getValue();
        }
        return null;
    }
}

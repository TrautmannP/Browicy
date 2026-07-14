package com.browicy.engine;

import com.browicy.engine.net.BinaryResource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FontResourceRegistry {
    private final ConcurrentHashMap<String, BinaryResource> resources = new ConcurrentHashMap<>();

    public void register(String family, BinaryResource resource) {
        resources.put(family, resource);
    }

    public Map<String, BinaryResource> resources() {
        return Map.copyOf(new LinkedHashMap<>(resources));
    }
}

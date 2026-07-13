package com.browicy.engine;

import com.browicy.engine.dom.Element;
import com.browicy.engine.net.BinaryResource;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ImageResourceRegistry {
    private final ConcurrentHashMap<Element, BinaryResource> resources =
            new ConcurrentHashMap<>();

    public void register(Element element, BinaryResource resource) {
        resources.put(element, resource);
    }

    public Optional<BinaryResource> find(Element element) {
        return Optional.ofNullable(resources.get(element));
    }
}

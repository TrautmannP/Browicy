package com.browicy.engine;

import com.browicy.engine.dom.Element;
import com.browicy.engine.net.BinaryResource;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ImageResourceRegistry {
    private final ConcurrentHashMap<Element, BinaryResource> resources =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<URI, BinaryResource> resourcesByUri =
            new ConcurrentHashMap<>();

    public void register(Element element, BinaryResource resource) {
        resources.put(element, resource);
        resourcesByUri.put(resource.uri(), resource);
    }

    public void register(URI uri, BinaryResource resource) {
        resourcesByUri.put(uri, resource);
        resourcesByUri.put(resource.uri(), resource);
    }

    public Optional<BinaryResource> find(Element element) {
        return Optional.ofNullable(resources.get(element));
    }

    public Optional<BinaryResource> find(URI uri) {
        return Optional.ofNullable(resourcesByUri.get(uri));
    }
}

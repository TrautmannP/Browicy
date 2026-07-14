package com.browicy.engine.selectors;

public interface SelectorNodeAdapter<N> {

    N parentElement(N element);

    N previousElementSibling(N element);

    N nextElementSibling(N element);

    String tagName(N element);

    boolean matchesType(N element, String typeName);

    String id(N element);

    boolean hasClass(N element, String className);

    boolean hasAttribute(N element, String name);

    String attributeValue(N element, String name);

    default boolean matchesState(N element, String state) {
        return false;
    }
}

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

    /**
     * Reports whether the element has any child nodes (elements, text, or comments).
     * Adapters without child access default to "non-empty" so {@code :empty}
     * never matches spuriously.
     */
    default boolean hasChildren(N element) {
        return true;
    }

    /** Namespace URI of the element, or {@code null} when it has none. */
    default String namespaceUri(N element) {
        return null;
    }

    /**
     * Namespace URI of the named attribute, or {@code null} when the attribute
     * is not namespaced (the default for HTML documents).
     */
    default String attributeNamespace(N element, String name) {
        return null;
    }
}

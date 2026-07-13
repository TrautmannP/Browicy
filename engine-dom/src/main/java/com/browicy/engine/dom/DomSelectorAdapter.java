package com.browicy.engine.dom;

import com.browicy.engine.selectors.SelectorNodeAdapter;

/** Gemeinsamer Adapter zwischen dem Browicy-DOM und der Selektor-Engine. */
public final class DomSelectorAdapter implements SelectorNodeAdapter<Element> {

    public static final DomSelectorAdapter INSTANCE = new DomSelectorAdapter();

    private DomSelectorAdapter() {
    }

    @Override
    public Element parentElement(Element element) {
        return element.getParent() instanceof Element parent ? parent : null;
    }

    @Override
    public boolean matchesType(Element element, String typeName) {
        return element.getNamespaceUri() == null
                ? typeName.equalsIgnoreCase(element.getTagName())
                : typeName.equals(element.getTagName());
    }

    @Override
    public String id(Element element) {
        return element.getId();
    }

    @Override
    public boolean hasClass(Element element, String className) {
        return element.hasClass(className);
    }
}

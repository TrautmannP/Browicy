package com.browicy.engine.dom;

import com.browicy.engine.selectors.SelectorNodeAdapter;

import java.util.Locale;

public final class DomSelectorAdapter implements SelectorNodeAdapter<Element> {

    public static final DomSelectorAdapter INSTANCE = new DomSelectorAdapter();

    private DomSelectorAdapter() {
    }

    @Override
    public Element parentElement(Element element) {
        return element.getParent() instanceof Element parent ? parent : null;
    }

    @Override
    public Element previousElementSibling(Element element) {
        Node sibling = element.getPreviousSibling();
        while (sibling != null && !(sibling instanceof Element)) {
            sibling = sibling.getPreviousSibling();
        }
        return (Element) sibling;
    }

    @Override
    public Element nextElementSibling(Element element) {
        Node sibling = element.getNextSibling();
        while (sibling != null && !(sibling instanceof Element)) {
            sibling = sibling.getNextSibling();
        }
        return (Element) sibling;
    }

    @Override
    public String tagName(Element element) {
        return element.getTagName();
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

    @Override
    public boolean hasAttribute(Element element, String name) {
        return element.hasAttribute(name);
    }

    @Override
    public String attributeValue(Element element, String name) {
        return element.getAttribute(name);
    }

    @Override
    public boolean matchesState(Element element, String state) {
        return switch (state) {
            case "hover" -> element.isHovered();
            case "checked" -> element.isCheckedState();
            case "focus" -> element.isFocused();
            case "active" -> element.isActive();
            case "disabled" -> isFormControl(element) && isDisabled(element);
            case "enabled" -> isFormControl(element) && !isDisabled(element);
            default -> false;
        };
    }

    private static boolean isFormControl(Element element) {
        return switch (element.getTagName().toLowerCase(Locale.ROOT)) {
            case "button", "input", "select", "textarea",
                 "option", "optgroup", "fieldset" -> true;
            default -> false;
        };
    }

    private static boolean isDisabled(Element element) {
        if (element.hasAttribute("disabled")) {
            return true;
        }
        for (Node ancestor = element.getParent(); ancestor instanceof Element parent;
             ancestor = parent.getParent()) {
            if ("fieldset".equalsIgnoreCase(parent.getTagName())
                    && parent.hasAttribute("disabled")) {
                return true;
            }
        }
        return false;
    }
}

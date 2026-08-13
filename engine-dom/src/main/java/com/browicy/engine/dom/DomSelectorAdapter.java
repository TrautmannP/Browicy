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
        if (element.getNamespaceUri() == null) {
            return typeName.equalsIgnoreCase(element.getTagName());
        }
        return typeName.equals(element.getLocalName());
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
    public boolean hasChildren(Element element) {
        return !element.getChildren().isEmpty();
    }

    @Override
    public String namespaceUri(Element element) {
        return element.getNamespaceUri();
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
            case "link" -> isLinkLike(element) && element.hasAttribute("href");
            case "visited" -> false;
            case "target" -> matchesTarget(element);
            case "indeterminate" -> isIndeterminate(element);
            default -> false;
        };
    }

    private static boolean isLinkLike(Element element) {
        return switch (element.getTagName().toLowerCase(Locale.ROOT)) {
            case "a", "area", "link" -> true;
            default -> false;
        };
    }

    private static boolean matchesTarget(Element element) {
        Document document = element.getOwnerDocument();
        if (document == null) {
            return false;
        }
        String fragment = document.getDocumentUri().getFragment();
        String id = element.getId();
        return fragment != null && !fragment.isEmpty() && fragment.equals(id);
    }

    private static boolean isIndeterminate(Element element) {
        if (element.isIndeterminate()) {
            return true;
        }
        if (!"input".equalsIgnoreCase(element.getTagName())) {
            return false;
        }
        String type = element.getAttribute("type");
        if (!"radio".equalsIgnoreCase(type)) {
            return false;
        }
        if (element.isCheckedState()) {
            return false;
        }
        String name = element.getAttribute("name");
        if (name == null || name.isBlank() || element.getOwnerDocument() == null) {
            return false;
        }
        for (Element candidate : element.getOwnerDocument()
                .getElementsByTagName("input")) {
            if (candidate == element) {
                continue;
            }
            if ("radio".equalsIgnoreCase(candidate.getAttribute("type"))
                    && name.equals(candidate.getAttribute("name"))
                    && candidate.isCheckedState()) {
                return false;
            }
        }
        return true;
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

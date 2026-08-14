package com.browicy.conformance.selector;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Generates the selector contract shared by both conformance extractors. */
public final class LayoutSelector {
    private LayoutSelector() {
    }

    public static String forElement(Element element) {
        if (element == null) {
            throw new IllegalArgumentException("element must not be null");
        }
        List<String> parts = new ArrayList<>();
        Node current = element;
        while (current instanceof Element currentElement) {
            String id = currentElement.getId();
            if (id != null && !id.isBlank() && isUniqueId(currentElement, id)) {
                parts.add("#" + cssEscape(id));
                break;
            }
            Node parent = current.getParent();
            int childIndex = elementIndex(current, parent);
            parts.add(currentElement.getTagName() + ":nth-child(" + childIndex + ")");
            current = parent;
        }
        Collections.reverse(parts);
        return String.join(" > ", parts);
    }

    public static String cssEscape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint) - 1;
            boolean first = escaped.isEmpty();
            if (codePoint == 0) {
                escaped.append('\uFFFD');
            } else if ((first && codePoint >= '0' && codePoint <= '9')
                    || codePoint < 0x20 || codePoint == 0x7F) {
                escaped.append('\\').append(Integer.toHexString(codePoint)).append(' ');
            } else if (!(first && codePoint == '-' && value.length() == 1)
                    && (codePoint == '-' || codePoint == '_'
                    || codePoint >= '0' && codePoint <= '9'
                    || codePoint >= 'A' && codePoint <= 'Z'
                    || codePoint >= 'a' && codePoint <= 'z'
                    || codePoint >= 0x80)) {
                escaped.appendCodePoint(codePoint);
            } else {
                escaped.append('\\').appendCodePoint(codePoint);
            }
        }
        return escaped.toString();
    }

    private static boolean isUniqueId(Element element, String id) {
        Document document = element.getOwnerDocument();
        if (document == null) {
            return true;
        }
        int matches = 0;
        for (Element candidate : document.getElementsByTagName("*")) {
            if (id.equals(candidate.getId()) && ++matches > 1) {
                return false;
            }
        }
        return matches == 1;
    }

    private static int elementIndex(Node node, Node parent) {
        if (parent == null) {
            return 1;
        }
        int index = 0;
        for (Node sibling : parent.getChildren()) {
            if (sibling instanceof Element) {
                index++;
            }
            if (sibling == node) {
                return index;
            }
        }
        throw new IllegalStateException("Element is not attached to its parent");
    }
}

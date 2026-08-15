package com.browicy.engine.html;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.List;

final class DocumentBaseUriResolver {

    private DocumentBaseUriResolver() {
    }

    static void apply(Document document) {
        Element base = firstBaseWithHref(document);
        if (base == null) {
            return;
        }
        String href = base.getAttribute("href");
        if (href == null || href.isBlank()) {
            return;
        }
        try {
            URI resolved = document.getDocumentUri().resolve(href.strip());
            if (resolved.isAbsolute()) {
                document.setBaseUri(resolved);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static Element firstBaseWithHref(Document document) {
        ArrayDeque<Node> pending = new ArrayDeque<>();
        pending.push(document);
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            if (node instanceof Element element
                    && "base".equals(element.getTagName())
                    && element.hasAttribute("href")) {
                return element;
            }
            List<Node> children = node.getChildren();
            for (int index = children.size() - 1; index >= 0; index--) {
                pending.push(children.get(index));
            }
        }
        return null;
    }
}

package com.browicy.engine.html;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import java.net.URI;

final class DocumentBaseUriResolver {

    private DocumentBaseUriResolver() {
    }

    static void apply(Document document) {
        for (Element base : document.getElementsByTagName("base")) {
            if (!base.hasAttribute("href")) {
                continue;
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
            return;
        }
    }
}

package com.browicy.engine.css;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StyleSheetRegistryTest {

    @Test
    public void appliesStableDocumentOrderIndependentOfRegistrationOrder() {
        Document document = documentWithParagraph();
        StyleSheetRegistry registry = new StyleSheetRegistry();

        registry.register(10, "p { color: blue; }");
        registry.register(3, "p { color: red; }");
        new StyleApplicator().apply(document, registry);

        assertEquals("blue", paragraph(document).getComputedStyles().get("color"));
    }

    @Test
    public void replacingARegisteredSourceRecalculatesCascade() {
        Document document = documentWithParagraph();
        StyleSheetRegistry registry = new StyleSheetRegistry();
        registry.register(0, "p { color: red; }");
        new StyleApplicator().apply(document, registry);
        assertEquals("red", paragraph(document).getComputedStyles().get("color"));

        registry.register(0, "p { color: green; }");
        new StyleApplicator().apply(document, registry);

        assertEquals("green", paragraph(document).getComputedStyles().get("color"));
    }

    private static Document documentWithParagraph() {
        Document document = new Document("about:test");
        Element html = document.createElement("html");
        Element body = document.createElement("body");
        Element paragraph = document.createElement("p");
        document.appendChild(html);
        html.appendChild(body);
        body.appendChild(paragraph);
        return document;
    }

    private static Element paragraph(Document document) {
        return document.getBody().findFirst("p");
    }
}

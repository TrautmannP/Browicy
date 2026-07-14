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

    @Test
    public void appliesRulesUsingAdvancedSelectors() {
        Document document = documentWithParagraph();
        Element paragraph = paragraph(document);
        paragraph.setAttribute("data-tags", "intro featured");
        Element sibling = document.createElement("p");
        paragraph.getParent().appendChild(sibling);
        StyleSheetRegistry registry = new StyleSheetRegistry();
        registry.register(0, """
                p[data-tags~=\"featured\"]:first-child { color: red; }
                p + p:last-child { color: blue; }
                """);

        new StyleApplicator().apply(document, registry);

        assertEquals("red", paragraph.getComputedStyles().get("color"));
        assertEquals("blue", sibling.getComputedStyles().get("color"));
    }

    @Test
    public void mutableStyleSheetInsertsAndDeletesRulesAtCssomIndexes() {
        Document document = documentWithParagraph();
        Element owner = document.createElement("style");
        StyleSheetRegistry registry = new StyleSheetRegistry();
        CssStyleSheet sheet = registry.register(0, owner, "p { color: red; }");

        assertEquals(1, sheet.insertRule("p { color: blue; }", 1));
        assertEquals(2, sheet.ruleCount());
        assertEquals("p { color: blue; }", sheet.ruleText(1));
        new StyleApplicator().apply(document, registry);
        assertEquals("blue", paragraph(document).getComputedStyles().get("color"));

        sheet.deleteRule(1);
        new StyleApplicator().apply(document, registry);
        assertEquals(1, sheet.ruleCount());
        assertEquals("red", paragraph(document).getComputedStyles().get("color"));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void mutableStyleSheetRejectsInsertPastEndOfRuleList() {
        StyleSheetRegistry registry = new StyleSheetRegistry();
        CssStyleSheet sheet = registry.register(0, "p { color: red; }");

        sheet.insertRule("p { color: blue; }", 2);
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

package com.browicy.engine.dom;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class DomImplementationTest {

    @Test
    public void createsNamespacedDocumentWithDocumentType() {
        DomImplementation implementation = new DomImplementation();
        DocumentType type = implementation.createDocumentType("html", "public", "system");

        Document document = implementation.createDocument("urn:test", "p:root", type);

        assertSame(type, document.getFirstChild());
        assertEquals("public", type.getPublicId());
        assertEquals("system", type.getSystemId());
        assertEquals("p:root", document.getDocumentElement().getTagName());
        assertEquals("urn:test", document.getDocumentElement().getNamespaceUri());
        assertEquals("p", document.getDocumentElement().getPrefix());
        assertEquals("root", document.getDocumentElement().getLocalName());
        assertSame(document, type.getOwnerDocument());
        assertSame(document, document.getDocumentElement().getOwnerDocument());
    }

    @Test
    public void createsEmptyDocumentForEmptyQualifiedName() {
        Document document = new DomImplementation().createDocument(null, null, null);

        assertNull(document.getDocumentElement());
    }

    @Test
    public void validatesNamesAndNamespaceConstraints() {
        Document document = new Document("about:test");

        assertDomError(DomException.INVALID_CHARACTER_ERR, () -> document.createElement("bad name"));
        assertDomError(DomException.NAMESPACE_ERR, () -> document.createElementNS(null, "p:name"));
        assertDomError(DomException.NAMESPACE_ERR,
                () -> document.createElementNS("urn:test", "xml:name"));
        assertDomError(DomException.NAMESPACE_ERR,
                () -> document.createElementNS("http://www.w3.org/2000/xmlns/", "x:name"));
    }

    private static void assertDomError(int code, Runnable operation) {
        try {
            operation.run();
            fail("Expected DOM exception with code " + code);
        } catch (DomException exception) {
            assertEquals(code, exception.getCode());
        }
    }
}

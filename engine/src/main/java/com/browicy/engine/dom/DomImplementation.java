package com.browicy.engine.dom;

/** Fabrik für eigenständige XML-Dokumente und Dokumenttyp-Knoten. */
public final class DomImplementation {

    public Document createDocument(String namespaceUri, String qualifiedName, DocumentType documentType) {
        Document document = new Document("about:blank");
        if (documentType != null) {
            if (documentType.getParent() != null) {
                throw DomException.wrongDocument("Der DocumentType gehört bereits zu einem Dokument");
            }
            document.appendChild(documentType);
        }
        if (qualifiedName != null && !qualifiedName.isEmpty()) {
            document.appendChild(document.createElementNS(namespaceUri, qualifiedName));
        }
        return document;
    }

    public DocumentType createDocumentType(String qualifiedName, String publicId, String systemId) {
        Document.validateQualifiedName(null, qualifiedName);
        return new DocumentType(qualifiedName, publicId, systemId);
    }
}

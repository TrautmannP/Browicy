package com.browicy.engine.dom;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Document extends Node implements ParentNode {

    private static final System.Logger LOGGER = System.getLogger(Document.class.getName());
    private static final String XML_NAMESPACE = "http://www.w3.org/XML/1998/namespace";
    private static final String XMLNS_NAMESPACE = "http://www.w3.org/2000/xmlns/";
    private static final String XML_NAME = "[A-Za-z_][A-Za-z0-9._-]*";
    private static final URI FALLBACK_URI = URI.create("about:blank");

    private final String url;
    private final URI documentUri;
    private URI baseUri;
    private final CopyOnWriteArrayList<DocumentMutationListener> mutationListeners =
            new CopyOnWriteArrayList<>();
    private volatile DocumentReadyState readyState = DocumentReadyState.LOADING;

    public Document(String url) {
        this.url = url == null || url.isBlank() ? FALLBACK_URI.toString() : url;
        this.documentUri = parseAbsoluteUri(this.url);
        this.baseUri = documentUri;
    }

    public String getUrl() {
        return url;
    }

    public URI getDocumentUri() {
        return documentUri;
    }

    public URI getBaseUri() {
        return baseUri;
    }

    public DocumentReadyState getReadyState() {
        return readyState;
    }

    /**
     * Advances the loading state. A document lifecycle is monotonic and cannot return to an
     * earlier state.
     */
    public void transitionTo(DocumentReadyState nextState) {
        Objects.requireNonNull(nextState, "nextState");
        DocumentReadyState current = readyState;
        if (nextState.ordinal() < current.ordinal()) {
            throw new IllegalStateException(
                    "Dokumentstatus kann nicht von " + current + " nach " + nextState + " zurückgesetzt werden");
        }
        readyState = nextState;
    }

    public void addMutationListener(DocumentMutationListener listener) {
        mutationListeners.addIfAbsent(Objects.requireNonNull(listener, "listener"));
    }

    public void removeMutationListener(DocumentMutationListener listener) {
        mutationListeners.remove(listener);
    }

    void dispatchMutation(DomMutation mutation) {
        for (DocumentMutationListener listener : mutationListeners) {
            try {
                listener.onMutation(mutation);
            } catch (RuntimeException failure) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "DOM-Mutationslistener warf eine Exception", failure);
            }
        }
    }

    @Override public short getNodeType() { return DOCUMENT_NODE; }
    @Override public String getNodeName() { return "#document"; }

    public void setBaseUri(URI baseUri) {
        Objects.requireNonNull(baseUri, "baseUri");
        if (!baseUri.isAbsolute()) {
            throw new IllegalArgumentException("Die Basis-URI muss absolut sein: " + baseUri);
        }
        this.baseUri = baseUri;
    }

    public Element createElement(String name) {
        if (name == null || !name.matches(XML_NAME)) {
            throw DomException.invalidCharacter("Ungültiger Elementname: " + name);
        }
        Element element = new Element(name);
        element.setOwnerDocument(this);
        return element;
    }
    public Element createElementNS(String namespaceUri, String qualifiedName) {
        validateQualifiedName(namespaceUri, qualifiedName);
        Element element = new Element(emptyToNull(namespaceUri), qualifiedName);
        element.setOwnerDocument(this);
        return element;
    }
    public TextNode createTextNode(String data) {
        TextNode node = new TextNode(data);
        node.setOwnerDocument(this);
        return node;
    }
    public CommentNode createComment(String data) {
        CommentNode node = new CommentNode(data);
        node.setOwnerDocument(this);
        return node;
    }
    public DocumentFragment createDocumentFragment() {
        DocumentFragment fragment = new DocumentFragment();
        fragment.setOwnerDocument(this);
        return fragment;
    }
    public Range createRange() { return new Range(this); }

    @Override
    protected void validateChildInsertion(Node child) {
        if (child instanceof TextNode) {
            throw DomException.hierarchyRequest("Ein Document darf keinen Textknoten als direktes Kind enthalten");
        }
        if (child instanceof Element && child.getParent() != this
                && getChildren().stream().anyMatch(Element.class::isInstance)) {
            throw DomException.hierarchyRequest("Ein Document darf nur ein Dokumentelement enthalten");
        }
        if (child instanceof DocumentType && child.getParent() != this
                && getChildren().stream().anyMatch(DocumentType.class::isInstance)) {
            throw DomException.hierarchyRequest("Ein Document darf nur einen DocumentType enthalten");
        }
    }

    public Element getDocumentElement() {
        for (Node child : getChildren()) {
            if (child instanceof Element element) {
                return element;
            }
        }
        return null;
    }

    public Element getBody() {
        Element body = findFirst("body");
        if (body != null) {
            return body;
        }
        Element html = getDocumentElement();
        if (html != null) {
            return html;
        }
        for (Node child : getChildren()) {
            if (child instanceof Element element) {
                return element;
            }
        }
        return null;
    }

    public String getTitle() {
        Element title = findFirst("title");
        return title == null ? "" : title.getTextContent().strip();
    }

    public Element getElementById(String id) {
        if (id == null) {
            return null;
        }
        for (Element element : collectElements(null)) {
            if (id.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        return null;
    }

    public List<Element> getElementsByTagName(String tag) {
        return collectElements(tag.toLowerCase());
    }

    private List<Element> collectElements(String tag) {
        List<Element> result = new ArrayList<>();
        collect(this, tag, result);
        return result;
    }

    private static void collect(Node node, String tag, List<Element> result) {
        for (Node child : node.getChildren()) {
            if (child instanceof Element element) {
                if (tag == null || "*".equals(tag) || element.getTagName().equals(tag)) {
                    result.add(element);
                }
                collect(element, tag, result);
            }
        }
    }

    private Element findFirst(String tag) {
        for (Node child : getChildren()) {
            if (child instanceof Element element) {
                Element found = element.findFirst(tag);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    Document copyShallow() {
        Document copy = new Document(url);
        copy.setBaseUri(baseUri);
        return copy;
    }

    static void validateQualifiedName(String namespaceUri, String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isEmpty()) {
            throw DomException.invalidCharacter("Der qualifizierte Name darf nicht leer sein");
        }
        String[] parts = qualifiedName.split(":", -1);
        if (parts.length > 2) {
            throw DomException.namespace("Ungültiger qualifizierter Name: " + qualifiedName);
        }
        for (String part : parts) {
            if (!part.matches(XML_NAME)) {
                if (parts.length > 1 || qualifiedName.indexOf(':') >= 0) {
                    throw DomException.namespace("Ungültiger qualifizierter Name: " + qualifiedName);
                }
                throw DomException.invalidCharacter("Ungültiger Elementname: " + qualifiedName);
            }
        }
        String namespace = emptyToNull(namespaceUri);
        String prefix = parts.length == 2 ? parts[0] : null;
        if (prefix != null && namespace == null) {
            throw DomException.namespace("Ein Präfix benötigt einen Namespace");
        }
        if ("xml".equals(prefix) && !XML_NAMESPACE.equals(namespace)) {
            throw DomException.namespace("Das xml-Präfix benötigt den XML-Namespace");
        }
        boolean xmlnsName = "xmlns".equals(qualifiedName) || "xmlns".equals(prefix);
        if (xmlnsName != XMLNS_NAMESPACE.equals(namespace)) {
            throw DomException.namespace("xmlns-Name und Namespace stimmen nicht überein");
        }
    }

    private static URI parseAbsoluteUri(String value) {
        try {
            URI uri = URI.create(value);
            return uri.isAbsolute() ? uri : FALLBACK_URI;
        } catch (IllegalArgumentException invalidUri) {
            return FALLBACK_URI;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    @Override
    public String toString() {
        return "#document(" + url + ")";
    }
}

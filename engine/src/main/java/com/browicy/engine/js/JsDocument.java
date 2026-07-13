package com.browicy.engine.js;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Event;
import com.browicy.engine.dom.Node;
import com.browicy.engine.dom.UiEvent;
import com.browicy.engine.html.HtmlParser;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * JavaScript-Sicht auf das {@link Document}: das globale {@code document}-Objekt.
 *
 * <p>Unterstützt neben DOM-Core- und Traversal-Funktionen auch das
 * DOM-Level-2-EventTarget-Modell und {@code document.createEvent()}.</p>
 *
 * <p>Knoten- und Event-Wrapper werden pro Dokument gecacht, damit Identität
 * wie im Browser funktioniert ({@code document.body === document.body}).</p>
 */
final class JsDocument implements ProxyObject, JsNodeLike {

    private static final List<String> MEMBERS = List.of(
            "title", "body", "documentElement", "URL", "nodeType", "nodeName", "nodeValue",
            "parentNode", "childNodes", "firstChild", "lastChild", "hasChildNodes",
            "ELEMENT_NODE", "TEXT_NODE", "COMMENT_NODE", "DOCUMENT_NODE", "DOCUMENT_TYPE_NODE", "DOCUMENT_FRAGMENT_NODE",
            "currentScript", "getElementById", "getElementsByTagName", "createElement",
            "createTextNode", "createComment", "createDocumentFragment", "createEvent",
            "createNodeIterator", "createTreeWalker", "write",
            JsEventTarget.ADD_EVENT_LISTENER, JsEventTarget.REMOVE_EVENT_LISTENER, JsEventTarget.DISPATCH_EVENT);

    private final Document document;
    private final Consumer<String> errorSink;
    private final Map<Node, Object> wrappers = new IdentityHashMap<>();
    private final Map<Event, JsEvent> eventWrappers = new IdentityHashMap<>();
    private final List<ListenerRegistration> listenerRegistrations = new ArrayList<>();
    private Element currentScript;
    private Value eventListenerInvoker;

    JsDocument(Document document, Consumer<String> errorSink) {
        this.document = document;
        this.errorSink = errorSink;
    }

    @Override public Document unwrapNode() { return document; }

    /** Liefert den (gecachten) JavaScript-Wrapper für ein DOM-Element. */
    JsElement wrap(Element element) {
        if (element == null) {
            return null;
        }
        return (JsElement) wrappers.computeIfAbsent(element, el -> new JsElement((Element) el, this));
    }

    Object wrap(Node node) {
        if (node == null) {
            return null;
        }
        if (node == document) {
            return this;
        }
        if (node instanceof Element element) {
            return wrap(element);
        }
        return wrappers.computeIfAbsent(node, value -> new JsNode(value, this));
    }

    JsEvent wrap(Event event) {
        if (event == null) {
            return null;
        }
        return eventWrappers.computeIfAbsent(event, value -> new JsEvent(value, this));
    }

    void setEventListenerInvoker(Value eventListenerInvoker) {
        this.eventListenerInvoker = eventListenerInvoker;
    }

    void addEventListener(Node target, String type, Value callback, boolean capture) {
        JsEventListener listener = new JsEventListener(callback, this);
        target.addEventListener(type, listener, capture);
        ListenerRegistration registration = new ListenerRegistration(target, type, listener, capture);
        if (!listenerRegistrations.contains(registration)) {
            listenerRegistrations.add(registration);
        }
    }

    void removeEventListener(Node target, String type, Value callback, boolean capture) {
        JsEventListener listener = new JsEventListener(callback, this);
        target.removeEventListener(type, listener, capture);
        listenerRegistrations.remove(new ListenerRegistration(target, type, listener, capture));
    }

    void clearEventListeners() {
        for (ListenerRegistration registration : List.copyOf(listenerRegistrations)) {
            registration.target().removeEventListener(
                    registration.type(), registration.listener(), registration.capture());
        }
        listenerRegistrations.clear();
    }

    void invokeEventListener(Value callback, Event event) {
        if (eventListenerInvoker == null) {
            throw new IllegalStateException("JavaScript-Event-Invoker ist nicht initialisiert");
        }
        try {
            eventListenerInvoker.executeVoid(callback, wrap(event.getCurrentTarget()), wrap(event));
        } catch (PolyglotException exception) {
            if (exception.isCancelled() || exception.isResourceExhausted()) {
                throw exception;
            }
            errorSink.accept(exception.getMessage() == null ? exception.toString() : exception.getMessage());
        }
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "title" -> document.getTitle();
            case "body" -> wrap(document.getBody());
            case "documentElement" -> wrap(document.getDocumentElement());
            case "URL" -> document.getUrl();
            case "nodeType" -> document.getNodeType();
            case "nodeName" -> document.getNodeName();
            case "nodeValue", "parentNode" -> null;
            case "childNodes" -> ProxyArray.fromList(document.getChildren().stream().map(this::wrap).toList());
            case "firstChild" -> wrap(document.getFirstChild());
            case "lastChild" -> wrap(document.getLastChild());
            case "hasChildNodes" -> (ProxyExecutable) args -> document.hasChildNodes();
            case "ELEMENT_NODE" -> Node.ELEMENT_NODE;
            case "TEXT_NODE" -> Node.TEXT_NODE;
            case "COMMENT_NODE" -> Node.COMMENT_NODE;
            case "DOCUMENT_NODE" -> Node.DOCUMENT_NODE;
            case "DOCUMENT_TYPE_NODE" -> Node.DOCUMENT_TYPE_NODE;
            case "DOCUMENT_FRAGMENT_NODE" -> Node.DOCUMENT_FRAGMENT_NODE;
            case "currentScript" -> wrap(currentScript);
            case "getElementById" -> (ProxyExecutable) args ->
                    wrap(document.getElementById(asString(args, 0)));
            case "getElementsByTagName" -> (ProxyExecutable) args ->
                    ProxyArray.fromList(document.getElementsByTagName(asString(args, 0)).stream()
                            .map(element -> (Object) wrap(element))
                            .collect(Collectors.toList()));
            case "createElement" -> (ProxyExecutable) args ->
                    wrap(document.createElement(asString(args, 0)));
            case "createTextNode" -> (ProxyExecutable) args -> wrap(document.createTextNode(asString(args, 0)));
            case "createComment" -> (ProxyExecutable) args -> wrap(document.createComment(asString(args, 0)));
            case "createDocumentFragment" -> (ProxyExecutable) args -> wrap(document.createDocumentFragment());
            case "createEvent" -> (ProxyExecutable) args -> wrap(createEvent(asString(args, 0)));
            case "createNodeIterator" -> (ProxyExecutable) args -> new JsNodeIterator(this,
                    expectNode(args, 0), whatToShow(args, 1), filter(args, 2));
            case "createTreeWalker" -> (ProxyExecutable) args -> new JsTreeWalker(this,
                    expectNode(args, 0), whatToShow(args, 1), filter(args, 2));
            case "write" -> (ProxyExecutable) args -> {
                StringBuilder html = new StringBuilder();
                for (int i = 0; i < args.length; i++) {
                    html.append(asString(args, i));
                }
                write(html.toString());
                return null;
            };
            case JsEventTarget.ADD_EVENT_LISTENER -> JsEventTarget.addEventListener(document, this);
            case JsEventTarget.REMOVE_EVENT_LISTENER -> JsEventTarget.removeEventListener(document, this);
            case JsEventTarget.DISPATCH_EVENT -> JsEventTarget.dispatchEvent(document);
            default -> null;
        };
    }

    private static Event createEvent(String interfaceName) {
        return switch (interfaceName.toLowerCase(Locale.ROOT)) {
            case "event", "events", "htmlevents" -> new Event();
            case "uievent", "uievents" -> new UiEvent();
            default -> throw new IllegalArgumentException("Nicht unterstütztes Event-Interface: " + interfaceName);
        };
    }

    private static Node expectNode(Value[] args, int index) {
        JsNodeLike node = JsElement.expectNode(args, index, false);
        return node.unwrapNode();
    }

    private static long whatToShow(Value[] args, int index) {
        return index >= args.length || args[index].isNull() ? 0xFFFFFFFFL : args[index].asLong() & 0xFFFFFFFFL;
    }

    private static Value filter(Value[] args, int index) {
        return index >= args.length || args[index].isNull() ? null : args[index];
    }

    void setCurrentScript(Element currentScript) {
        this.currentScript = currentScript;
    }

    private void write(String html) {
        Node parent = currentScript == null ? document.getBody() : currentScript.getParent();
        if (parent == null) {
            parent = document;
        }
        Node reference = null;
        if (currentScript != null) {
            List<Node> siblings = parent.getChildren();
            int index = siblings.indexOf(currentScript);
            if (index >= 0 && index + 1 < siblings.size()) {
                reference = siblings.get(index + 1);
            }
        }

        Document fragment = new HtmlParser().parse(html, document.getUrl());
        for (Node node : List.copyOf(fragment.getChildren())) {
            parent.insertBefore(node, reference);
        }
    }

    @Override
    public void putMember(String key, Value value) {
        if ("title".equals(key)) {
            setTitle(value.isString() ? value.asString() : value.toString());
            return;
        }
        throw new UnsupportedOperationException(
                "Eigenschaft nicht unterstützt oder schreibgeschützt: " + key);
    }

    /**
     * Setzt den Dokumenttitel. Fehlt das {@code <title>}-Element, wird es
     * im {@code <head>} angelegt; ohne {@code <head>} passiert nichts
     * (Fragment ohne Grundgerüst).
     */
    private void setTitle(String text) {
        Element title = firstByTag("title");
        if (title == null) {
            Element head = firstByTag("head");
            if (head == null) {
                return;
            }
            title = new Element("title");
            head.appendChild(title);
        }
        title.setTextContent(text);
    }

    private Element firstByTag(String tag) {
        List<Element> elements = document.getElementsByTagName(tag);
        return elements.isEmpty() ? null : elements.get(0);
    }

    @Override
    public Object getMemberKeys() {
        return MEMBERS.toArray();
    }

    @Override
    public boolean hasMember(String key) {
        return MEMBERS.contains(key);
    }

    private static String asString(Value[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Argument " + index + " fehlt");
        }
        Value value = args[index];
        return value.isString() ? value.asString() : value.toString();
    }

    private record ListenerRegistration(Node target, String type,
                                        JsEventListener listener, boolean capture) {
    }

    @Override
    public String toString() {
        return "[object HTMLDocument]";
    }
}

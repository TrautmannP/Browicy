package com.browicy.engine.js;

import lombok.AccessLevel;
import lombok.Setter;

import com.browicy.engine.css.CssStyleSheet;
import com.browicy.engine.css.StyleApplicator;
import com.browicy.engine.css.StyleSheetRegistry;
import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Event;
import com.browicy.engine.dom.Node;
import com.browicy.engine.js.handlers.JsDocumentCreationHandler;
import com.browicy.engine.js.handlers.JsDocumentTraversalHandler;
import com.browicy.engine.js.handlers.JsMemberHandler;
import com.browicy.engine.js.handlers.JsNodeConstants;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class JsDocument implements ProxyObject, JsNodeLike {

    private static final List<JsMemberHandler> HANDLERS = List.of(
            new JsDocumentTraversalHandler(), new JsDocumentCreationHandler());

    private static final List<String> CORE_MEMBERS = List.of(
            "title", "head", "body", "activeElement", "cookie", "documentElement",
            "forms", "links", "scripts", "styleSheets", "implementation", "defaultView",
            "URL", "referrer", "location", "readyState", "nodeType", "nodeName",
            "nodeValue", "parentNode", "ownerDocument",
            JsEventTarget.ADD_EVENT_LISTENER, JsEventTarget.REMOVE_EVENT_LISTENER,
            JsEventTarget.DISPATCH_EVENT);

    private static final List<String> MEMBERS = buildMembers();

    private static List<String> buildMembers() {
        List<String> keys = new ArrayList<>(JsNodeConstants.keys());
        keys.addAll(CORE_MEMBERS);
        for (JsMemberHandler handler : HANDLERS) {
            keys.addAll(handler.keys());
        }
        return List.copyOf(keys);
    }

    private final Document document;
    private final Consumer<String> errorSink;
    private final Map<Node, Object> wrappers = new IdentityHashMap<>();
    private final Map<Document, JsDocument> documentWrappers;
    private final Map<Event, JsEvent> eventWrappers = new IdentityHashMap<>();
    private final List<ListenerRegistration> listenerRegistrations;
    private final StyleSheetRegistry styleSheets;
    private final Runnable styleSheetMutationCallback;
    private final Map<CssStyleSheet, JsCssStyleSheet> styleSheetWrappers = new IdentityHashMap<>();
    private final Map<String, Value> expandos = new LinkedHashMap<>();
    @Setter(AccessLevel.PACKAGE)
    private Element currentScript;
    @Setter(AccessLevel.PACKAGE)
    private Value eventListenerInvoker;
    @Setter(AccessLevel.PACKAGE)
    private Value domOperationWrapper;
    @Setter(AccessLevel.PACKAGE)
    private java.util.function.LongSupplier taskBudgetMillis = () -> 0L;
    @Setter(AccessLevel.PACKAGE)
    private java.util.function.Supplier<String> taskDescriptionSupplier = () -> null;
    @Setter(AccessLevel.PACKAGE)
    private JsCookieStore cookieStore;
    @Setter(AccessLevel.PACKAGE)
    private LayoutMetricsAccess layoutMetrics = LayoutMetricsAccess.DISABLED;
    private JsCustomElementRegistry customElements;
    private JsDomImplementation implementation;
    private JsWindow defaultView;
    private String referrer = "";
    private Value promiseGlobal;

    void setCustomElementRegistry(JsCustomElementRegistry customElements) {
        this.customElements = customElements;
    }

    JsDocument(Document document, Consumer<String> errorSink,
               StyleSheetRegistry styleSheets, Runnable styleSheetMutationCallback) {
        this(document, errorSink, new IdentityHashMap<>(), new ArrayList<>(),
                styleSheets, styleSheetMutationCallback);
    }

    private JsDocument(Document document, Consumer<String> errorSink,
                       Map<Document, JsDocument> documentWrappers,
                       List<ListenerRegistration> listenerRegistrations,
                       StyleSheetRegistry styleSheets,
                       Runnable styleSheetMutationCallback) {
        this.document = document;
        this.errorSink = errorSink;
        this.documentWrappers = documentWrappers;
        this.listenerRegistrations = listenerRegistrations;
        this.styleSheets = Objects.requireNonNull(styleSheets, "styleSheets");
        this.styleSheetMutationCallback = Objects.requireNonNull(
                styleSheetMutationCallback, "styleSheetMutationCallback");
        documentWrappers.put(document, this);
    }

    @Override
    public Document unwrapNode() {
        return document;
    }

    public JsElement wrap(Element element) {
        if (element == null) {
            return null;
        }
        return (JsElement) wrappers.computeIfAbsent(
                element, el -> new JsElement((Element) el, this));
    }

    public LayoutMetricsAccess layoutMetrics() {
        return layoutMetrics;
    }

    public Object wrap(Node node) {
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

    public JsDocument wrapDocument(Document relatedDocument) {
        JsDocument existing = documentWrappers.get(relatedDocument);
        if (existing != null) {
            return existing;
        }
        JsDocument wrapper = new JsDocument(
                relatedDocument, errorSink, documentWrappers, listenerRegistrations,
                styleSheets, styleSheetMutationCallback);
        wrapper.setEventListenerInvoker(eventListenerInvoker);
        wrapper.setDomOperationWrapper(domOperationWrapper);
        wrapper.setTaskBudgetMillis(taskBudgetMillis);
        wrapper.setCookieStore(cookieStore);
        wrapper.setPromiseGlobal(promiseGlobal);
        wrapper.setReferrer(referrer);
        documentWrappers.put(relatedDocument, wrapper);
        return wrapper;
    }

    void setReferrer(String referrer) {
        this.referrer = referrer == null ? "" : referrer;
    }

    void setPromiseGlobal(Value promiseGlobal) {
        this.promiseGlobal = promiseGlobal;
    }

    Value promiseGlobal() {
        return promiseGlobal;
    }

    public Element currentScript() {
        return currentScript;
    }

    JsComputedStyleDeclaration computedStyle(JsElement element) {
        Document owner = element.unwrap().getOwnerDocument();
        if (owner != null && owner != document) {
            new StyleApplicator().apply(owner);
        }
        return new JsComputedStyleDeclaration(element.unwrap(), layoutMetrics);
    }

    public JsWindow defaultView() {
        return defaultView == null ? defaultView = new JsWindow(this) : defaultView;
    }

    void setExpando(String name, Value value) {
        expandos.put(name, value);
    }

    public Object wrapOwnerDocument(Node node) {
        Document ownerDocument = node.getOwnerDocument();
        return ownerDocument == null ? null : wrapDocument(ownerDocument);
    }

    void preserveWrapper(JsNodeLike wrapper) {
        wrappers.put(wrapper.unwrapNode(), wrapper);
    }

    public JsEvent wrap(Event event) {
        if (event == null) {
            return null;
        }
        return eventWrappers.computeIfAbsent(event, value -> new JsEvent(value, this));
    }

    public JsCssStyleSheet styleSheet(Element ownerNode) {
        return wrap(styleSheets.ensureStyleSheet(ownerNode, ownerNode.getTextContent()));
    }

    public void styleSheetContentChanged(Element ownerNode) {
        styleSheets.updateStyleSheet(ownerNode, ownerNode.getTextContent());
        styleSheetMutationCallback.run();
    }

    private JsCssStyleSheet wrap(CssStyleSheet sheet) {
        return styleSheetWrappers.computeIfAbsent(sheet,
                value -> new JsCssStyleSheet(value, this, styleSheetMutationCallback));
    }

    public Object domOperation(ProxyExecutable operation) {
        if (domOperationWrapper == null) {
            return operation;
        }
        return domOperationWrapper.execute(operation);
    }

    public JsHtmlCollection htmlCollection(Supplier<List<Element>> query) {
        return new JsHtmlCollection(query, this);
    }

    public JsNodeList nodeList(List<Element> elements) {
        return new JsNodeList(elements, this);
    }

    public void elementCreated(Element element) {
        if (customElements != null) {
            customElements.elementCreated(element);
        }
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
            errorSink.accept(GraalPageRuntime.describePolyglotFailure(
                    exception, taskBudgetMillis.getAsLong(), taskDescriptionSupplier.get()));
        }
    }

    @Override
    public Object getMember(String key) {
        Integer constant = JsNodeConstants.valueOf(key);
        if (constant != null) {
            return constant;
        }
        for (JsMemberHandler handler : HANDLERS) {
            if (handler.canHandle(key)) {
                return handler.get(key, null, this);
            }
        }
        return switch (key) {
            case "title" -> document.getTitle();
            case "head" -> wrap(firstByTag("head"));
            case "body" -> wrap(document.getBody());
            case "activeElement" -> wrap(document.getFocusedElement());
            case "cookie" -> cookieStore == null ? "" : cookieStore.cookiesForScript(documentUri());
            case "documentElement" -> wrap(document.getDocumentElement());
            case "forms" -> htmlCollection(() -> document.getElementsByTagName("form"));
            case "links" -> htmlCollection(() -> {
                List<Element> links = new ArrayList<>();
                for (Element element : document.getElementsByTagName("a")) {
                    if (element.hasAttribute("href")) {
                        links.add(element);
                    }
                }
                for (Element element : document.getElementsByTagName("area")) {
                    if (element.hasAttribute("href")) {
                        links.add(element);
                    }
                }
                return links;
            });
            case "scripts" -> htmlCollection(() -> document.getElementsByTagName("script"));
            case "styleSheets" -> new JsStyleSheetList();
            case "implementation" -> implementation == null
                    ? implementation = new JsDomImplementation(this) : implementation;
            case "defaultView" -> defaultView();
            case "URL" -> document.getUrl();
            case "referrer" -> referrer;
            case "location" -> ProxyObject.fromMap(
                    GraalPageRuntime.locationParts(document.getUrl()));
            case "readyState" -> document.getReadyState().scriptValue();
            case "nodeType" -> document.getNodeType();
            case "nodeName" -> document.getNodeName();
            case "nodeValue", "parentNode", "ownerDocument" -> null;
            case JsEventTarget.ADD_EVENT_LISTENER -> JsEventTarget.addEventListener(document, this);
            case JsEventTarget.REMOVE_EVENT_LISTENER -> JsEventTarget.removeEventListener(document, this);
            case JsEventTarget.DISPATCH_EVENT -> JsEventTarget.dispatchEvent(document);
            default -> expandos.get(key);
        };
    }

    @Override
    public void putMember(String key, Value value) {
        if ("title".equals(key)) {
            setTitle(value.isString() ? value.asString() : value.toString());
            return;
        }
        if ("cookie".equals(key)) {
            if (cookieStore != null) {
                cookieStore.storeFromScript(
                        documentUri(), value.isString() ? value.asString() : value.toString());
            }
            return;
        }
        expandos.put(key, value);
    }

    private java.net.URI documentUri() {
        String url = document.getUrl();
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            return new java.net.URI(url.strip());
        } catch (java.net.URISyntaxException invalid) {
            return null;
        }
    }

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
        List<String> keys = new ArrayList<>(MEMBERS);
        keys.addAll(expandos.keySet());
        return ProxyArray.fromArray(keys.toArray());
    }

    @Override
    public boolean hasMember(String key) {
        return MEMBERS.contains(key) || expandos.containsKey(key);
    }

    private record ListenerRegistration(Node target, String type,
                                        JsEventListener listener, boolean capture) {
    }

    private final class JsStyleSheetList implements ProxyObject {
        @Override
        public Object getMember(String key) {
            if ("length".equals(key)) return styleSheets.styleSheets().size();
            if ("item".equals(key)) return (ProxyExecutable) args -> item(
                    args.length > 0 && args[0].fitsInInt() ? args[0].asInt() : -1);
            try {
                return item(Integer.parseInt(key));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private Object item(int index) {
            List<CssStyleSheet> sheets = styleSheets.styleSheets();
            return index >= 0 && index < sheets.size() ? wrap(sheets.get(index)) : null;
        }

        @Override
        public Object getMemberKeys() {
            List<String> keys = new ArrayList<>(List.of("length", "item"));
            for (int index = 0; index < styleSheets.styleSheets().size(); index++) {
                keys.add(Integer.toString(index));
            }
            return ProxyArray.fromArray(keys.toArray());
        }

        @Override
        public boolean hasMember(String key) {
            return "length".equals(key) || "item".equals(key) || getMember(key) != null;
        }

        @Override
        public void putMember(String key, Value value) {
            throw new UnsupportedOperationException("StyleSheetList ist schreibgeschuetzt");
        }
    }

    @Override
    public String toString() {
        return "[object HTMLDocument]";
    }
}

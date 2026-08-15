package com.browicy.engine.js.handlers;

import com.browicy.engine.dom.CustomEvent;
import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Event;
import com.browicy.engine.dom.Node;
import com.browicy.engine.dom.UiEvent;
import com.browicy.engine.html.HtmlParser;
import com.browicy.engine.js.JsDocument;
import com.browicy.engine.js.JsElement;
import com.browicy.engine.js.JsNodeIterator;
import com.browicy.engine.js.JsNodeList;
import com.browicy.engine.js.JsRange;
import com.browicy.engine.js.JsTreeWalker;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.util.List;
import java.util.Locale;

import static com.browicy.engine.js.handlers.JsMemberHandler.asString;
import static com.browicy.engine.js.handlers.JsMemberHandler.expectNode;
import static com.browicy.engine.js.handlers.JsMemberHandler.nullableString;

public final class JsDocumentCreationHandler implements JsMemberHandler {

    private static final List<String> KEYS = List.of(
            "currentScript",
            "getElementById", "getElementsByTagName", "querySelector", "querySelectorAll",
            "elementFromPoint",
            "createElement", "createElementNS", "createTextNode", "createComment",
            "createDocumentFragment", "createRange", "createEvent",
            "createNodeIterator", "createTreeWalker", "write");

    @Override
    public List<String> keys() {
        return KEYS;
    }

    @Override
    public boolean canHandle(String key) {
        return KEYS.contains(key);
    }

    @Override
    public Object get(String key, JsElement element, JsDocument doc) {
        Document document = doc.unwrapNode();
        return switch (key) {
            case "currentScript" -> doc.wrap(doc.currentScript());
            case "getElementById" -> (ProxyExecutable) args ->
                    doc.wrap(document.getElementById(asString(args, 0)));
            case "getElementsByTagName" -> (ProxyExecutable) args ->
                    doc.htmlCollection(() -> document.getElementsByTagName(asString(args, 0)));
            case "querySelector" -> doc.domOperation((ProxyExecutable) args ->
                    doc.wrap(document.querySelector(asString(args, 0))));
            case "querySelectorAll" -> doc.domOperation((ProxyExecutable) args ->
                    doc.nodeList(document.querySelectorAll(asString(args, 0))));
            case "elementFromPoint" -> (ProxyExecutable) args -> null;
            case "createElement" -> doc.domOperation((ProxyExecutable) args -> {
                Element created = document.createElement(asString(args, 0));
                doc.elementCreated(created);
                return doc.wrap(created);
            });
            case "createElementNS" -> doc.domOperation((ProxyExecutable) args ->
                    doc.wrap(document.createElementNS(nullableString(args, 0), asString(args, 1))));
            case "createTextNode" -> (ProxyExecutable) args ->
                    doc.wrap(document.createTextNode(asString(args, 0)));
            case "createComment" -> (ProxyExecutable) args ->
                    doc.wrap(document.createComment(asString(args, 0)));
            case "createDocumentFragment" -> (ProxyExecutable) args ->
                    doc.wrap(document.createDocumentFragment());
            case "createRange" -> (ProxyExecutable) args ->
                    new JsRange(document.createRange(), doc);
            case "createEvent" -> (ProxyExecutable) args -> doc.wrap(createEvent(asString(args, 0)));
            case "createNodeIterator" -> (ProxyExecutable) args -> new JsNodeIterator(doc,
                    expectNode(args, 0, false).unwrapNode(),
                    whatToShow(args, 1), filter(args, 2));
            case "createTreeWalker" -> (ProxyExecutable) args -> new JsTreeWalker(doc,
                    expectNode(args, 0, false).unwrapNode(),
                    whatToShow(args, 1), filter(args, 2));
            case "write" -> (ProxyExecutable) args -> {
                StringBuilder html = new StringBuilder();
                for (int i = 0; i < args.length; i++) {
                    html.append(asString(args, i));
                }
                write(doc, html.toString());
                return null;
            };
            default -> null;
        };
    }

    @Override
    public boolean set(String key, Value value, JsElement element, JsDocument doc) {
        return false;
    }

    private static Event createEvent(String interfaceName) {
        return switch (interfaceName.toLowerCase(Locale.ROOT)) {
            case "event", "events", "htmlevents" -> new Event();
            case "uievent", "uievents" -> new UiEvent();
            case "customevent", "customevents" -> new CustomEvent();
            default -> throw new IllegalArgumentException(
                    "Nicht unterstütztes Event-Interface: " + interfaceName);
        };
    }

    private static long whatToShow(Value[] args, int index) {
        return index >= args.length || args[index].isNull()
                ? 0xFFFFFFFFL : args[index].asLong() & 0xFFFFFFFFL;
    }

    private static Value filter(Value[] args, int index) {
        return index >= args.length || args[index].isNull() ? null : args[index];
    }

    private static void write(JsDocument doc, String html) {
        Document document = doc.unwrapNode();
        Node parent = doc.currentScript() == null
                ? document.getBody() : doc.currentScript().getParent();
        if (parent == null) {
            parent = document;
        }
        Node reference = null;
        if (doc.currentScript() != null) {
            List<Node> siblings = parent.getChildren();
            int index = siblings.indexOf(doc.currentScript());
            if (index >= 0 && index + 1 < siblings.size()) {
                reference = siblings.get(index + 1);
            }
        }

        Document fragment = new HtmlParser().parse(html, document.getUrl());
        for (Node node : List.copyOf(fragment.getChildren())) {
            parent.insertBefore(node, reference);
        }
    }
}

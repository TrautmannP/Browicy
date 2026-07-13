package com.browicy.engine.js;

import com.browicy.engine.dom.Element;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.List;
import java.util.stream.Collectors;

/**
 * JavaScript-Sicht auf ein {@link Element} des Browicy-DOM. Implementiert
 * als {@link ProxyObject}, d.h. ohne Java-Reflection — der Polyglot-Kontext
 * kann dadurch vollständig ohne Host-Zugriff laufen.
 *
 * <p>Unterstützt einen minimalen, browserüblichen Ausschnitt:
 * {@code tagName}, {@code id}, {@code textContent} (lesen und schreiben),
 * {@code children}, {@code getAttribute}, {@code setAttribute},
 * {@code hasAttribute} und {@code appendChild}.</p>
 */
final class JsElement implements ProxyObject, JsNodeLike {

    private static final List<String> MEMBERS = List.of(
            "tagName", "nodeName", "nodeType", "nodeValue", "id", "className", "type", "textContent", "children", "childNodes",
            "parentNode", "firstChild", "lastChild", "previousSibling", "nextSibling",
            "getAttribute", "setAttribute", "removeAttribute", "hasAttribute",
            "appendChild", "insertBefore", "replaceChild", "removeChild", "hasChildNodes", "contains", "click",
            JsEventTarget.ADD_EVENT_LISTENER, JsEventTarget.REMOVE_EVENT_LISTENER, JsEventTarget.DISPATCH_EVENT,
            "ELEMENT_NODE", "TEXT_NODE", "COMMENT_NODE", "DOCUMENT_NODE", "DOCUMENT_TYPE_NODE", "DOCUMENT_FRAGMENT_NODE");

    private final Element element;
    private final JsDocument document;

    JsElement(Element element, JsDocument document) {
        this.element = element;
        this.document = document;
    }

    /** Das zugrunde liegende DOM-Element (für {@code appendChild} u.ä.). */
    Element unwrap() {
        return element;
    }

    @Override public Element unwrapNode() { return element; }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            // Wie im Browser-DOM: Tag-Namen von HTML-Elementen sind GROSS geschrieben
            case "tagName" -> element.getTagName().toUpperCase();
            case "nodeName" -> element.getTagName().toUpperCase();
            case "nodeType" -> element.getNodeType();
            case "nodeValue" -> null;
            case "id" -> orEmpty(element.getAttribute("id"));
            case "className" -> orEmpty(element.getAttribute("class"));
            case "type" -> orEmpty(element.getAttribute("type"));
            case "textContent" -> element.getTextContent();
            case "children" -> ProxyArray.fromList(element.getChildElements().stream()
                    .map(child -> (Object) document.wrap(child))
                    .collect(Collectors.toList()));
            case "childNodes" -> ProxyArray.fromList(element.getChildren().stream()
                    .map(document::wrap).collect(Collectors.toList()));
            case "parentNode" -> document.wrap(element.getParent());
            case "firstChild" -> childAt(0);
            case "lastChild" -> childAt(element.getChildren().size() - 1);
            case "previousSibling" -> sibling(-1);
            case "nextSibling" -> sibling(1);
            case "getAttribute" -> (ProxyExecutable) args -> element.getAttribute(asString(args, 0));
            case "setAttribute" -> (ProxyExecutable) args -> {
                element.setAttribute(asString(args, 0), asString(args, 1));
                return null;
            };
            case "removeAttribute" -> (ProxyExecutable) args -> {
                element.removeAttribute(asString(args, 0));
                return null;
            };
            case "hasAttribute" -> (ProxyExecutable) args -> element.hasAttribute(asString(args, 0));
            case "appendChild" -> (ProxyExecutable) args -> {
                JsNodeLike child = expectNode(args, 0, false);
                element.appendChild(child.unwrapNode());
                return child;
            };
            case "insertBefore" -> (ProxyExecutable) args -> {
                JsNodeLike child = expectNode(args, 0, false);
                JsNodeLike reference = expectNode(args, 1, true);
                element.insertBefore(child.unwrapNode(), reference == null ? null : reference.unwrapNode());
                return child;
            };
            case "replaceChild" -> (ProxyExecutable) args -> {
                JsNodeLike replacement = expectNode(args, 0, false);
                JsNodeLike oldChild = expectNode(args, 1, false);
                element.replaceChild(replacement.unwrapNode(), oldChild.unwrapNode());
                return oldChild;
            };
            case "removeChild" -> (ProxyExecutable) args -> {
                JsNodeLike child = expectNode(args, 0, false);
                element.removeChild(child.unwrapNode());
                return child;
            };
            case "hasChildNodes" -> (ProxyExecutable) args -> element.hasChildNodes();
            case "contains" -> (ProxyExecutable) args -> {
                JsNodeLike other = expectNode(args, 0, true);
                return other != null && element.contains(other.unwrapNode());
            };
            case "click" -> JsEventTarget.click(element);
            case JsEventTarget.ADD_EVENT_LISTENER -> JsEventTarget.addEventListener(element, document);
            case JsEventTarget.REMOVE_EVENT_LISTENER -> JsEventTarget.removeEventListener(element, document);
            case JsEventTarget.DISPATCH_EVENT -> JsEventTarget.dispatchEvent(element);
            case "ELEMENT_NODE" -> com.browicy.engine.dom.Node.ELEMENT_NODE;
            case "TEXT_NODE" -> com.browicy.engine.dom.Node.TEXT_NODE;
            case "COMMENT_NODE" -> com.browicy.engine.dom.Node.COMMENT_NODE;
            case "DOCUMENT_NODE" -> com.browicy.engine.dom.Node.DOCUMENT_NODE;
            case "DOCUMENT_TYPE_NODE" -> com.browicy.engine.dom.Node.DOCUMENT_TYPE_NODE;
            case "DOCUMENT_FRAGMENT_NODE" -> com.browicy.engine.dom.Node.DOCUMENT_FRAGMENT_NODE;
            default -> null;
        };
    }

    @Override
    public void putMember(String key, Value value) {
        switch (key) {
            case "textContent" -> element.setTextContent(toText(value));
            case "id" -> element.setAttribute("id", toText(value));
            case "className" -> element.setAttribute("class", toText(value));
            case "type" -> element.setAttribute("type", toText(value));
            default -> throw new UnsupportedOperationException(
                    "Eigenschaft nicht unterstützt oder schreibgeschützt: " + key);
        }
    }

    private Object childAt(int index) {
        return index >= 0 && index < element.getChildren().size()
                ? document.wrap(element.getChildren().get(index)) : null;
    }

    private Object sibling(int offset) {
        if (element.getParent() == null) {
            return null;
        }
        List<com.browicy.engine.dom.Node> siblings = element.getParent().getChildren();
        int index = siblings.indexOf(element) + offset;
        return index >= 0 && index < siblings.size() ? document.wrap(siblings.get(index)) : null;
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
        return toText(args[index]);
    }

    static JsNodeLike expectNode(Value[] args, int index, boolean nullable) {
        if (index < args.length && args[index].isNull() && nullable) return null;
        if (index < args.length && args[index].isProxyObject()
                && args[index].asProxyObject() instanceof JsNodeLike node) {
            return node;
        }
        throw new IllegalArgumentException("Es wird ein DOM-Knoten erwartet");
    }

    private static String toText(Value value) {
        return value.isString() ? value.asString() : value.toString();
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String toString() {
        return "[object HTML" + element.getTagName() + "Element]";
    }
}

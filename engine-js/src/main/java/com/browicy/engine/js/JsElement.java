package com.browicy.engine.js;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import com.browicy.engine.dom.Element;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.stream.Collectors;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class JsElement implements ProxyObject, JsNodeLike {

    private static final List<String> MEMBERS = List.of(
            "tagName", "nodeName", "nodeType", "nodeValue", "namespaceURI", "prefix", "localName",
            "id", "className", "classList", "name", "type", "value", "checked", "defaultChecked", "selected", "defaultSelected",
            "textContent", "children", "childNodes", "length", "elements", "form", "options", "selectedIndex",
            "caption", "tHead", "tFoot", "tBodies", "rows", "cells", "rowIndex", "sectionRowIndex", "cellIndex",
            "parentNode", "ownerDocument", "firstChild", "lastChild", "previousSibling", "nextSibling",
            "getAttribute", "setAttribute", "removeAttribute", "hasAttribute", "getElementsByTagName",
            "querySelector", "querySelectorAll",
            "createCaption", "deleteCaption", "createTHead", "deleteTHead", "createTFoot", "deleteTFoot",
            "insertRow", "deleteRow", "insertCell", "deleteCell", "add", "remove",
            "appendChild", "insertBefore", "replaceChild", "removeChild", "hasChildNodes", "contains",
            "compareDocumentPosition", "isSameNode", "isEqualNode", "cloneNode", "click",
            JsEventTarget.ADD_EVENT_LISTENER, JsEventTarget.REMOVE_EVENT_LISTENER, JsEventTarget.DISPATCH_EVENT,
            "ELEMENT_NODE", "TEXT_NODE", "COMMENT_NODE", "DOCUMENT_NODE", "DOCUMENT_TYPE_NODE", "DOCUMENT_FRAGMENT_NODE",
            "DOCUMENT_POSITION_DISCONNECTED", "DOCUMENT_POSITION_PRECEDING", "DOCUMENT_POSITION_FOLLOWING",
            "DOCUMENT_POSITION_CONTAINS", "DOCUMENT_POSITION_CONTAINED_BY", "DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC");

    private final Element element;
    private final JsDocument document;
    private JsDomTokenList classList;

    Element unwrap() {
        return element;
    }

    @Override public Element unwrapNode() { return element; }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "tagName" -> element.getNodeName();
            case "nodeName" -> element.getNodeName();
            case "nodeType" -> element.getNodeType();
            case "nodeValue" -> null;
            case "namespaceURI" -> element.getNamespaceUri();
            case "prefix" -> element.getPrefix();
            case "localName" -> element.getLocalName();
            case "id" -> orEmpty(element.getAttribute("id"));
            case "className" -> orEmpty(element.getAttribute("class"));
            case "classList" -> classList == null
                    ? classList = new JsDomTokenList(element.getClassList(), document) : classList;
            case "name" -> orEmpty(element.getAttribute("name"));
            case "type" -> inputType();
            case "value" -> value();
            case "checked" -> element.isCheckedState();
            case "defaultChecked" -> element.hasAttribute("checked");
            case "selected" -> element.hasAttribute("selected");
            case "defaultSelected" -> element.hasAttribute("selected");
            case "textContent" -> element.getTextContent();
            case "children" -> collection(element::getChildElements);
            case "childNodes" -> ProxyArray.fromList(element.getChildren().stream()
                    .map(document::wrap).collect(Collectors.toList()));
            case "elements" -> collection(this::formControls);
            case "length" -> "form".equals(tag()) ? formControls().size()
                    : "select".equals(tag()) ? options().size() : 0;
            case "form" -> document.wrap(formOwner());
            case "options" -> collection(this::options);
            case "selectedIndex" -> selectedIndex();
            case "caption" -> document.wrap(direct("caption"));
            case "tHead" -> document.wrap(direct("thead"));
            case "tFoot" -> document.wrap(direct("tfoot"));
            case "tBodies" -> collection(() -> directAll("tbody"));
            case "rows" -> collection(this::rows);
            case "cells" -> collection(this::cells);
            case "rowIndex" -> rowIndex(false);
            case "sectionRowIndex" -> rowIndex(true);
            case "cellIndex" -> cellIndex();
            case "parentNode" -> document.wrap(element.getParent());
            case "ownerDocument" -> document.wrapOwnerDocument(element);
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
            case "getElementsByTagName" -> (ProxyExecutable) args ->
                    collection(() -> element.getElementsByTagName(asString(args, 0)));
            case "querySelector" -> document.domOperation((ProxyExecutable) args ->
                    document.wrap(element.querySelector(asString(args, 0))));
            case "querySelectorAll" -> document.domOperation((ProxyExecutable) args ->
                    new JsNodeList(element.querySelectorAll(asString(args, 0)), document));
            case "createCaption" -> (ProxyExecutable) args -> document.wrap(createTablePart("caption", 0));
            case "deleteCaption" -> removeTablePart("caption");
            case "createTHead" -> (ProxyExecutable) args -> document.wrap(createTablePart("thead", afterCaption()));
            case "deleteTHead" -> removeTablePart("thead");
            case "createTFoot" -> (ProxyExecutable) args -> document.wrap(createTablePart("tfoot", element.getChildren().size()));
            case "deleteTFoot" -> removeTablePart("tfoot");
            case "insertRow" -> (ProxyExecutable) args -> document.wrap(insertRow(indexArg(args, 0, -1)));
            case "deleteRow" -> (ProxyExecutable) args -> { deleteFrom(rows(), indexArg(args, 0, -2)); return null; };
            case "insertCell" -> (ProxyExecutable) args -> document.wrap(insertCell(indexArg(args, 0, -1)));
            case "deleteCell" -> (ProxyExecutable) args -> { deleteFrom(cells(), indexArg(args, 0, -2)); return null; };
            case "add" -> (ProxyExecutable) args -> { addOption(args); return null; };
            case "remove" -> (ProxyExecutable) args -> { removeOption(args); return null; };
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
            case "compareDocumentPosition" -> (ProxyExecutable) args ->
                    element.compareDocumentPosition(expectNode(args, 0, false).unwrapNode());
            case "isSameNode" -> (ProxyExecutable) args -> {
                JsNodeLike other = expectNode(args, 0, true);
                return other != null && element.isSameNode(other.unwrapNode());
            };
            case "isEqualNode" -> (ProxyExecutable) args -> {
                JsNodeLike other = expectNode(args, 0, true);
                return other != null && element.isEqualNode(other.unwrapNode());
            };
            case "cloneNode" -> (ProxyExecutable) args -> document.wrap(element.cloneNode(
                    args.length > 0 && args[0].asBoolean()));
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
            case "DOCUMENT_POSITION_DISCONNECTED" -> com.browicy.engine.dom.Node.DOCUMENT_POSITION_DISCONNECTED;
            case "DOCUMENT_POSITION_PRECEDING" -> com.browicy.engine.dom.Node.DOCUMENT_POSITION_PRECEDING;
            case "DOCUMENT_POSITION_FOLLOWING" -> com.browicy.engine.dom.Node.DOCUMENT_POSITION_FOLLOWING;
            case "DOCUMENT_POSITION_CONTAINS" -> com.browicy.engine.dom.Node.DOCUMENT_POSITION_CONTAINS;
            case "DOCUMENT_POSITION_CONTAINED_BY" -> com.browicy.engine.dom.Node.DOCUMENT_POSITION_CONTAINED_BY;
            case "DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC" -> com.browicy.engine.dom.Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC;
            default -> null;
        };
    }

    @Override
    public void putMember(String key, Value value) {
        switch (key) {
            case "textContent" -> element.setTextContent(toText(value));
            case "id" -> element.setAttribute("id", toText(value));
            case "className" -> element.setAttribute("class", toText(value));
            case "name" -> element.setAttribute("name", toText(value));
            case "type" -> element.setAttribute("type", toText(value).toLowerCase(Locale.ROOT));
            case "value" -> element.setValueState(toText(value));
            case "checked" -> setChecked(value.asBoolean());
            case "defaultChecked" -> booleanAttribute("checked", value.asBoolean());
            case "selected", "defaultSelected" -> booleanAttribute("selected", value.asBoolean());
            case "selectedIndex" -> setSelectedIndex(value.asInt());
            default -> throw new UnsupportedOperationException(
                    "Eigenschaft nicht unterstützt oder schreibgeschützt: " + key);
        }
    }

    private String tag() { return element.getTagName().toLowerCase(Locale.ROOT); }
    private JsHtmlCollection collection(java.util.function.Supplier<List<Element>> query) {
        return new JsHtmlCollection(query, document);
    }
    private String inputType() {
        String type = element.getAttribute("type");
        if (type == null || type.isEmpty()) return "button".equals(tag()) ? "submit" : "input".equals(tag()) ? "text" : "";
        return type.toLowerCase(Locale.ROOT);
    }
    private String value() {
        String value = element.getValueState();
        return value == null ? ("option".equals(tag()) ? element.getTextContent() : "") : value;
    }
    private void setChecked(boolean checked) {
        element.setCheckedState(checked);
        if (!checked || !"radio".equals(inputType())) return;
        Element form = formOwner();
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) return;
        com.browicy.engine.dom.Node root = form == null ? element.getOwnerDocument() : form;
        if (root == null) return;
        for (Element candidate : descendants(root)) {
            if (candidate != element && "input".equals(candidate.getTagName())
                    && "radio".equalsIgnoreCase(orEmpty(candidate.getAttribute("type")))
                    && name.equals(candidate.getAttribute("name"))
                    && sameFormOwner(candidate, form)) {
                candidate.setCheckedState(false);
            }
        }
    }
    private static List<Element> descendants(com.browicy.engine.dom.Node root) {
        List<Element> result = new ArrayList<>();
        collectDescendants(root, result);
        return result;
    }
    private static void collectDescendants(com.browicy.engine.dom.Node root, List<Element> result) {
        for (com.browicy.engine.dom.Node child : root.getChildren()) if (child instanceof Element e) {
            result.add(e); collectDescendants(e, result);
        }
    }
    private static boolean sameFormOwner(Element candidate, Element expected) {
        for (com.browicy.engine.dom.Node node = candidate.getParent(); node != null; node = node.getParent()) {
            if (node instanceof Element e && "form".equals(e.getTagName())) return e == expected;
        }
        return expected == null;
    }
    private void booleanAttribute(String name, boolean enabled) {
        if (enabled) element.setAttribute(name, name); else element.removeAttribute(name);
    }
    private List<Element> formControls() {
        if (!"form".equals(tag())) return List.of();
        return element.getElementsByTagName("*").stream().filter(e ->
                List.of("button", "fieldset", "input", "object", "output", "select", "textarea").contains(e.getTagName())).toList();
    }
    private Element formOwner() {
        for (com.browicy.engine.dom.Node node = element.getParent(); node != null; node = node.getParent())
            if (node instanceof Element e && "form".equals(e.getTagName())) return e;
        return null;
    }
    private List<Element> options() { return element.getElementsByTagName("option"); }
    private int selectedIndex() {
        List<Element> options = options();
        for (int i = 0; i < options.size(); i++) if (options.get(i).hasAttribute("selected")) return i;
        return options.isEmpty() ? -1 : 0;
    }
    private void setSelectedIndex(int selected) {
        List<Element> options = options();
        for (int i = 0; i < options.size(); i++) {
            if (i == selected) options.get(i).setAttribute("selected", "selected"); else options.get(i).removeAttribute("selected");
        }
    }
    private Element direct(String wanted) { return directAll(wanted).stream().findFirst().orElse(null); }
    private List<Element> directAll(String wanted) {
        return element.getChildElements().stream().filter(e -> wanted.equals(e.getTagName())).toList();
    }
    private List<Element> rows() {
        if ("tr".equals(tag())) return List.of();
        if (!"table".equals(tag())) return directAll("tr");
        List<Element> rows = new ArrayList<>();
        for (Element child : element.getChildElements()) {
            if ("tr".equals(child.getTagName())) rows.add(child);
            else if (List.of("thead", "tbody", "tfoot").contains(child.getTagName())) rows.addAll(child.getChildElements().stream().filter(e -> "tr".equals(e.getTagName())).toList());
        }
        return rows;
    }
    private List<Element> cells() { return directAll("td").isEmpty() ? directAll("th") : element.getChildElements().stream().filter(e -> "td".equals(e.getTagName()) || "th".equals(e.getTagName())).toList(); }
    private Element createTablePart(String name, int index) {
        Element existing = direct(name); if (existing != null) return existing;
        Element created = new Element(name);
        com.browicy.engine.dom.Node ref = index < element.getChildren().size() ? element.getChildren().get(index) : null;
        element.insertBefore(created, ref); return created;
    }
    private int afterCaption() { return direct("caption") == null ? 0 : element.getChildren().indexOf(direct("caption")) + 1; }
    private ProxyExecutable removeTablePart(String name) { return args -> { Element part = direct(name); if (part != null) element.removeChild(part); return null; }; }
    private Element insertRow(int index) {
        List<Element> rows = rows(); if (index < -1 || index > rows.size()) throw new IndexOutOfBoundsException();
        Element row = new Element("tr");
        if (index >= 0 && index < rows.size()) rows.get(index).getParent().insertBefore(row, rows.get(index));
        else if ("table".equals(tag())) { Element body = direct("tbody"); (body == null ? element : body).appendChild(row); }
        else element.appendChild(row);
        return row;
    }
    private Element insertCell(int index) {
        List<Element> cells = cells(); if (index < -1 || index > cells.size()) throw new IndexOutOfBoundsException();
        Element cell = new Element("td"); element.insertBefore(cell, index >= 0 && index < cells.size() ? cells.get(index) : null); return cell;
    }
    private void deleteFrom(List<Element> values, int index) {
        if (index == -1) index = values.size() - 1;
        if (index < 0 || index >= values.size()) throw new IndexOutOfBoundsException();
        values.get(index).getParent().removeChild(values.get(index));
    }
    private int rowIndex(boolean section) {
        if (!"tr".equals(tag())) return -1;
        Element parent = element.getParent() instanceof Element e ? e : null;
        if (parent == null) return -1;
        if (section) return parent.getChildElements().stream().filter(e -> "tr".equals(e.getTagName())).toList().indexOf(element);
        for (com.browicy.engine.dom.Node n = parent; n != null; n = n.getParent()) if (n instanceof Element e && "table".equals(e.getTagName())) return new JsElement(e, document).rows().indexOf(element);
        return -1;
    }
    private int cellIndex() { return element.getParent() instanceof Element e ? e.getChildElements().stream().filter(c -> "td".equals(c.getTagName()) || "th".equals(c.getTagName())).toList().indexOf(element) : -1; }
    private void addOption(Value[] args) {
        JsNodeLike option = expectNode(args, 0, false); JsNodeLike before = args.length < 2 || args[1].isNull() ? null : expectNode(args, 1, true);
        element.insertBefore(option.unwrapNode(), before == null ? null : before.unwrapNode());
    }
    private void removeOption(Value[] args) { int index = indexArg(args, 0, -2); List<Element> values = options(); if (index >= 0 && index < values.size()) values.get(index).getParent().removeChild(values.get(index)); }
    private static int indexArg(Value[] args, int index, int defaultValue) { return index >= args.length ? defaultValue : args[index].asInt(); }

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

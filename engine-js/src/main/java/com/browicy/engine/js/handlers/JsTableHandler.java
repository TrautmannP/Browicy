package com.browicy.engine.js.handlers;

import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import com.browicy.engine.js.JsDocument;
import com.browicy.engine.js.JsElement;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.util.ArrayList;
import java.util.List;

import static com.browicy.engine.js.handlers.JsMemberHandler.indexArg;
import static com.browicy.engine.js.handlers.JsMemberHandler.tag;

public final class JsTableHandler implements JsMemberHandler {

    private static final List<String> KEYS = List.of(
            "caption", "tHead", "tFoot", "tBodies", "rows", "cells",
            "rowIndex", "sectionRowIndex", "cellIndex",
            "createCaption", "deleteCaption", "createTHead", "deleteTHead",
            "createTFoot", "deleteTFoot",
            "insertRow", "deleteRow", "insertCell", "deleteCell");

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
        Element el = element.unwrap();
        return switch (key) {
            case "caption" -> doc.wrap(direct(el, "caption"));
            case "tHead" -> doc.wrap(direct(el, "thead"));
            case "tFoot" -> doc.wrap(direct(el, "tfoot"));
            case "tBodies" -> doc.htmlCollection(() -> directAll(el, "tbody"));
            case "rows" -> doc.htmlCollection(() -> rows(el));
            case "cells" -> doc.htmlCollection(() -> cells(el));
            case "rowIndex" -> rowIndex(el, false);
            case "sectionRowIndex" -> rowIndex(el, true);
            case "cellIndex" -> cellIndex(el);
            case "createCaption" -> (ProxyExecutable) args ->
                    doc.wrap(createTablePart(el, "caption", 0));
            case "deleteCaption" -> removeTablePart(el, "caption");
            case "createTHead" -> (ProxyExecutable) args ->
                    doc.wrap(createTablePart(el, "thead", afterCaption(el)));
            case "deleteTHead" -> removeTablePart(el, "thead");
            case "createTFoot" -> (ProxyExecutable) args ->
                    doc.wrap(createTablePart(el, "tfoot", el.getChildren().size()));
            case "deleteTFoot" -> removeTablePart(el, "tfoot");
            case "insertRow" -> (ProxyExecutable) args ->
                    doc.wrap(insertRow(el, indexArg(args, 0, -1)));
            case "deleteRow" -> (ProxyExecutable) args -> {
                deleteFrom(el, rows(el), indexArg(args, 0, -2));
                return null;
            };
            case "insertCell" -> (ProxyExecutable) args ->
                    doc.wrap(insertCell(el, indexArg(args, 0, -1)));
            case "deleteCell" -> (ProxyExecutable) args -> {
                deleteFrom(el, cells(el), indexArg(args, 0, -2));
                return null;
            };
            default -> null;
        };
    }

    @Override
    public boolean set(String key, Value value, JsElement element, JsDocument doc) {
        return false;
    }

    private static Element direct(Element element, String wanted) {
        return directAll(element, wanted).stream().findFirst().orElse(null);
    }

    private static List<Element> directAll(Element element, String wanted) {
        return element.getChildElements().stream()
                .filter(e -> wanted.equals(e.getTagName())).toList();
    }

    private static List<Element> rows(Element element) {
        if ("tr".equals(tag(element))) {
            return List.of();
        }
        if (!"table".equals(tag(element))) {
            return directAll(element, "tr");
        }
        List<Element> rows = new ArrayList<>();
        for (Element child : element.getChildElements()) {
            if ("tr".equals(child.getTagName())) {
                rows.add(child);
            } else if (List.of("thead", "tbody", "tfoot").contains(child.getTagName())) {
                rows.addAll(child.getChildElements().stream()
                        .filter(e -> "tr".equals(e.getTagName())).toList());
            }
        }
        return rows;
    }

    private static List<Element> cells(Element element) {
        return directAll(element, "td").isEmpty()
                ? directAll(element, "th")
                : element.getChildElements().stream()
                .filter(e -> "td".equals(e.getTagName()) || "th".equals(e.getTagName()))
                .toList();
    }

    private static Element createTablePart(Element element, String name, int index) {
        Element existing = direct(element, name);
        if (existing != null) {
            return existing;
        }
        Element created = new Element(name);
        Node ref = index < element.getChildren().size() ? element.getChildren().get(index) : null;
        element.insertBefore(created, ref);
        return created;
    }

    private static int afterCaption(Element element) {
        return direct(element, "caption") == null
                ? 0 : element.getChildren().indexOf(direct(element, "caption")) + 1;
    }

    private static ProxyExecutable removeTablePart(Element element, String name) {
        return args -> {
            Element part = direct(element, name);
            if (part != null) {
                element.removeChild(part);
            }
            return null;
        };
    }

    private static Element insertRow(Element element, int index) {
        List<Element> rows = rows(element);
        if (index < -1 || index > rows.size()) {
            throw new IndexOutOfBoundsException();
        }
        Element row = new Element("tr");
        if (index >= 0 && index < rows.size()) {
            rows.get(index).getParent().insertBefore(row, rows.get(index));
        } else if ("table".equals(tag(element))) {
            Element body = direct(element, "tbody");
            (body == null ? element : body).appendChild(row);
        } else {
            element.appendChild(row);
        }
        return row;
    }

    private static Element insertCell(Element element, int index) {
        List<Element> cells = cells(element);
        if (index < -1 || index > cells.size()) {
            throw new IndexOutOfBoundsException();
        }
        Element cell = new Element("td");
        element.insertBefore(cell,
                index >= 0 && index < cells.size() ? cells.get(index) : null);
        return cell;
    }

    private static void deleteFrom(Element element, List<Element> values, int index) {
        if (index == -1) {
            index = values.size() - 1;
        }
        if (index < 0 || index >= values.size()) {
            throw new IndexOutOfBoundsException();
        }
        values.get(index).getParent().removeChild(values.get(index));
    }

    private static int rowIndex(Element element, boolean section) {
        if (!"tr".equals(tag(element))) {
            return -1;
        }
        Element parent = element.getParent() instanceof Element e ? e : null;
        if (parent == null) {
            return -1;
        }
        if (section) {
            return parent.getChildElements().stream()
                    .filter(e -> "tr".equals(e.getTagName())).toList().indexOf(element);
        }
        for (Node n = parent; n != null; n = n.getParent()) {
            if (n instanceof Element e && "table".equals(e.getTagName())) {
                return rows(e).indexOf(element);
            }
        }
        return -1;
    }

    private static int cellIndex(Element element) {
        return element.getParent() instanceof Element e
                ? e.getChildElements().stream()
                .filter(c -> "td".equals(c.getTagName()) || "th".equals(c.getTagName()))
                .toList().indexOf(element)
                : -1;
    }
}

package com.browicy.engine.js.handlers;

import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import com.browicy.engine.js.JsDocument;
import com.browicy.engine.js.JsElement;
import com.browicy.engine.js.JsNodeLike;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.browicy.engine.js.handlers.JsMemberHandler.expectNode;
import static com.browicy.engine.js.handlers.JsMemberHandler.indexArg;
import static com.browicy.engine.js.handlers.JsMemberHandler.orEmpty;
import static com.browicy.engine.js.handlers.JsMemberHandler.tag;
import static com.browicy.engine.js.handlers.JsMemberHandler.toText;

public final class JsFormHandler implements JsMemberHandler {

    private static final List<String> KEYS = List.of(
            "type", "value", "checked", "defaultChecked", "indeterminate",
            "selected", "defaultSelected", "disabled",
            "form", "elements", "length", "selectedIndex", "options", "add", "remove");

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
            case "type" -> inputType(el);
            case "value" -> value(el);
            case "checked" -> el.isCheckedState();
            case "defaultChecked" -> el.hasAttribute("checked");
            case "indeterminate" -> el.isIndeterminate();
            case "selected", "defaultSelected" -> el.hasAttribute("selected");
            case "disabled" -> el.hasAttribute("disabled");
            case "form" -> doc.wrap(formOwner(el));
            case "elements" -> doc.htmlCollection(() -> formControls(el));
            case "length" -> "form".equals(tag(el)) ? formControls(el).size()
                    : "select".equals(tag(el)) ? options(el).size() : 0;
            case "selectedIndex" -> selectedIndex(el);
            case "options" -> doc.htmlCollection(() -> options(el));
            case "add" -> (ProxyExecutable) args -> {
                addOption(el, args);
                return null;
            };
            case "remove" -> (ProxyExecutable) args -> {
                if (args.length == 0) {
                    el.remove();
                } else {
                    removeOption(el, args);
                }
                return null;
            };
            default -> null;
        };
    }

    @Override
    public boolean set(String key, Value value, JsElement element, JsDocument doc) {
        Element el = element.unwrap();
        switch (key) {
            case "type" -> el.setAttribute("type", toText(value).toLowerCase(Locale.ROOT));
            case "value" -> el.setValueState(toText(value));
            case "checked" -> setChecked(el, value.asBoolean());
            case "defaultChecked" -> booleanAttribute(el, "checked", value.asBoolean());
            case "indeterminate" -> el.setIndeterminate(value.asBoolean());
            case "selected", "defaultSelected" -> booleanAttribute(el, "selected", value.asBoolean());
            case "disabled" -> booleanAttribute(el, "disabled", value.asBoolean());
            case "selectedIndex" -> setSelectedIndex(el, value.asInt());
            default -> {
                return false;
            }
        }
        return true;
    }

    private static String inputType(Element element) {
        String type = element.getAttribute("type");
        if (type == null || type.isEmpty()) {
            return "button".equals(tag(element)) ? "submit"
                    : "input".equals(tag(element)) ? "text" : "";
        }
        return type.toLowerCase(Locale.ROOT);
    }

    private static String value(Element element) {
        String value = element.getValueState();
        return value == null ? ("option".equals(tag(element)) ? element.getTextContent() : "") : value;
    }

    private static void setChecked(Element element, boolean checked) {
        element.setCheckedState(checked);
        if (!checked || !"radio".equals(inputType(element))) {
            return;
        }
        Element form = formOwner(element);
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }
        Node root = form == null ? element.getOwnerDocument() : form;
        if (root == null) {
            return;
        }
        for (Element candidate : descendants(root)) {
            if (candidate != element && "input".equals(candidate.getTagName())
                    && "radio".equalsIgnoreCase(orEmpty(candidate.getAttribute("type")))
                    && name.equals(candidate.getAttribute("name"))
                    && sameFormOwner(candidate, form)) {
                candidate.setCheckedState(false);
            }
        }
    }

    private static List<Element> descendants(Node root) {
        List<Element> result = new ArrayList<>();
        collectDescendants(root, result);
        return result;
    }

    private static void collectDescendants(Node root, List<Element> result) {
        for (Node child : root.getChildren()) {
            if (child instanceof Element e) {
                result.add(e);
                collectDescendants(e, result);
            }
        }
    }

    private static boolean sameFormOwner(Element candidate, Element expected) {
        for (Node node = candidate.getParent(); node != null; node = node.getParent()) {
            if (node instanceof Element e && "form".equals(e.getTagName())) {
                return e == expected;
            }
        }
        return expected == null;
    }

    private static void booleanAttribute(Element element, String name, boolean enabled) {
        if (enabled) {
            element.setAttribute(name, name);
        } else {
            element.removeAttribute(name);
        }
    }

    private static List<Element> formControls(Element element) {
        if (!"form".equals(tag(element))) {
            return List.of();
        }
        return element.getElementsByTagName("*").stream().filter(e ->
                List.of("button", "fieldset", "input", "object", "output", "select", "textarea")
                        .contains(e.getTagName())).toList();
    }

    private static Element formOwner(Element element) {
        for (Node node = element.getParent(); node != null; node = node.getParent()) {
            if (node instanceof Element e && "form".equals(e.getTagName())) {
                return e;
            }
        }
        return null;
    }

    private static List<Element> options(Element element) {
        return element.getElementsByTagName("option");
    }

    private static int selectedIndex(Element element) {
        List<Element> options = options(element);
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).hasAttribute("selected")) {
                return i;
            }
        }
        return options.isEmpty() ? -1 : 0;
    }

    private static void setSelectedIndex(Element element, int selected) {
        List<Element> options = options(element);
        for (int i = 0; i < options.size(); i++) {
            if (i == selected) {
                options.get(i).setAttribute("selected", "selected");
            } else {
                options.get(i).removeAttribute("selected");
            }
        }
    }

    private static void addOption(Element element, Value[] args) {
        JsNodeLike option = expectNode(args, 0, false);
        JsNodeLike before = args.length < 2 || args[1].isNull() ? null : expectNode(args, 1, true);
        element.insertBefore(option.unwrapNode(), before == null ? null : before.unwrapNode());
    }

    private static void removeOption(Element element, Value[] args) {
        int index = indexArg(args, 0, -2);
        List<Element> values = options(element);
        if (index >= 0 && index < values.size()) {
            values.get(index).getParent().removeChild(values.get(index));
        }
    }
}

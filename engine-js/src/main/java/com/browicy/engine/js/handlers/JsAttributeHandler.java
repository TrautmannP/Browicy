package com.browicy.engine.js.handlers;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import com.browicy.engine.html.HtmlParser;
import com.browicy.engine.html.HtmlSerializer;
import com.browicy.engine.js.JsDocument;
import com.browicy.engine.js.JsElement;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.util.List;

import static com.browicy.engine.js.handlers.JsMemberHandler.asString;
import static com.browicy.engine.js.handlers.JsMemberHandler.orEmpty;
import static com.browicy.engine.js.handlers.JsMemberHandler.tag;
import static com.browicy.engine.js.handlers.JsMemberHandler.toText;

public final class JsAttributeHandler implements JsMemberHandler {

    private static final List<String> KEYS = List.of(
            "id", "className", "classList", "dataset", "name", "nodeValue",
            "textContent", "innerHTML", "style", "sheet",
            "getAttribute", "setAttribute", "removeAttribute", "hasAttribute",
            "toggleAttribute", "getElementsByTagName");

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
            case "id" -> orEmpty(el.getAttribute("id"));
            case "className" -> orEmpty(el.getAttribute("class"));
            case "classList" -> element.classList();
            case "dataset" -> element.dataset();
            case "name" -> orEmpty(el.getAttribute("name"));
            case "nodeValue" -> null;
            case "textContent" -> el.getTextContent();
            case "innerHTML" -> HtmlSerializer.innerHtml(el);
            case "style" -> element.style();
            case "sheet" -> "style".equals(tag(el)) ? element.sheet() : null;
            case "getAttribute" -> (ProxyExecutable) args -> el.getAttribute(asString(args, 0));
            case "setAttribute" -> (ProxyExecutable) args -> {
                el.setAttribute(asString(args, 0), asString(args, 1));
                return null;
            };
            case "removeAttribute" -> (ProxyExecutable) args -> {
                el.removeAttribute(asString(args, 0));
                return null;
            };
            case "hasAttribute" -> (ProxyExecutable) args -> el.hasAttribute(asString(args, 0));
            case "toggleAttribute" -> (ProxyExecutable) args -> toggleAttribute(el, args);
            case "getElementsByTagName" -> (ProxyExecutable) args ->
                    doc.htmlCollection(() -> el.getElementsByTagName(asString(args, 0)));
            default -> null;
        };
    }

    @Override
    public boolean set(String key, Value value, JsElement element, JsDocument doc) {
        Element el = element.unwrap();
        switch (key) {
            case "textContent" -> {
                el.setTextContent(toText(value));
                element.styleContentMaybeChanged();
            }
            case "innerHTML" -> {
                setInnerHtml(el, toText(value));
                element.styleContentMaybeChanged();
            }
            case "style" -> element.style().setCssText(toText(value));
            case "id" -> el.setAttribute("id", toText(value));
            case "className" -> el.setAttribute("class", toText(value));
            case "name" -> el.setAttribute("name", toText(value));
            default -> {
                return false;
            }
        }
        return true;
    }

    private static Object toggleAttribute(Element element, Value[] args) {
        String attribute = asString(args, 0);
        boolean force = args.length > 1 && !args[1].isNull() && args[1].asBoolean();
        boolean present = element.hasAttribute(attribute);
        boolean enabled = args.length > 1 && !args[1].isNull() ? force : !present;
        if (enabled && !present) {
            element.setAttribute(attribute, "");
        } else if (!enabled && present) {
            element.removeAttribute(attribute);
        }
        return enabled;
    }

    private static void setInnerHtml(Element element, String html) {
        element.clearChildren();
        Document fragment = new HtmlParser().parse(
                "<body>" + html + "</body>", element.getOwnerDocument().getUrl());
        Element body = fragment.getBody();
        if (body == null) {
            return;
        }
        for (Node child : List.copyOf(body.getChildren())) {
            element.appendChild(child);
        }
    }
}

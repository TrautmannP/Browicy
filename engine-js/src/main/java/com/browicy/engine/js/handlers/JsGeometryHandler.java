package com.browicy.engine.js.handlers;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import com.browicy.engine.js.JsDocument;
import com.browicy.engine.js.JsElement;
import com.browicy.engine.js.LayoutElementMetrics;
import com.browicy.engine.js.LayoutMetricsAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsGeometryHandler implements JsMemberHandler {

    private static final List<String> KEYS = List.of(
            "getBoundingClientRect", "getClientRects",
            "offsetWidth", "offsetHeight", "clientWidth", "clientHeight",
            "offsetLeft", "offsetTop");

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
            case "getBoundingClientRect" -> (ProxyExecutable) args -> boundingClientRect(el, doc);
            case "getClientRects" -> (ProxyExecutable) args -> clientRects(el, doc);
            case "offsetWidth" -> (double) metrics(el, doc).width();
            case "offsetHeight" -> (double) metrics(el, doc).height();
            case "clientWidth" -> (double) metrics(el, doc).clientWidth();
            case "clientHeight" -> (double) metrics(el, doc).clientHeight();
            case "offsetLeft" -> (double) offsetLeft(el, doc);
            case "offsetTop" -> (double) offsetTop(el, doc);
            default -> null;
        };
    }

    @Override
    public boolean set(String key, Value value, JsElement element, JsDocument doc) {
        return false;
    }

    private static LayoutElementMetrics metrics(Element element, JsDocument doc) {
        return doc.layoutMetrics().metricsFor(element);
    }

    private static Object boundingClientRect(Element element, JsDocument doc) {
        LayoutElementMetrics metrics = metrics(element, doc);
        if (!metrics.rendered()) {
            return ProxyObject.fromMap(rectMap(0, 0, 0, 0));
        }
        return ProxyObject.fromMap(rectMap(metrics.left(), metrics.top(),
                metrics.width(), metrics.height()));
    }

    private static Object clientRects(Element element, JsDocument doc) {
        LayoutElementMetrics metrics = metrics(element, doc);
        if (!metrics.rendered()) {
            return ProxyArray.fromArray();
        }
        ProxyObject rect = ProxyObject.fromMap(Map.of(
                "x", (double) metrics.left(), "y", (double) metrics.top(),
                "width", (double) metrics.width(), "height", (double) metrics.height(),
                "top", (double) metrics.top(), "right", (double) metrics.right(),
                "bottom", (double) metrics.bottom(), "left", (double) metrics.left()));
        return ProxyArray.fromArray(rect);
    }

    private static Map<String, Object> rectMap(double x, double y, double width, double height) {
        Map<String, Object> rect = new LinkedHashMap<>();
        rect.put("x", x);
        rect.put("y", y);
        rect.put("width", width);
        rect.put("height", height);
        rect.put("top", y);
        rect.put("right", x + width);
        rect.put("bottom", y + height);
        rect.put("left", x);
        rect.put("toJSON", (ProxyExecutable) inner -> {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("x", x);
            json.put("y", y);
            json.put("width", width);
            json.put("height", height);
            json.put("top", y);
            json.put("right", x + width);
            json.put("bottom", y + height);
            json.put("left", x);
            return json;
        });
        return rect;
    }

    private static float offsetLeft(Element element, JsDocument doc) {
        LayoutElementMetrics own = metrics(element, doc);
        if (!own.rendered()) {
            return 0;
        }
        Element parent = offsetParentElement(element);
        if (parent == null) {
            return 0;
        }
        LayoutElementMetrics parentMetrics = doc.layoutMetrics().metricsFor(parent);
        return own.left() - (parentMetrics.left() + parentMetrics.borderLeft());
    }

    private static float offsetTop(Element element, JsDocument doc) {
        LayoutElementMetrics own = metrics(element, doc);
        if (!own.rendered()) {
            return 0;
        }
        Element parent = offsetParentElement(element);
        if (parent == null) {
            return 0;
        }
        LayoutElementMetrics parentMetrics = doc.layoutMetrics().metricsFor(parent);
        return own.top() - (parentMetrics.top() + parentMetrics.borderTop());
    }

    private static Element offsetParentElement(Element element) {
        if ("fixed".equals(positionOf(element))) {
            return null;
        }
        String tag = JsMemberHandler.tag(element);
        if ("body".equals(tag) || "html".equals(tag)) {
            return null;
        }
        for (Node node = element.getParent(); node != null; node = node.getParent()) {
            if (node instanceof Element ancestor && !"static".equals(positionOf(ancestor))) {
                return ancestor;
            }
        }
        Document owner = element.getOwnerDocument();
        return owner == null ? null : owner.getBody();
    }

    private static String positionOf(Element candidate) {
        return candidate.getComputedStyles().getOrDefault("position", "static");
    }
}

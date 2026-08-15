package com.browicy.engine.js.handlers;

import com.browicy.engine.js.JsDocument;
import com.browicy.engine.js.JsElement;
import com.browicy.engine.js.JsEventTarget;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.util.List;

public final class JsEventHandler implements JsMemberHandler {

    private static final List<String> KEYS = List.of(
            JsEventTarget.ADD_EVENT_LISTENER,
            JsEventTarget.REMOVE_EVENT_LISTENER,
            JsEventTarget.DISPATCH_EVENT);

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
        return switch (key) {
            case JsEventTarget.ADD_EVENT_LISTENER -> JsEventTarget.addEventListener(element.unwrap(), doc);
            case JsEventTarget.REMOVE_EVENT_LISTENER -> JsEventTarget.removeEventListener(element.unwrap(), doc);
            case JsEventTarget.DISPATCH_EVENT -> JsEventTarget.dispatchEvent(element.unwrap());
            default -> null;
        };
    }

    @Override
    public boolean set(String key, Value value, JsElement element, JsDocument doc) {
        return false;
    }
}

package com.browicy.engine.js.handlers;

import com.browicy.engine.js.JsDocument;
import com.browicy.engine.js.JsElement;
import com.browicy.engine.js.JsEventTarget;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.util.List;

public final class JsInteractiveHandler implements JsMemberHandler {

    private static final List<String> KEYS = List.of(
            "click", "focus", "blur", "scrollIntoView", "play", "pause", "load");

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
            case "click" -> JsEventTarget.click(element.unwrap());
            case "focus" -> (ProxyExecutable) args -> {
                if (element.unwrap().getOwnerDocument() != null) {
                    element.unwrap().getOwnerDocument().setFocusedElement(element.unwrap());
                }
                return null;
            };
            case "blur" -> (ProxyExecutable) args -> {
                if (element.unwrap().isFocused()) {
                    element.unwrap().getOwnerDocument().setFocusedElement(null);
                }
                return null;
            };
            case "scrollIntoView", "play", "pause", "load" -> (ProxyExecutable) args -> null;
            default -> null;
        };
    }

    @Override
    public boolean set(String key, Value value, JsElement element, JsDocument doc) {
        return false;
    }
}

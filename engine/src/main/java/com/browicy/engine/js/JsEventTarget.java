package com.browicy.engine.js;

import com.browicy.engine.dom.Event;
import com.browicy.engine.dom.Node;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

/** Gemeinsame JavaScript-Anbindung der EventTarget-Methoden aller DOM-Knoten. */
final class JsEventTarget {

    static final String ADD_EVENT_LISTENER = "addEventListener";
    static final String REMOVE_EVENT_LISTENER = "removeEventListener";
    static final String DISPATCH_EVENT = "dispatchEvent";

    private JsEventTarget() {
    }

    static ProxyExecutable addEventListener(Node target, JsDocument document) {
        return args -> {
            Value callback = callback(args, 1);
            if (callback != null) {
                document.addEventListener(target, asString(args, 0), callback, capture(args, 2));
            }
            return null;
        };
    }

    static ProxyExecutable removeEventListener(Node target, JsDocument document) {
        return args -> {
            Value callback = callback(args, 1);
            if (callback != null) {
                document.removeEventListener(target, asString(args, 0), callback, capture(args, 2));
            }
            return null;
        };
    }

    static ProxyExecutable dispatchEvent(Node target) {
        return args -> target.dispatchEvent(JsEvent.expect(args, 0).unwrap());
    }

    static ProxyExecutable click(Node target) {
        return args -> {
            target.dispatchEvent(new Event("click", true, true));
            return null;
        };
    }

    private static Value callback(Value[] args, int index) {
        if (index >= args.length || args[index].isNull()) {
            return null;
        }
        Value callback = args[index];
        boolean handleEventObject = callback.hasMembers()
                && callback.hasMember("handleEvent")
                && callback.getMember("handleEvent").canExecute();
        if (!callback.canExecute() && !handleEventObject) {
            throw new IllegalArgumentException("EventListener muss aufrufbar sein oder handleEvent besitzen");
        }
        return callback;
    }

    private static boolean capture(Value[] args, int index) {
        if (index >= args.length || args[index].isNull()) {
            return false;
        }
        Value options = args[index];
        if (options.isBoolean()) {
            return options.asBoolean();
        }
        return options.hasMembers() && options.hasMember("capture")
                && options.getMember("capture").asBoolean();
    }

    private static String asString(Value[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Argument " + index + " fehlt");
        }
        return args[index].isString() ? args[index].asString() : args[index].toString();
    }
}

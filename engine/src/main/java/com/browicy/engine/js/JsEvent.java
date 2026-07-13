package com.browicy.engine.js;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import com.browicy.engine.dom.Event;
import com.browicy.engine.dom.UiEvent;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class JsEvent implements ProxyObject {

    private static final List<String> MEMBERS = List.of(
            "type", "target", "currentTarget", "eventPhase", "bubbles", "cancelable",
            "defaultPrevented", "timeStamp", "isTrusted", "view", "detail",
            "NONE", "CAPTURING_PHASE", "AT_TARGET", "BUBBLING_PHASE",
            "stopPropagation", "stopImmediatePropagation", "preventDefault",
            "initEvent", "initUIEvent");

    private final Event event;
    private final JsDocument document;

    Event unwrap() {
        return event;
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "type" -> event.getType();
            case "target" -> document.wrap(event.getTarget());
            case "currentTarget" -> document.wrap(event.getCurrentTarget());
            case "eventPhase" -> event.getEventPhase();
            case "bubbles" -> event.isBubbles();
            case "cancelable" -> event.isCancelable();
            case "defaultPrevented" -> event.isDefaultPrevented();
            case "timeStamp" -> event.getTimeStamp();
            case "isTrusted" -> false;
            case "view" -> event instanceof UiEvent uiEvent ? uiEvent.getView() : null;
            case "detail" -> event instanceof UiEvent uiEvent ? uiEvent.getDetail() : 0;
            case "NONE" -> Event.NONE;
            case "CAPTURING_PHASE" -> Event.CAPTURING_PHASE;
            case "AT_TARGET" -> Event.AT_TARGET;
            case "BUBBLING_PHASE" -> Event.BUBBLING_PHASE;
            case "stopPropagation" -> (ProxyExecutable) args -> {
                event.stopPropagation();
                return null;
            };
            case "stopImmediatePropagation" -> (ProxyExecutable) args -> {
                event.stopImmediatePropagation();
                return null;
            };
            case "preventDefault" -> (ProxyExecutable) args -> {
                event.preventDefault();
                return null;
            };
            case "initEvent" -> (ProxyExecutable) args -> {
                event.initEvent(asString(args, 0), asBoolean(args, 1), asBoolean(args, 2));
                return null;
            };
            case "initUIEvent" -> (ProxyExecutable) args -> {
                if (!(event instanceof UiEvent uiEvent)) {
                    throw new IllegalStateException("Dieses Event ist kein UIEvent");
                }
                uiEvent.initUiEvent(asString(args, 0), asBoolean(args, 1), asBoolean(args, 2),
                        asNullableObject(args, 3), asInt(args, 4));
                return null;
            };
            default -> null;
        };
    }

    @Override
    public Object getMemberKeys() {
        return MEMBERS.toArray();
    }

    @Override
    public boolean hasMember(String key) {
        return MEMBERS.contains(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("Event-Eigenschaft ist schreibgeschützt: " + key);
    }

    static JsEvent expect(Value[] args, int index) {
        if (index < args.length && args[index].isProxyObject()
                && args[index].asProxyObject() instanceof JsEvent event) {
            return event;
        }
        throw new IllegalArgumentException("Es wird ein DOM-Event erwartet");
    }

    private static String asString(Value[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Argument " + index + " fehlt");
        }
        return args[index].isString() ? args[index].asString() : args[index].toString();
    }

    private static boolean asBoolean(Value[] args, int index) {
        return index < args.length && !args[index].isNull() && args[index].asBoolean();
    }

    private static int asInt(Value[] args, int index) {
        return index < args.length && !args[index].isNull() ? args[index].asInt() : 0;
    }

    private static Object asNullableObject(Value[] args, int index) {
        return index >= args.length || args[index].isNull() ? null : args[index];
    }

    @Override
    public String toString() {
        return event instanceof UiEvent ? "[object UIEvent]" : "[object Event]";
    }
}

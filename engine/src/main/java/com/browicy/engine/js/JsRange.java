package com.browicy.engine.js;

import com.browicy.engine.dom.Node;
import com.browicy.engine.dom.Range;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.List;

/** JavaScript-Proxy für eine live aktualisierte DOM-Range. */
final class JsRange implements ProxyObject {

    private static final List<String> MEMBERS = List.of(
            "startContainer", "startOffset", "endContainer", "endOffset",
            "collapsed", "commonAncestorContainer",
            "START_TO_START", "START_TO_END", "END_TO_END", "END_TO_START",
            "setStart", "setEnd", "setStartBefore", "setStartAfter",
            "setEndBefore", "setEndAfter", "collapse", "selectNode",
            "selectNodeContents", "extractContents", "cloneContents",
            "deleteContents", "insertNode", "surroundContents", "cloneRange",
            "compareBoundaryPoints", "detach", "toString");

    private final Range range;
    private final JsDocument document;

    JsRange(Range range, JsDocument document) {
        this.range = range;
        this.document = document;
    }

    Range unwrap() {
        return range;
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "startContainer" -> document.wrap(range.getStartContainer());
            case "startOffset" -> range.getStartOffset();
            case "endContainer" -> document.wrap(range.getEndContainer());
            case "endOffset" -> range.getEndOffset();
            case "collapsed" -> range.isCollapsed();
            case "commonAncestorContainer" -> document.wrap(range.getCommonAncestorContainer());
            case "START_TO_START" -> Range.START_TO_START;
            case "START_TO_END" -> Range.START_TO_END;
            case "END_TO_END" -> Range.END_TO_END;
            case "END_TO_START" -> Range.END_TO_START;
            case "setStart" -> operation(args -> {
                range.setStart(expectNode(args, 0), asInt(args, 1));
                return null;
            });
            case "setEnd" -> operation(args -> {
                range.setEnd(expectNode(args, 0), asInt(args, 1));
                return null;
            });
            case "setStartBefore" -> operation(args -> {
                range.setStartBefore(expectNode(args, 0));
                return null;
            });
            case "setStartAfter" -> operation(args -> {
                range.setStartAfter(expectNode(args, 0));
                return null;
            });
            case "setEndBefore" -> operation(args -> {
                range.setEndBefore(expectNode(args, 0));
                return null;
            });
            case "setEndAfter" -> operation(args -> {
                range.setEndAfter(expectNode(args, 0));
                return null;
            });
            case "collapse" -> (ProxyExecutable) args -> {
                range.collapse(asBoolean(args, 0));
                return null;
            };
            case "selectNode" -> (ProxyExecutable) args -> {
                range.selectNode(expectNode(args, 0));
                return null;
            };
            case "selectNodeContents" -> (ProxyExecutable) args -> {
                range.selectNodeContents(expectNode(args, 0));
                return null;
            };
            case "extractContents" -> (ProxyExecutable) args -> document.wrap(range.extractContents());
            case "cloneContents" -> (ProxyExecutable) args -> document.wrap(range.cloneContents());
            case "deleteContents" -> (ProxyExecutable) args -> {
                range.deleteContents();
                return null;
            };
            case "insertNode" -> (ProxyExecutable) args -> {
                range.insertNode(expectNode(args, 0));
                return null;
            };
            case "surroundContents" -> operation(args -> {
                range.surroundContents(expectNode(args, 0));
                return null;
            });
            case "cloneRange" -> (ProxyExecutable) args -> new JsRange(range.cloneRange(), document);
            case "compareBoundaryPoints" -> (ProxyExecutable) args ->
                    range.compareBoundaryPoints((short) asInt(args, 0), expectRange(args, 1).range);
            case "detach" -> (ProxyExecutable) args -> {
                range.detach();
                return null;
            };
            case "toString" -> (ProxyExecutable) args -> range.toString();
            default -> null;
        };
    }

    private Object operation(ProxyExecutable operation) {
        return document.domOperation(operation);
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
        throw new UnsupportedOperationException("Range-Eigenschaft ist schreibgeschützt: " + key);
    }

    private static Node expectNode(Value[] args, int index) {
        return JsElement.expectNode(args, index, false).unwrapNode();
    }

    private static JsRange expectRange(Value[] args, int index) {
        if (index < args.length && args[index].isProxyObject()
                && args[index].asProxyObject() instanceof JsRange range) {
            return range;
        }
        throw new IllegalArgumentException("Es wird eine DOM-Range erwartet");
    }

    private static int asInt(Value[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Argument " + index + " fehlt");
        }
        return args[index].asInt();
    }

    private static boolean asBoolean(Value[] args, int index) {
        return index < args.length && !args[index].isNull() && args[index].asBoolean();
    }

    @Override
    public String toString() {
        return "[object Range]";
    }
}

package com.browicy.engine.js;

import com.browicy.engine.css.CssStyleSheet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

final class JsCssStyleSheet implements ProxyObject {

    private static final List<String> MEMBERS = List.of(
            "cssRules", "rules", "insertRule", "deleteRule", "ownerNode", "href");

    private final CssStyleSheet sheet;
    private final JsDocument document;
    private final Runnable mutationCallback;
    private final JsCssRuleList rules = new JsCssRuleList();

    JsCssStyleSheet(CssStyleSheet sheet, JsDocument document, Runnable mutationCallback) {
        this.sheet = Objects.requireNonNull(sheet, "sheet");
        this.document = Objects.requireNonNull(document, "document");
        this.mutationCallback = Objects.requireNonNull(mutationCallback, "mutationCallback");
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "cssRules", "rules" -> rules;
            case "ownerNode" -> document.wrap(sheet.ownerNode());
            case "href" -> sheet.href();
            case "insertRule" -> document.domOperation((ProxyExecutable) args -> {
                String rule = text(args, 0);
                int index = args.length < 2 ? 0 : index(args, 1);
                int inserted = sheet.insertRule(rule, index);
                mutationCallback.run();
                return inserted;
            });
            case "deleteRule" -> document.domOperation((ProxyExecutable) args -> {
                sheet.deleteRule(index(args, 0));
                mutationCallback.run();
                return null;
            });
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
        throw new UnsupportedOperationException("CSSStyleSheet ist schreibgeschuetzt");
    }

    private static String text(Value[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("CSS-Regel fehlt");
        }
        return args[index].isString() ? args[index].asString() : args[index].toString();
    }

    private static int index(Value[] args, int index) {
        if (index >= args.length || !args[index].fitsInInt()) {
            throw new IllegalArgumentException("CSS-Regelindex muss eine Ganzzahl sein");
        }
        return args[index].asInt();
    }

    @Override
    public String toString() {
        return "[object CSSStyleSheet]";
    }

    private final class JsCssRuleList implements ProxyObject {

        @Override
        public Object getMember(String key) {
            if ("length".equals(key)) {
                return sheet.ruleCount();
            }
            if ("item".equals(key)) {
                return (ProxyExecutable) args -> ruleAt(
                        args.length > 0 && args[0].fitsInInt() ? args[0].asInt() : -1);
            }
            try {
                return ruleAt(Integer.parseInt(key));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private Object ruleAt(int index) {
            return index >= 0 && index < sheet.ruleCount()
                    ? new JsCssRule(sheet.ruleText(index), JsCssStyleSheet.this) : null;
        }

        @Override
        public Object getMemberKeys() {
            List<String> keys = new ArrayList<>(List.of("length", "item"));
            for (int index = 0; index < sheet.ruleCount(); index++) {
                keys.add(Integer.toString(index));
            }
            return keys.toArray();
        }

        @Override
        public boolean hasMember(String key) {
            return "length".equals(key) || "item".equals(key) || getMember(key) != null;
        }

        @Override
        public void putMember(String key, Value value) {
            throw new UnsupportedOperationException("CSSRuleList ist schreibgeschuetzt");
        }

        @Override
        public String toString() {
            return "[object CSSRuleList]";
        }
    }
}

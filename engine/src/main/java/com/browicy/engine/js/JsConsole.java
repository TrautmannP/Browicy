package com.browicy.engine.js;

import lombok.AccessLevel;
import lombok.Getter;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

final class JsConsole implements ProxyObject {

    private static final List<String> LEVELS = List.of("log", "info", "warn", "error", "debug");

    @Getter(AccessLevel.PACKAGE)
    private final List<String> messages = new ArrayList<>();

    @Override
    public Object getMember(String key) {
        if (!LEVELS.contains(key)) {
            return null;
        }
        return (ProxyExecutable) args -> {
            String text = Arrays.stream(args)
                    .map(JsConsole::format)
                    .collect(Collectors.joining(" "));
            messages.add(key + ": " + text);
            return null;
        };
    }

    private static String format(Value value) {
        return value.isString() ? value.asString() : value.toString();
    }

    @Override
    public Object getMemberKeys() {
        return LEVELS.toArray();
    }

    @Override
    public boolean hasMember(String key) {
        return LEVELS.contains(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("console ist schreibgeschützt");
    }
}

package com.browicy.engine.js;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import com.browicy.engine.dom.Event;
import com.browicy.engine.dom.EventListener;
import org.graalvm.polyglot.Value;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class JsEventListener implements EventListener {

    private final Value callback;
    private final JsDocument document;

    @Override
    public void handleEvent(Event event) {
        document.invokeEventListener(callback, event);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JsEventListener listener
                && document == listener.document
                && callback.equals(listener.callback);
    }

    @Override
    public int hashCode() {
        return 31 * System.identityHashCode(document) + callback.hashCode();
    }
}

package com.browicy.engine.js;

import com.browicy.engine.dom.Event;
import com.browicy.engine.dom.EventListener;
import org.graalvm.polyglot.Value;

/** Bindet eine GraalJS-Funktion bzw. ein handleEvent-Objekt an das Java-DOM. */
final class JsEventListener implements EventListener {

    private final Value callback;
    private final JsDocument document;

    JsEventListener(Value callback, JsDocument document) {
        this.callback = callback;
        this.document = document;
    }

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

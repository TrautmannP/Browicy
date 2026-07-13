package com.browicy.engine.dom;

/**
 * Callback eines DOM-Ereignisses. Listener werden über {@link EventTarget}
 * registriert und während der Ereignisausbreitung synchron aufgerufen.
 */
@FunctionalInterface
public interface EventListener {

    void handleEvent(Event event);
}

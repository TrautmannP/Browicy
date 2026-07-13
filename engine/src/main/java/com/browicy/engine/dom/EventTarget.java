package com.browicy.engine.dom;

/**
 * Minimaler DOM-Level-2-EventTarget-Vertrag.
 */
public interface EventTarget {

    void addEventListener(String type, EventListener listener, boolean useCapture);

    void removeEventListener(String type, EventListener listener, boolean useCapture);

    /**
     * Dispatcht das Ereignis synchron. Der Rückgabewert ist {@code false},
     * wenn ein abbrechbares Ereignis per {@link Event#preventDefault()}
     * abgebrochen wurde.
     */
    boolean dispatchEvent(Event event);
}

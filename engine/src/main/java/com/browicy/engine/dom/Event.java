package com.browicy.engine.dom;

/**
 * DOM-Ereignis mit den Zuständen, die für Capturing, Target- und
 * Bubbling-Phase benötigt werden.
 */
public class Event {

    public static final short NONE = 0;
    public static final short CAPTURING_PHASE = 1;
    public static final short AT_TARGET = 2;
    public static final short BUBBLING_PHASE = 3;

    private String type = "";
    private boolean bubbles;
    private boolean cancelable;
    private final long timeStamp = System.currentTimeMillis();

    private Node target;
    private Node currentTarget;
    private short eventPhase = NONE;
    private boolean defaultPrevented;
    private boolean propagationStopped;
    private boolean immediatePropagationStopped;
    private boolean dispatching;
    private boolean initialized;

    public Event() {
    }

    public Event(String type, boolean bubbles, boolean cancelable) {
        initEvent(type, bubbles, cancelable);
    }

    public String getType() {
        return type;
    }

    public Node getTarget() {
        return target;
    }

    public Node getCurrentTarget() {
        return currentTarget;
    }

    public short getEventPhase() {
        return eventPhase;
    }

    public boolean isBubbles() {
        return bubbles;
    }

    public boolean isCancelable() {
        return cancelable;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public boolean isDefaultPrevented() {
        return defaultPrevented;
    }

    public boolean isDispatching() {
        return dispatching;
    }

    /**
     * Initialisiert bzw. reinitialisiert das Ereignis. Während eines laufenden
     * Dispatches ist die Operation entsprechend DOM Level 2 wirkungslos.
     */
    public void initEvent(String type, boolean bubbles, boolean cancelable) {
        if (dispatching) {
            return;
        }
        this.type = type == null ? "" : type;
        this.bubbles = bubbles;
        this.cancelable = cancelable;
        this.target = null;
        this.currentTarget = null;
        this.eventPhase = NONE;
        this.defaultPrevented = false;
        this.propagationStopped = false;
        this.immediatePropagationStopped = false;
        this.initialized = true;
    }

    public void stopPropagation() {
        propagationStopped = true;
    }

    public void stopImmediatePropagation() {
        propagationStopped = true;
        immediatePropagationStopped = true;
    }

    public void preventDefault() {
        if (cancelable) {
            defaultPrevented = true;
        }
    }

    boolean isPropagationStopped() {
        return propagationStopped;
    }

    boolean isImmediatePropagationStopped() {
        return immediatePropagationStopped;
    }

    void beginDispatch(Node target) {
        if (!initialized || type.isEmpty()) {
            throw new IllegalStateException("Das Event wurde nicht mit einem Typ initialisiert");
        }
        if (dispatching) {
            throw new IllegalStateException("Das Event wird bereits dispatcht");
        }
        this.target = target;
        this.currentTarget = null;
        this.eventPhase = NONE;
        this.propagationStopped = false;
        this.immediatePropagationStopped = false;
        this.dispatching = true;
    }

    void enter(Node currentTarget, short phase) {
        this.currentTarget = currentTarget;
        this.eventPhase = phase;
    }

    void finishDispatch() {
        currentTarget = null;
        eventPhase = NONE;
        propagationStopped = false;
        immediatePropagationStopped = false;
        dispatching = false;
    }
}

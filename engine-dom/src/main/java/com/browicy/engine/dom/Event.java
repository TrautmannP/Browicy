package com.browicy.engine.dom;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
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
    @Getter(AccessLevel.PACKAGE)
    private boolean propagationStopped;
    @Getter(AccessLevel.PACKAGE)
    private boolean immediatePropagationStopped;
    private boolean dispatching;
    @Getter(AccessLevel.NONE)
    private boolean initialized;


    public Event(String type, boolean bubbles, boolean cancelable) {
        initEvent(type, bubbles, cancelable);
    }

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

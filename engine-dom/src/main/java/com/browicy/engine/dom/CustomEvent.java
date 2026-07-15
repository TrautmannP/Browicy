package com.browicy.engine.dom;

import lombok.Getter;

@Getter
public final class CustomEvent extends Event {

    private Object detail;

    @Override
    public void initEvent(String type, boolean bubbles, boolean cancelable) {
        super.initEvent(type, bubbles, cancelable);
        if (!isDispatching()) detail = null;
    }

    public void initCustomEvent(String type, boolean bubbles, boolean cancelable, Object detail) {
        if (isDispatching()) return;
        super.initEvent(type, bubbles, cancelable);
        this.detail = detail;
    }
}

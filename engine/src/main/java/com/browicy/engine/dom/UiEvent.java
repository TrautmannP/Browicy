package com.browicy.engine.dom;

import lombok.Getter;

@Getter
public final class UiEvent extends Event {

    private Object view;
    private int detail;

    @Override
    public void initEvent(String type, boolean bubbles, boolean cancelable) {
        super.initEvent(type, bubbles, cancelable);
        if (!isDispatching()) {
            view = null;
            detail = 0;
        }
    }

    public void initUiEvent(String type, boolean bubbles, boolean cancelable, Object view, int detail) {
        if (isDispatching()) {
            return;
        }
        super.initEvent(type, bubbles, cancelable);
        this.view = view;
        this.detail = detail;
    }
}

package com.browicy.engine.dom;

/**
 * DOM-UIEvent mit View- und Detail-Informationen.
 */
public final class UiEvent extends Event {

    private Object view;
    private int detail;

    public Object getView() {
        return view;
    }

    public int getDetail() {
        return detail;
    }

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

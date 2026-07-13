package com.browicy.engine.dom;

/** Loading state exposed through {@code document.readyState}. */
public enum DocumentReadyState {
    LOADING("loading"),
    INTERACTIVE("interactive"),
    COMPLETE("complete");

    private final String scriptValue;

    DocumentReadyState(String scriptValue) {
        this.scriptValue = scriptValue;
    }

    public String scriptValue() {
        return scriptValue;
    }
}

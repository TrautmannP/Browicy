package com.browicy.engine.net;

@FunctionalInterface
public interface NetworkRequestObserver {

    void onEvent(NetworkRequestEvent event);
}

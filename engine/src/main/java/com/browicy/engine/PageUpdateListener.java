package com.browicy.engine;

@FunctionalInterface
public interface PageUpdateListener {

    PageUpdateListener NO_OP = update -> {
    };

    void onUpdate(PageUpdate update);
}

package com.browicy.engine.net;

import java.util.concurrent.atomic.AtomicLong;

final class RequestIds {

    private static final AtomicLong NEXT_ID = new AtomicLong(1);

    private RequestIds() {
    }

    static long next() {
        return NEXT_ID.getAndIncrement();
    }
}

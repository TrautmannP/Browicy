package com.browicy.engine.net;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

public final class DownloadBudget {

    private final AtomicLong transferRemaining;
    private final AtomicLong decodedRemaining;

    public DownloadBudget(long maxTransferBytes, long maxDecodedBytes) {
        if (maxTransferBytes < 0 || maxDecodedBytes < 0) {
            throw new IllegalArgumentException("Download-Limits dürfen nicht negativ sein");
        }
        transferRemaining = new AtomicLong(maxTransferBytes);
        decodedRemaining = new AtomicLong(maxDecodedBytes);
    }

    void consumeTransfer(long bytes) throws IOException {
        consume(transferRemaining, bytes, "Transferbudget der Seite überschritten");
    }

    void consumeDecoded(long bytes) throws IOException {
        consume(decodedRemaining, bytes, "Speicherbudget der Seite überschritten");
    }

    private static void consume(AtomicLong remaining, long bytes, String message) throws IOException {
        if (bytes <= 0) return;
        long before = remaining.getAndAccumulate(bytes, (current, used) ->
                used > current ? -1 : current - used);
        if (before < bytes) throw new IOException(message);
    }
}

package com.browicy.devtools.network;

import com.browicy.engine.net.NetworkRequestEvent;
import com.browicy.engine.net.NetworkRequestObserver;
import com.browicy.engine.net.NetworkResourceType;
import com.browicy.engine.net.PageLoadEvent;
import com.browicy.engine.net.PageLoadObserver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

public final class NetworkLog implements NetworkRequestObserver, PageLoadObserver {

    private static final int DEFAULT_MAX_ENTRIES = 500;

    private final Object lock = new Object();
    private final LinkedHashMap<Long, NetworkRequestEntry> entries = new LinkedHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final int maxEntries;

    public NetworkLog() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public NetworkLog(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries muss positiv sein: " + maxEntries);
        }
        this.maxEntries = maxEntries;
    }

    @Override
    public void onEvent(NetworkRequestEvent event) {
        synchronized (lock) {
            switch (event) {
                case NetworkRequestEvent.Started started -> {
                    entries.put(started.requestId(), NetworkRequestEntry.started(
                            started.requestId(), started.resourceType(), started.url(), started.at()));
                    evictOldest();
                }
                case NetworkRequestEvent.Redirected redirected ->
                        update(redirected.requestId(), NetworkRequestEntry::redirected);
                case NetworkRequestEvent.Loaded loaded -> update(loaded.requestId(), entry ->
                        entry.loaded(loaded.finalUri(), loaded.statusCode(),
                                loaded.sizeBytes(), loaded.at()));
                case NetworkRequestEvent.Failed failed -> update(failed.requestId(), entry ->
                        entry.failed(messageOf(failed.cause()), failed.at()));
                case NetworkRequestEvent.Cancelled cancelled ->
                        update(cancelled.requestId(), entry -> entry.cancelled(cancelled.at()));
            }
        }
        fireChanged();
    }

    @Override
    public void onEvent(PageLoadEvent event) {
        synchronized (lock) {
            switch (event) {
                case PageLoadEvent.Started started -> {
                    entries.put(started.loadId(), NetworkRequestEntry.started(
                            started.loadId(), NetworkResourceType.DOCUMENT,
                            started.url(), started.at()));
                    evictOldest();
                }
                case PageLoadEvent.Redirected redirected ->
                        update(redirected.loadId(), NetworkRequestEntry::redirected);
                case PageLoadEvent.Loaded loaded -> update(loaded.loadId(), entry ->
                        entry.loaded(loaded.page().uri(), loaded.page().statusCode(),
                                loaded.page().sizeBytes(), loaded.at()));
                case PageLoadEvent.Failed failed -> update(failed.loadId(), entry ->
                        entry.failed(messageOf(failed.cause()), failed.at()));
                case PageLoadEvent.Cancelled cancelled ->
                        update(cancelled.loadId(), entry -> entry.cancelled(cancelled.at()));
            }
        }
        fireChanged();
    }

    public List<NetworkRequestEntry> entries() {
        synchronized (lock) {
            return List.copyOf(entries.values());
        }
    }

    public void clear() {
        synchronized (lock) {
            if (entries.isEmpty()) {
                return;
            }
            entries.clear();
        }
        fireChanged();
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void update(long loadId, UnaryOperator<NetworkRequestEntry> change) {
        NetworkRequestEntry entry = entries.get(loadId);
        if (entry != null) {
            entries.put(loadId, change.apply(entry));
        }
    }

    private void evictOldest() {
        while (entries.size() > maxEntries) {
            entries.remove(entries.firstEntry().getKey());
        }
    }

    private void fireChanged() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    private static String messageOf(Exception cause) {
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}

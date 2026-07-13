package com.browicy.devtools.network;

import com.browicy.engine.net.PageLoad;
import com.browicy.engine.net.PageLoadEvent;
import com.browicy.engine.net.PageLoader;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NetworkLogTest {

    private static final Instant START = Instant.parse("2026-07-13T12:00:00Z");
    private static final Instant END = START.plusMillis(120);

    private final NetworkLog log = new NetworkLog();

    @Test
    public void startedCreatesLoadingEntry() {
        log.onEvent(new PageLoadEvent.Started(1, START, "http://beispiel.de"));

        List<NetworkRequestEntry> entries = log.entries();
        assertEquals(1, entries.size());
        NetworkRequestEntry entry = entries.getFirst();
        assertEquals(1, entry.loadId());
        assertEquals("http://beispiel.de", entry.url());
        assertEquals(PageLoad.State.LOADING, entry.state());
        assertNull(entry.statusCode());
        assertTrue(entry.duration().isEmpty());
        assertEquals("http://beispiel.de", entry.displayUrl());
    }

    @Test
    public void loadedCompletesEntry() {
        URI finalUri = URI.create("http://beispiel.de/start");
        String html = "<html><body>Inhalt</body></html>";
        log.onEvent(new PageLoadEvent.Started(1, START, "beispiel.de"));
        log.onEvent(new PageLoadEvent.Loaded(1, END, new PageLoader.Page(finalUri, 200, html)));

        NetworkRequestEntry entry = log.entries().getFirst();
        assertEquals(PageLoad.State.LOADED, entry.state());
        assertEquals(Integer.valueOf(200), entry.statusCode());
        assertEquals(html.length(), entry.htmlLength());
        assertEquals(finalUri.toString(), entry.displayUrl());
        assertEquals(Duration.ofMillis(120), entry.duration().orElseThrow());
        assertNull(entry.failureMessage());
    }

    @Test
    public void redirectsAreCounted() {
        log.onEvent(new PageLoadEvent.Started(1, START, "http://a.de"));
        log.onEvent(new PageLoadEvent.Redirected(1, START, URI.create("http://a.de"),
                URI.create("http://b.de"), 301));
        log.onEvent(new PageLoadEvent.Redirected(1, START, URI.create("http://b.de"),
                URI.create("http://c.de"), 302));

        NetworkRequestEntry entry = log.entries().getFirst();
        assertEquals(2, entry.redirectCount());
        assertEquals(PageLoad.State.LOADING, entry.state());
    }

    @Test
    public void failedStoresMessage() {
        log.onEvent(new PageLoadEvent.Started(1, START, "http://kaputt.de"));
        log.onEvent(new PageLoadEvent.Failed(1, END, "http://kaputt.de",
                new IOException("Verbindung fehlgeschlagen")));

        NetworkRequestEntry entry = log.entries().getFirst();
        assertEquals(PageLoad.State.FAILED, entry.state());
        assertEquals("Verbindung fehlgeschlagen", entry.failureMessage());
        assertEquals(Duration.ofMillis(120), entry.duration().orElseThrow());
    }

    @Test
    public void failureWithoutMessageFallsBackToExceptionName() {
        log.onEvent(new PageLoadEvent.Started(1, START, "http://kaputt.de"));
        log.onEvent(new PageLoadEvent.Failed(1, END, "http://kaputt.de", new IOException()));

        assertEquals("IOException", log.entries().getFirst().failureMessage());
    }

    @Test
    public void cancelledMarksEntry() {
        log.onEvent(new PageLoadEvent.Started(1, START, "http://beispiel.de"));
        log.onEvent(new PageLoadEvent.Cancelled(1, END, "http://beispiel.de"));

        NetworkRequestEntry entry = log.entries().getFirst();
        assertEquals(PageLoad.State.CANCELLED, entry.state());
        assertNull(entry.failureMessage());
    }

    @Test
    public void eventsForUnknownLoadsAreIgnored() {
        log.onEvent(new PageLoadEvent.Cancelled(99, END, "http://unbekannt.de"));

        assertTrue(log.entries().isEmpty());
    }

    @Test
    public void oldestEntriesAreEvicted() {
        NetworkLog boundedLog = new NetworkLog(2);
        boundedLog.onEvent(new PageLoadEvent.Started(1, START, "http://eins.de"));
        boundedLog.onEvent(new PageLoadEvent.Started(2, START, "http://zwei.de"));
        boundedLog.onEvent(new PageLoadEvent.Started(3, START, "http://drei.de"));

        List<NetworkRequestEntry> entries = boundedLog.entries();
        assertEquals(2, entries.size());
        assertEquals(2, entries.get(0).loadId());
        assertEquals(3, entries.get(1).loadId());
    }

    @Test
    public void rejectsInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new NetworkLog(0));
    }

    @Test
    public void clearRemovesEntriesAndNotifiesListeners() {
        AtomicInteger notifications = new AtomicInteger();
        log.addListener(notifications::incrementAndGet);

        log.onEvent(new PageLoadEvent.Started(1, START, "http://beispiel.de"));
        log.clear();

        assertTrue(log.entries().isEmpty());
        assertEquals(2, notifications.get());

        log.clear();
        assertEquals(2, notifications.get());
    }

    @Test
    public void removedListenerIsNotNotified() {
        AtomicInteger notifications = new AtomicInteger();
        Runnable listener = notifications::incrementAndGet;
        log.addListener(listener);
        log.removeListener(listener);

        log.onEvent(new PageLoadEvent.Started(1, START, "http://beispiel.de"));

        assertEquals(0, notifications.get());
    }
}

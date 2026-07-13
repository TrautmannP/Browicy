package com.browicy.engine.net;

import java.net.URI;
import java.time.Instant;

/**
 * Ereignis im Lebenszyklus eines Seitenladevorgangs, gemeldet an registrierte
 * {@link PageLoadObserver} (siehe {@link PageLoader#addObserver}).
 *
 * <p>Für jeden Ladevorgang gilt: genau ein {@link Started}, beliebig viele
 * {@link Redirected} und genau ein Abschluss-Ereignis ({@link Loaded},
 * {@link Failed} oder {@link Cancelled}). Zusammengehörige Ereignisse tragen
 * dieselbe {@link #loadId()}.</p>
 */
public sealed interface PageLoadEvent {

    /** Eindeutige, fortlaufende Nummer des Ladevorgangs (siehe {@link PageLoad#id()}). */
    long loadId();

    /** Zeitpunkt, zu dem das Ereignis eingetreten ist. */
    Instant at();

    /** Ein Ladevorgang wurde gestartet. */
    record Started(long loadId, Instant at, String url) implements PageLoadEvent {
    }

    /** Einer Weiterleitung ({@code Location}-Header) wird gefolgt. */
    record Redirected(long loadId, Instant at, URI from, URI to, int statusCode) implements PageLoadEvent {
    }

    /** Der Ladevorgang wurde erfolgreich abgeschlossen. */
    record Loaded(long loadId, Instant at, PageLoader.Page page) implements PageLoadEvent {
    }

    /** Der Ladevorgang ist fehlgeschlagen. */
    record Failed(long loadId, Instant at, String url, Exception cause) implements PageLoadEvent {
    }

    /** Der Ladevorgang wurde über {@link PageLoad#cancel()} abgebrochen. */
    record Cancelled(long loadId, Instant at, String url) implements PageLoadEvent {
    }
}

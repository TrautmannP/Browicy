package com.browicy.engine.net;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Ein beobachtbarer, asynchroner Seitenladevorgang, gestartet über
 * {@link PageLoader#loadAsync(String)}.
 *
 * <p>Der Vorgang beginnt im Zustand {@link State#LOADING} und endet genau
 * einmal in einem der Endzustände {@link State#LOADED}, {@link State#FAILED}
 * oder {@link State#CANCELLED}. Über {@link #onDone(Consumer)} lassen sich
 * Listener registrieren, {@link #await()} blockiert bis zum Abschluss.</p>
 *
 * <p>{@link #cancel()} wirkt sofort auf den Zustand; die laufende
 * Netzwerkoperation wird kooperativ beendet (spätestens vor der nächsten
 * Weiterleitung), ihr Ergebnis wird verworfen.</p>
 */
public final class PageLoad {

    /** Lebenszyklus eines Ladevorgangs. */
    public enum State {
        /** Der Request läuft noch. */
        LOADING,
        /** Erfolgreich abgeschlossen; {@link #page()} liefert das Ergebnis. */
        LOADED,
        /** Mit Fehler beendet; {@link #failure()} liefert die Ursache. */
        FAILED,
        /** Durch {@link #cancel()} abgebrochen. */
        CANCELLED
    }

    private final String url;
    private final CountDownLatch done = new CountDownLatch(1);
    private final List<Consumer<PageLoad>> listeners = new CopyOnWriteArrayList<>();

    private volatile State state = State.LOADING;
    private volatile PageLoader.Page page;
    private volatile Exception failure;

    PageLoad(String url) {
        this.url = url;
    }

    /** Die ursprünglich angeforderte URL-Eingabe (vor Normalisierung und Redirects). */
    public String url() {
        return url;
    }

    public State state() {
        return state;
    }

    /** {@code true}, sobald ein Endzustand erreicht ist. */
    public boolean isDone() {
        return state != State.LOADING;
    }

    /** {@code true}, wenn der Vorgang abgebrochen wurde. */
    public boolean isCancelled() {
        return state == State.CANCELLED;
    }

    /** Das Ergebnis, falls der Vorgang erfolgreich abgeschlossen wurde. */
    public Optional<PageLoader.Page> page() {
        return Optional.ofNullable(page);
    }

    /** Die Fehlerursache, falls der Vorgang fehlgeschlagen ist. */
    public Optional<Exception> failure() {
        return Optional.ofNullable(failure);
    }

    /**
     * Bricht den Ladevorgang ab. Der Zustand wechselt sofort zu
     * {@link State#CANCELLED}; ein bereits beendeter Vorgang bleibt unverändert.
     */
    public void cancel() {
        finish(State.CANCELLED, null, null);
    }

    /**
     * Registriert einen Listener, der genau einmal beim Erreichen eines
     * Endzustands aufgerufen wird — auf dem Lade-Thread bzw. sofort auf dem
     * aufrufenden Thread, falls der Vorgang schon beendet ist.
     */
    public void onDone(Consumer<PageLoad> listener) {
        synchronized (this) {
            if (state == State.LOADING) {
                listeners.add(listener);
                return;
            }
        }
        listener.accept(this);
    }

    /**
     * Blockiert bis zum Abschluss und liefert die geladene Seite.
     *
     * @throws java.io.IOException  wenn der Vorgang fehlgeschlagen ist
     * @throws CancellationException wenn der Vorgang abgebrochen wurde
     */
    public PageLoader.Page await() throws java.io.IOException, InterruptedException {
        done.await();
        switch (state) {
            case LOADED:
                return page;
            case CANCELLED:
                throw new CancellationException("Ladevorgang abgebrochen: " + url);
            case FAILED:
                if (failure instanceof java.io.IOException io) {
                    throw io;
                }
                if (failure instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new java.io.IOException(failure);
            default:
                throw new IllegalStateException("Unerwarteter Zustand: " + state);
        }
    }

    void completeLoaded(PageLoader.Page result) {
        finish(State.LOADED, result, null);
    }

    void completeFailed(Exception cause) {
        finish(State.FAILED, null, cause);
    }

    /** Erster Endzustand gewinnt; alle weiteren Abschlussversuche sind wirkungslos. */
    private void finish(State terminal, PageLoader.Page result, Exception cause) {
        synchronized (this) {
            if (state != State.LOADING) {
                return;
            }
            this.page = result;
            this.failure = cause;
            this.state = terminal;
        }
        done.countDown();
        for (Consumer<PageLoad> listener : listeners) {
            listener.accept(this);
        }
    }
}

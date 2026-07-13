package com.browicy.engine.net;

/**
 * Beobachter für Seitenladevorgänge eines {@link PageLoader}, z.&nbsp;B. für
 * Entwicklerwerkzeuge oder Protokollierung.
 *
 * <p>Die Aufrufe erfolgen auf dem Thread, auf dem das Ereignis eintritt
 * (Lade-Thread bzw. bei {@link PageLoad#cancel()} der abbrechende Thread) —
 * Implementierungen müssen threadsicher sein und dürfen nicht blockieren.
 * Exceptions aus Beobachtern werden protokolliert und beeinflussen den
 * Ladevorgang nicht.</p>
 */
@FunctionalInterface
public interface PageLoadObserver {

    void onEvent(PageLoadEvent event);
}

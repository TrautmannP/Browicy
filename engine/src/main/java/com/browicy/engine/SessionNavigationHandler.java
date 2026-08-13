package com.browicy.engine;

import com.browicy.engine.js.PageNavigationHandler;

/**
 * Verbindet den JavaScript-Navigations-Hook mit der {@link PageSession}, die
 * die Navigation ausführt. Der Coordinator bindet die Session, bevor die
 * Skriptausführung beginnt; Nachfolge-Loads (Navigationen) binden nicht neu.
 */
final class SessionNavigationHandler implements PageNavigationHandler {

    private final BrowicyEngine engine;
    private final PageUpdateListener listener;
    private final PageLoadProgress progress;
    private volatile PageSession session;

    SessionNavigationHandler(BrowicyEngine engine,
                             PageUpdateListener listener,
                             PageLoadProgress progress) {
        this.engine = engine;
        this.listener = listener;
        this.progress = progress;
    }

    void bind(PageSession session) {
        if (this.session == null) {
            this.session = session;
        }
    }

    PageUpdateListener listener() {
        return listener;
    }

    PageLoadProgress progress() {
        return progress;
    }

    @Override
    public void navigate(String url, boolean replace) {
        PageSession target = session;
        if (target != null) {
            engine.navigateTo(this, target, url);
        }
    }
}

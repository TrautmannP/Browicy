package com.browicy.css21;

import com.browicy.engine.BrowicyEngine;
import com.browicy.engine.PageSession;
import com.browicy.ui.DomViewPanel;
import java.awt.image.BufferedImage;

/**
 * Rendert Testseiten mit der Browicy-Engine in-process und liefert
 * Viewport-Screenshots in derselben Größe wie Chrome — denselben
 * Rendering-Pfad, den auch die Desktop-App und der CLI-Inspector nutzen
 * (Engine → Render-Tree → Java2D über {@link DomViewPanel}).
 */
public final class BrowicyRenderer implements AutoCloseable {

    private final BrowicyEngine engine;

    public BrowicyRenderer() {
        engine = new BrowicyEngine();
    }

    /** Viewport-Screenshot der URL in der angegebenen Größe. */
    public BufferedImage screenshot(String url, int width, int height) {
        try (PageSession session = engine.loadPageSession(url, ignored -> { })) {
            session.awaitResources();
            DomViewPanel panel = new DomViewPanel(session);
            try {
                return panel.captureScreenshot(width, height, false);
            } finally {
                panel.dispose();
            }
        }
    }

    @Override
    public void close() {
        engine.close();
    }
}

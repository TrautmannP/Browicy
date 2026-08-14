package com.browicy.ui;

import com.browicy.engine.dom.Document;
import com.browicy.engine.html.HtmlParser;
import java.awt.Graphics2D;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DomViewPanelConcurrencyTest {

    private static final long DOCUMENT_LOCK_HOLD_MILLIS = 3_000;

    @Test(timeout = 15_000)
    public void edtInstantiatesPanelWhileJsThreadHoldsDocumentLock() throws Exception {
        Document document = new HtmlParser().parse(
                "<html><body><p>Inhalt</p></body></html>");
        CountDownLatch lockHeld = new CountDownLatch(1);
        AtomicBoolean documentLockReleased = new AtomicBoolean();
        AtomicReference<Throwable> edtFailure = new AtomicReference<>();
        CountDownLatch edtFinished = new CountDownLatch(1);

        Thread jsThread = Thread.ofPlatform().daemon().name("test-js-thread").start(() -> {
            synchronized (document) {
                lockHeld.countDown();
                try {
                    Thread.sleep(DOCUMENT_LOCK_HOLD_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            documentLockReleased.set(true);
        });
        assertTrue("JS-Thread hat das document-Lock nicht erreicht",
                lockHeld.await(2, TimeUnit.SECONDS));

        SwingUtilities.invokeLater(() -> {
            try {
                DomViewPanel panel = new DomViewPanel(document);
                panel.addComponentListener(new ComponentAdapter() {
                    @Override
                    public void componentResized(ComponentEvent event) {
                    }
                });
                panel.setSize(400, 300);
                BufferedImage image = new BufferedImage(
                        400, 300, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics();
                try {
                    panel.paintComponent(graphics);
                } finally {
                    graphics.dispose();
                }
            } catch (Throwable failure) {
                edtFailure.set(failure);
            } finally {
                edtFinished.countDown();
            }
        });

        assertTrue("EDT blockierte, waehrend das document-Lock gehalten wurde",
                edtFinished.await(2, TimeUnit.SECONDS));
        assertFalse("EDT wurde erst nach Freigabe des document-Locks fertig "
                        + "(Lock-Inversion ueber das Panel-Monitor?)",
                documentLockReleased.get());
        assertNull("EDT-Ausfuehrung schlug fehl", edtFailure.get());

        jsThread.join(TimeUnit.SECONDS.toMillis(5));
    }
}

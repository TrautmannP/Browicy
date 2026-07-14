package com.browicy.ui;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

public final class EdtFreezeWatchdog implements AutoCloseable {

    private static final System.Logger LOGGER =
            System.getLogger(EdtFreezeWatchdog.class.getName());
    private static final long DEFAULT_THRESHOLD_MILLIS = 2_000;
    private static final long POLL_INTERVAL_MILLIS = 1_000;

    private final long thresholdMillis;
    private final Thread thread;
    private volatile boolean running = true;

    public EdtFreezeWatchdog() {
        this(DEFAULT_THRESHOLD_MILLIS);
    }

    public EdtFreezeWatchdog(long thresholdMillis) {
        this.thresholdMillis = thresholdMillis;
        this.thread = new Thread(this::watch, "browicy-edt-watchdog");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    private void watch() {
        while (running) {
            try {
                pingOnce();
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void pingOnce() throws InterruptedException {
        CountDownLatch pong = new CountDownLatch(1);
        long startNanos = System.nanoTime();
        SwingUtilities.invokeLater(pong::countDown);
        if (pong.await(thresholdMillis, TimeUnit.MILLISECONDS)) {
            return;
        }
        LOGGER.log(System.Logger.Level.WARNING,
                "Der EDT reagiert seit mehr als " + thresholdMillis
                        + " ms nicht – aktueller Stand:\n" + describeEdt());
        pong.await();
        long blockedMillis = (System.nanoTime() - startNanos) / 1_000_000;
        LOGGER.log(System.Logger.Level.INFO,
                "Der EDT ist nach " + blockedMillis + " ms wieder reaktionsfähig");
    }

    private static String describeEdt() {
        Thread edt = findEdtThread();
        if (edt == null) {
            return "(EDT-Thread nicht gefunden)";
        }
        StringBuilder description = new StringBuilder(
                edt.getName() + " [" + edt.getState() + "]");
        for (StackTraceElement frame : edt.getStackTrace()) {
            description.append("\n\tat ").append(frame);
        }
        return description.toString();
    }

    private static Thread findEdtThread() {
        Thread[] threads = new Thread[Thread.activeCount() * 2 + 8];
        int count = Thread.enumerate(threads);
        for (int index = 0; index < count; index++) {
            if (threads[index].getName().startsWith("AWT-EventQueue")) {
                return threads[index];
            }
        }
        return null;
    }

    @Override
    public void close() {
        running = false;
        thread.interrupt();
    }
}

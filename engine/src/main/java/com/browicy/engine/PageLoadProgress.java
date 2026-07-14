package com.browicy.engine;

import java.util.concurrent.atomic.AtomicInteger;

public final class PageLoadProgress {

    public enum Phase {
        QUEUED("Warte auf Start"),
        FETCHING_HTML("Lade HTML"),
        PARSING("Parse HTML"),
        APPLYING_STYLES("Wende Stylesheets an"),
        RUNNING_SCRIPTS("Führe Skripte aus"),
        LOADING_RESOURCES("Lade Ressourcen"),
        COMPLETE("Fertig"),
        FAILED("Fehlgeschlagen");

        private final String label;

        Phase(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final long startedNanos = System.nanoTime();
    private volatile Phase phase = Phase.QUEUED;
    private volatile String activity = "";
    private final AtomicInteger totalStylesheets = new AtomicInteger();
    private final AtomicInteger pendingStylesheets = new AtomicInteger();
    private final AtomicInteger totalScripts = new AtomicInteger();
    private final AtomicInteger executedScripts = new AtomicInteger();
    private final AtomicInteger totalImages = new AtomicInteger();
    private final AtomicInteger pendingImages = new AtomicInteger();
    private final AtomicInteger totalFonts = new AtomicInteger();
    private final AtomicInteger pendingFonts = new AtomicInteger();

    public void phase(Phase phase, String activity) {
        this.phase = phase;
        this.activity = activity == null ? "" : activity;
    }

    public void activity(String activity) {
        this.activity = activity == null ? "" : activity;
    }

    public void stylesheetStarted() {
        totalStylesheets.incrementAndGet();
        pendingStylesheets.incrementAndGet();
    }

    public void stylesheetFinished() {
        pendingStylesheets.decrementAndGet();
    }

    public void scriptPlanned() {
        totalScripts.incrementAndGet();
    }

    public void scriptExecuted() {
        executedScripts.incrementAndGet();
    }

    public void imageStarted() {
        totalImages.incrementAndGet();
        pendingImages.incrementAndGet();
    }

    public void imageFinished() {
        pendingImages.decrementAndGet();
    }

    public void fontStarted() {
        totalFonts.incrementAndGet();
        pendingFonts.incrementAndGet();
    }

    public void fontFinished() {
        pendingFonts.decrementAndGet();
    }

    public Snapshot snapshot() {
        return new Snapshot(phase, activity,
                (System.nanoTime() - startedNanos) / 1_000_000,
                totalStylesheets.get(), pendingStylesheets.get(),
                totalScripts.get(), executedScripts.get(),
                totalImages.get(), pendingImages.get(),
                totalFonts.get(), pendingFonts.get());
    }

    public record Snapshot(Phase phase,
                           String activity,
                           long elapsedMillis,
                           int totalStylesheets,
                           int pendingStylesheets,
                           int totalScripts,
                           int executedScripts,
                           int totalImages,
                           int pendingImages,
                           int totalFonts,
                           int pendingFonts) {

        public String describe() {
            StringBuilder text = new StringBuilder(phase.label());
            if (!activity.isBlank()) {
                text.append(": ").append(activity);
            }
            if (phase == Phase.RUNNING_SCRIPTS && totalScripts > 0) {
                text.append(" (").append(executedScripts).append('/')
                        .append(totalScripts).append(')');
            }
            if (phase == Phase.LOADING_RESOURCES) {
                appendPending(text, "Stylesheets", pendingStylesheets);
                appendPending(text, "Bilder", pendingImages);
                appendPending(text, "Schriften", pendingFonts);
            }
            return text.toString();
        }

        private static void appendPending(StringBuilder text, String label, int pending) {
            if (pending > 0) {
                text.append(" – ").append(pending).append(' ').append(label).append(" ausstehend");
            }
        }
    }
}

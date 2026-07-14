package com.browicy.engine.js;

public record PageRuntimeDiagnostics(boolean closed,
                                     boolean contextUsable,
                                     int queuedTasks,
                                     String currentTask,
                                     long currentTaskMillis) {

    private static final PageRuntimeDiagnostics CLOSED =
            new PageRuntimeDiagnostics(true, false, 0, null, 0);

    public static PageRuntimeDiagnostics closedRuntime() {
        return CLOSED;
    }

    public boolean busy() {
        return currentTask != null;
    }

    public String describe() {
        if (closed) {
            return "JavaScript-Runtime geschlossen";
        }
        if (!contextUsable) {
            return "JavaScript-Kontext nach fatalem Fehler deaktiviert";
        }
        if (currentTask == null) {
            return queuedTasks == 0
                    ? "JavaScript-Runtime wartet"
                    : queuedTasks + " JavaScript-Tasks in der Warteschlange";
        }
        return "Führt aus: " + currentTask + " (seit " + currentTaskMillis + " ms, "
                + queuedTasks + " wartend)";
    }
}

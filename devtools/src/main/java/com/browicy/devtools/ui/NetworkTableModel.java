package com.browicy.devtools.ui;

import com.browicy.devtools.network.NetworkRequestEntry;
import com.browicy.engine.net.NetworkResourceType;
import com.browicy.engine.net.PageLoad;
import java.util.List;
import javax.swing.table.AbstractTableModel;

final class NetworkTableModel extends AbstractTableModel {

    static final int COLUMN_ID = 0;
    static final int COLUMN_TYPE = 1;
    static final int COLUMN_URL = 2;
    static final int COLUMN_STATE = 3;
    static final int COLUMN_STATUS = 4;
    static final int COLUMN_REDIRECTS = 5;
    static final int COLUMN_SIZE = 6;
    static final int COLUMN_DURATION = 7;

    private static final String[] COLUMNS =
            {"Nr.", "Typ", "URL", "Zustand", "Status", "Weiterleitungen", "Größe (Bytes)", "Dauer"};
    private static final String NOT_AVAILABLE = "—";

    private List<NetworkRequestEntry> entries = List.of();

    void setEntries(List<NetworkRequestEntry> entries) {
        this.entries = List.copyOf(entries);
        fireTableDataChanged();
    }

    NetworkRequestEntry entryAt(int row) {
        return entries.get(row);
    }

    @Override
    public int getRowCount() {
        return entries.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        NetworkRequestEntry entry = entries.get(row);
        return switch (column) {
            case COLUMN_ID -> String.valueOf(entry.loadId());
            case COLUMN_TYPE -> typeLabel(entry.resourceType());
            case COLUMN_URL -> entry.displayUrl();
            case COLUMN_STATE -> stateLabel(entry.state());
            case COLUMN_STATUS -> entry.statusCode() == null
                    ? NOT_AVAILABLE
                    : String.valueOf(entry.statusCode());
            case COLUMN_REDIRECTS -> String.valueOf(entry.redirectCount());
            case COLUMN_SIZE -> entry.state() == PageLoad.State.LOADED
                    ? String.format("%,d", entry.sizeBytes())
                    : NOT_AVAILABLE;
            case COLUMN_DURATION -> entry.duration()
                    .map(duration -> duration.toMillis() + " ms")
                    .orElse(NOT_AVAILABLE);
            default -> throw new IllegalArgumentException("Unbekannte Spalte: " + column);
        };
    }

    static String stateLabel(PageLoad.State state) {
        return switch (state) {
            case LOADING -> "Lädt …";
            case LOADED -> "Geladen";
            case FAILED -> "Fehlgeschlagen";
            case CANCELLED -> "Abgebrochen";
        };
    }

    static String typeLabel(NetworkResourceType type) {
        return switch (type) {
            case DOCUMENT -> "Dokument";
            case STYLESHEET -> "CSS";
            case SCRIPT -> "JavaScript";
            case IMAGE -> "Bild";
            case FONT -> "Schrift";
            case FETCH -> "Fetch";
        };
    }
}

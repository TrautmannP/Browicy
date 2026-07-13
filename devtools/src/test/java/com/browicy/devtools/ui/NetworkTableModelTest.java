package com.browicy.devtools.ui;

import com.browicy.devtools.network.NetworkRequestEntry;
import com.browicy.engine.net.NetworkResourceType;
import com.browicy.engine.net.PageLoad;
import org.junit.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class NetworkTableModelTest {

    private static final Instant START = Instant.parse("2026-07-13T12:00:00Z");

    private final NetworkTableModel model = new NetworkTableModel();

    @Test
    public void mapsLoadingEntryWithPlaceholders() {
        model.setEntries(List.of(NetworkRequestEntry.started(1, "http://beispiel.de", START)));

        assertEquals(1, model.getRowCount());
        assertEquals("1", model.getValueAt(0, NetworkTableModel.COLUMN_ID));
        assertEquals("Dokument", model.getValueAt(0, NetworkTableModel.COLUMN_TYPE));
        assertEquals("http://beispiel.de", model.getValueAt(0, NetworkTableModel.COLUMN_URL));
        assertEquals("Lädt …", model.getValueAt(0, NetworkTableModel.COLUMN_STATE));
        assertEquals("—", model.getValueAt(0, NetworkTableModel.COLUMN_STATUS));
        assertEquals("0", model.getValueAt(0, NetworkTableModel.COLUMN_REDIRECTS));
        assertEquals("—", model.getValueAt(0, NetworkTableModel.COLUMN_SIZE));
        assertEquals("—", model.getValueAt(0, NetworkTableModel.COLUMN_DURATION));
    }

    @Test
    public void mapsLoadedEntry() {
        NetworkRequestEntry entry = NetworkRequestEntry.started(2, "beispiel.de", START)
                .redirected()
                .loaded(URI.create("http://www.beispiel.de/"), 200, 1234, START.plusMillis(120));
        model.setEntries(List.of(entry));

        assertEquals("http://www.beispiel.de/", model.getValueAt(0, NetworkTableModel.COLUMN_URL));
        assertEquals("Geladen", model.getValueAt(0, NetworkTableModel.COLUMN_STATE));
        assertEquals("200", model.getValueAt(0, NetworkTableModel.COLUMN_STATUS));
        assertEquals("1", model.getValueAt(0, NetworkTableModel.COLUMN_REDIRECTS));
        assertEquals(String.format("%,d", 1234), model.getValueAt(0, NetworkTableModel.COLUMN_SIZE));
        assertEquals("120 ms", model.getValueAt(0, NetworkTableModel.COLUMN_DURATION));
    }

    @Test
    public void mapsFailedEntry() {
        NetworkRequestEntry entry = NetworkRequestEntry.started(3, "http://kaputt.de", START)
                .failed("Verbindung fehlgeschlagen", START.plusMillis(50));
        model.setEntries(List.of(entry));

        assertEquals("Fehlgeschlagen", model.getValueAt(0, NetworkTableModel.COLUMN_STATE));
        assertEquals("—", model.getValueAt(0, NetworkTableModel.COLUMN_STATUS));
        assertEquals("—", model.getValueAt(0, NetworkTableModel.COLUMN_SIZE));
        assertEquals("50 ms", model.getValueAt(0, NetworkTableModel.COLUMN_DURATION));
        assertEquals("Verbindung fehlgeschlagen", model.entryAt(0).failureMessage());
    }

    @Test
    public void labelsAllStates() {
        assertEquals("Lädt …", NetworkTableModel.stateLabel(PageLoad.State.LOADING));
        assertEquals("Geladen", NetworkTableModel.stateLabel(PageLoad.State.LOADED));
        assertEquals("Fehlgeschlagen", NetworkTableModel.stateLabel(PageLoad.State.FAILED));
        assertEquals("Abgebrochen", NetworkTableModel.stateLabel(PageLoad.State.CANCELLED));
    }
    @Test
    public void labelsResourceTypes() {
        assertEquals("Dokument", NetworkTableModel.typeLabel(NetworkResourceType.DOCUMENT));
        assertEquals("CSS", NetworkTableModel.typeLabel(NetworkResourceType.STYLESHEET));
        assertEquals("JavaScript", NetworkTableModel.typeLabel(NetworkResourceType.SCRIPT));
    }

}

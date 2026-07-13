package com.browicy.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BrowserState {

    private final List<BrowserTab> tabs = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    @Getter
    private String selectedTabId;

    public BrowserState() {
        BrowserTab tab = new BrowserTab();
        tabs.add(tab);
        selectedTabId = tab.getId();
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void fireChanged() {
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }

    public List<BrowserTab> getTabs() {
        return Collections.unmodifiableList(tabs);
    }

    public BrowserTab getSelectedTab() {
        for (BrowserTab tab : tabs) {
            if (tab.getId().equals(selectedTabId)) {
                return tab;
            }
        }
        return tabs.getFirst();
    }

    public void selectTab(String id) {
        if (!selectedTabId.equals(id) && indexOf(id) >= 0) {
            selectedTabId = id;
            fireChanged();
        }
    }

    public void addTab() {
        BrowserTab tab = new BrowserTab();
        tabs.add(tab);
        selectedTabId = tab.getId();
        fireChanged();
    }

    public void removeTab(String id) {
        int index = indexOf(id);
        if (index < 0) {
            return;
        }
        tabs.remove(index);
        if (tabs.isEmpty()) {
            tabs.add(new BrowserTab());
        }
        if (selectedTabId.equals(id)) {
            int newIndex = Math.min(Math.max(index - 1, 0), tabs.size() - 1);
            selectedTabId = tabs.get(newIndex).getId();
        }
        fireChanged();
    }

    public void updateUrl(String id, String url) {
        int index = indexOf(id);
        if (index >= 0 && !tabs.get(index).getUrl().equals(url)) {
            tabs.get(index).setUrl(url);
            fireChanged();
        }
    }

    public void updateTitle(String id, String title) {
        int index = indexOf(id);
        if (index >= 0 && !tabs.get(index).getTitle().equals(title)) {
            tabs.get(index).setTitle(title);
            fireChanged();
        }
    }

    private int indexOf(String id) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}

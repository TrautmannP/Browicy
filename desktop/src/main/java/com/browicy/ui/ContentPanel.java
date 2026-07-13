package com.browicy.ui;

import com.browicy.engine.BrowicyEngine;
import com.browicy.engine.PageSession;
import com.browicy.engine.PageUpdate;
import com.browicy.engine.dom.Document;
import com.browicy.model.BrowserState;
import com.browicy.model.BrowserTab;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public final class ContentPanel extends JPanel {

    private final BrowserState state;
    private final BrowicyEngine engine;
    private final Map<String, PendingLoad> pendingLoads = new HashMap<>();
    private String renderedTabId;
    private String renderedUrl;
    private Document shownDocument;
    private DomViewPanel domViewPanel;

    public ContentPanel(BrowserState state, BrowicyEngine engine) {
        super(new BorderLayout());
        this.state = state;
        this.engine = engine;
        setBackground(UiTheme.BACKGROUND);
        refresh();
    }

    public void refresh() {
        discardRemovedTabLoads();
        BrowserTab tab = state.getSelectedTab();
        if (tab.getId().equals(renderedTabId) && tab.getUrl().equals(renderedUrl)) {
            return;
        }
        renderedTabId = tab.getId();
        renderedUrl = tab.getUrl();
        shownDocument = null;
        domViewPanel = null;

        removeAll();
        if (tab.isBlank()) {
            add(emptyTabContent(tab), BorderLayout.CENTER);
            revalidate();
            repaint();
            return;
        }

        PageSession existingSession = tab.getPageSession();
        if (existingSession != null && !existingSession.isClosed()) {
            showDocument(tab.getId(), existingSession);
            return;
        }

        add(loadingContent(tab.getUrl()), BorderLayout.CENTER);
        revalidate();
        repaint();
        startLoad(tab);
    }

    private void startLoad(BrowserTab tab) {
        String tabId = tab.getId();
        String url = tab.getUrl();
        PendingLoad current = pendingLoads.get(tabId);
        if (current != null && current.url().equals(url)) {
            return;
        }
        if (current != null) {
            current.worker().cancel(true);
        }

        SwingWorker<PageSession, Void> worker = new SwingWorker<>() {
            private volatile PageSession producedSession;

            @Override
            protected PageSession doInBackground() {
                PageSession session = engine.loadPageSession(url, update -> SwingUtilities.invokeLater(
                        () -> applyPageUpdate(tabId, url, update)));
                producedSession = session;
                return session;
            }

            @Override
            protected void done() {
                PendingLoad activeLoad = pendingLoads.get(tabId);
                if (activeLoad == null || activeLoad.worker() != this) {
                    closeProducedSession();
                    return;
                }
                pendingLoads.remove(tabId);
                if (isCancelled()) {
                    closeProducedSession();
                    return;
                }

                PageSession session;
                try {
                    session = get();
                } catch (CancellationException cancelled) {
                    return;
                } catch (Exception failure) {
                    Document document = engine.parseHtml(
                            "<body><h1>Seite konnte nicht geladen werden</h1></body>", url);
                    session = PageSession.completed(document);
                }

                BrowserTab loadedTab = state.findTab(tabId);
                if (loadedTab == null || !url.equals(loadedTab.getUrl())) {
                    session.close();
                    return;
                }
                loadedTab.attachPageSession(session);
                if (tabId.equals(renderedTabId) && url.equals(renderedUrl)) {
                    showDocument(tabId, session);
                }
            }

            private void closeProducedSession() {
                PageSession session = producedSession;
                if (session != null) {
                    session.close();
                }
            }
        };
        pendingLoads.put(tabId, new PendingLoad(url, worker));
        worker.execute();
    }

    private void applyPageUpdate(String tabId, String url, PageUpdate update) {
        BrowserTab tab = state.findTab(tabId);
        if (tab == null || !url.equals(tab.getUrl())) {
            return;
        }
        PageSession session = tab.getPageSession();
        if (session == null || session.document() != update.document()) {
            return;
        }

        String title = update.document().getTitle();
        if (!title.isBlank()) {
            state.updateTitle(tabId, title);
        }
        if (tabId.equals(renderedTabId) && url.equals(renderedUrl)
                && shownDocument == update.document() && domViewPanel != null) {
            domViewPanel.applyPageUpdate(update);
        }
    }

    private void showDocument(String tabId, PageSession session) {
        Document document = session.document();
        shownDocument = document;
        domViewPanel = new DomViewPanel(session);

        removeAll();
        JScrollPane scrollPane = new JScrollPane(domViewPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);
        add(scrollPane, BorderLayout.CENTER);
        revalidate();
        repaint();

        String title = document.getTitle();
        if (!title.isBlank()) {
            state.updateTitle(tabId, title);
        }
    }

    private void discardRemovedTabLoads() {
        Set<String> liveTabIds = new HashSet<>();
        for (BrowserTab tab : state.getTabs()) {
            liveTabIds.add(tab.getId());
        }
        pendingLoads.entrySet().removeIf(entry -> {
            if (liveTabIds.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().worker().cancel(true);
            return true;
        });
    }

    public void close() {
        for (PendingLoad load : pendingLoads.values()) {
            load.worker().cancel(true);
        }
        pendingLoads.clear();
        shownDocument = null;
        domViewPanel = null;
    }

    private JPanel loadingContent(String url) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UiTheme.BACKGROUND);
        JLabel label = new JLabel("Lädt " + url + " …", SwingConstants.CENTER);
        label.setFont(UiTheme.BODY);
        label.setForeground(UiTheme.TEXT_SECONDARY);
        panel.add(label, new GridBagConstraints());
        return panel;
    }

    private JPanel emptyTabContent(BrowserTab tab) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UiTheme.BACKGROUND);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.insets = new Insets(4, 0, 4, 0);

        JLabel heading = new JLabel("Neuer Tab", SwingConstants.CENTER);
        heading.setFont(UiTheme.fontFor("h4"));
        heading.setForeground(UiTheme.TEXT_PRIMARY);
        panel.add(heading, gbc);

        JLabel hint = new JLabel("Gib eine URL ein und bestätige mit Enter.", SwingConstants.CENTER);
        hint.setFont(UiTheme.BODY);
        hint.setForeground(UiTheme.TEXT_SECONDARY);
        panel.add(hint, gbc);

        JLabel tabInfo = new JLabel("Tab: " + tab.getTitle(), SwingConstants.CENTER);
        tabInfo.setFont(UiTheme.BODY_SMALL);
        tabInfo.setForeground(UiTheme.TEXT_SECONDARY);
        panel.add(tabInfo, gbc);

        return panel;
    }

    private record PendingLoad(String url, SwingWorker<PageSession, Void> worker) {
    }
}

package com.browicy.ui;

import com.browicy.engine.BrowicyEngine;
import com.browicy.engine.PageLoadProgress;
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
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

public final class ContentPanel extends JPanel {

    private static final long SLOW_LOAD_HINT_MILLIS = 20_000;

    private final BrowserState state;
    private final BrowicyEngine engine;
    private final Map<String, PendingLoad> pendingLoads = new HashMap<>();
    private String renderedTabId;
    private String renderedUrl;
    private Document shownDocument;
    private DomViewPanel domViewPanel;
    private Timer loadingStatusTimer;

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
        disposeDomViewPanel();

        stopLoadingStatusTimer();
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

        PendingLoad pending = startLoad(tab);
        add(loadingContent(tab.getUrl(), pending.progress(), tab.getId()), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private PendingLoad startLoad(BrowserTab tab) {
        String tabId = tab.getId();
        String url = tab.getUrl();
        PendingLoad current = pendingLoads.get(tabId);
        if (current != null && current.url().equals(url)) {
            return current;
        }
        if (current != null) {
            current.worker().cancel(true);
        }

        PageLoadProgress progress = new PageLoadProgress();
        SwingWorker<PageSession, Void> worker = new SwingWorker<>() {
            private volatile PageSession producedSession;

            @Override
            protected PageSession doInBackground() {
                PageSession session = engine.loadPageSession(url, update -> SwingUtilities.invokeLater(
                        () -> applyPageUpdate(tabId, url, update)), progress);
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
        PendingLoad pending = new PendingLoad(url, worker, progress);
        pendingLoads.put(tabId, pending);
        worker.execute();
        return pending;
    }

    private void cancelLoad(String tabId, String url) {
        PendingLoad load = pendingLoads.get(tabId);
        if (load == null || !load.url().equals(url)) {
            return;
        }
        pendingLoads.remove(tabId);
        load.worker().cancel(true);
        if (tabId.equals(renderedTabId) && url.equals(renderedUrl)) {
            renderedUrl = null;
            stopLoadingStatusTimer();
            removeAll();
            add(cancelledContent(url), BorderLayout.CENTER);
            revalidate();
            repaint();
        }
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
        disposeDomViewPanel();
        domViewPanel = new DomViewPanel(session);

        stopLoadingStatusTimer();
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
        stopLoadingStatusTimer();
        shownDocument = null;
        disposeDomViewPanel();
    }

    private void disposeDomViewPanel() {
        if (domViewPanel != null) {
            domViewPanel.dispose();
            domViewPanel = null;
        }
    }

    private void stopLoadingStatusTimer() {
        if (loadingStatusTimer != null) {
            loadingStatusTimer.stop();
            loadingStatusTimer = null;
        }
    }

    private JPanel loadingContent(String url, PageLoadProgress progress, String tabId) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UiTheme.BACKGROUND);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.insets = new Insets(4, 0, 4, 0);

        JLabel heading = new JLabel("Lädt " + url + " …", SwingConstants.CENTER);
        heading.setFont(UiTheme.BODY);
        heading.setForeground(UiTheme.TEXT_PRIMARY);
        panel.add(heading, gbc);

        JLabel status = new JLabel(progress.snapshot().describe(), SwingConstants.CENTER);
        status.setFont(UiTheme.BODY_SMALL);
        status.setForeground(UiTheme.TEXT_SECONDARY);
        panel.add(status, gbc);

        JLabel slowHint = new JLabel(" ", SwingConstants.CENTER);
        slowHint.setFont(UiTheme.BODY_SMALL);
        slowHint.setForeground(UiTheme.TEXT_SECONDARY);
        panel.add(slowHint, gbc);

        JButton cancel = new JButton("Abbrechen");
        cancel.addActionListener(event -> cancelLoad(tabId, url));
        panel.add(cancel, gbc);

        stopLoadingStatusTimer();
        loadingStatusTimer = new Timer(250, event -> {
            PageLoadProgress.Snapshot snapshot = progress.snapshot();
            status.setText(snapshot.describe() + " – " + (snapshot.elapsedMillis() / 1000) + " s");
            if (snapshot.elapsedMillis() > SLOW_LOAD_HINT_MILLIS) {
                slowHint.setText("Der Ladevorgang dauert ungewöhnlich lange."
                        + " Über „Abbrechen“ kannst du ihn beenden.");
            }
        });
        loadingStatusTimer.start();
        return panel;
    }

    private JPanel cancelledContent(String url) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UiTheme.BACKGROUND);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.insets = new Insets(4, 0, 4, 0);

        JLabel heading = new JLabel("Ladevorgang abgebrochen", SwingConstants.CENTER);
        heading.setFont(UiTheme.BODY);
        heading.setForeground(UiTheme.TEXT_PRIMARY);
        panel.add(heading, gbc);

        JLabel hint = new JLabel(url, SwingConstants.CENTER);
        hint.setFont(UiTheme.BODY_SMALL);
        hint.setForeground(UiTheme.TEXT_SECONDARY);
        panel.add(hint, gbc);
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

    private record PendingLoad(String url, SwingWorker<PageSession, Void> worker,
                               PageLoadProgress progress) {
    }
}

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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public final class ContentPanel extends JPanel {

    private final BrowserState state;
    private final BrowicyEngine engine;
    private String renderedTabId;
    private String renderedUrl;
    private PageSession activeSession;
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
        BrowserTab tab = state.getSelectedTab();
        if (tab.getId().equals(renderedTabId) && tab.getUrl().equals(renderedUrl)) {
            return;
        }
        cancelActiveSession();
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

        add(loadingContent(tab.getUrl()), BorderLayout.CENTER);
        revalidate();
        repaint();

        String tabId = tab.getId();
        String url = tab.getUrl();
        new SwingWorker<PageSession, Void>() {
            @Override
            protected PageSession doInBackground() {
                return engine.loadPageSession(url, update -> SwingUtilities.invokeLater(
                        () -> applyPageUpdate(tabId, url, update)));
            }

            @Override
            protected void done() {
                PageSession session;
                try {
                    session = get();
                } catch (Exception e) {
                    Document document = engine.parseHtml(
                            "<body><h1>Seite konnte nicht geladen werden</h1></body>", url);
                    session = PageSession.completed(document);
                }
                if (!tabId.equals(renderedTabId) || !url.equals(renderedUrl)) {
                    session.cancel();
                    return;
                }
                activeSession = session;
                showDocument(tabId, session.document());
            }
        }.execute();
    }

    private void applyPageUpdate(String tabId, String url, PageUpdate update) {
        if (!tabId.equals(renderedTabId) || !url.equals(renderedUrl)
                || shownDocument != update.document() || domViewPanel == null) {
            return;
        }
        if (update instanceof PageUpdate.StylesChanged) {
            domViewPanel.refreshFromDocument();
        }
    }

    private void showDocument(String tabId, Document document) {
        shownDocument = document;
        domViewPanel = new DomViewPanel(document);

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

    private void cancelActiveSession() {
        if (activeSession != null) {
            activeSession.cancel();
            activeSession = null;
        }
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
}

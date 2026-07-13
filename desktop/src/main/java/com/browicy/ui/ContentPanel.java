package com.browicy.ui;

import com.browicy.engine.BrowicyEngine;
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

/**
 * Inhaltsbereich des aktiven Tabs: leerer-Tab-Hinweis für {@code about:blank},
 * sonst die über die Engine geladene und gerenderte Seite.
 */
public final class ContentPanel extends JPanel {

    private final BrowserState state;
    private final BrowicyEngine engine;
    private String renderedTabId;
    private String renderedUrl;

    public ContentPanel(BrowserState state, BrowicyEngine engine) {
        super(new BorderLayout());
        this.state = state;
        this.engine = engine;
        setBackground(UiTheme.BACKGROUND);
        refresh();
    }

    /** Baut den Inhalt neu auf, wenn sich Tab oder URL geändert haben. */
    public void refresh() {
        BrowserTab tab = state.getSelectedTab();
        if (tab.getId().equals(renderedTabId) && tab.getUrl().equals(renderedUrl)) {
            return;
        }
        renderedTabId = tab.getId();
        renderedUrl = tab.getUrl();

        removeAll();
        if (tab.isBlank()) {
            add(emptyTabContent(tab), BorderLayout.CENTER);
        } else {
            Document document = engine.loadPage(tab.getUrl());
            String title = document.getTitle();
            JScrollPane scrollPane = new JScrollPane(new DomViewPanel(document));
            scrollPane.setBorder(null);
            scrollPane.getVerticalScrollBar().setUnitIncrement(24);
            add(scrollPane, BorderLayout.CENTER);
            if (!title.isBlank()) {
                // Nach dem Aufbau melden; löst über den Listener refresh() der Tab-Leiste aus.
                state.updateTitle(tab.getId(), title);
            }
        }
        revalidate();
        repaint();
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

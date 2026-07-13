package com.browicy.ui;

import com.browicy.model.BrowserState;
import com.browicy.model.BrowserTab;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Navigationsleiste: Zurück/Vorwärts/Neuladen und die Adresszeile.
 * Enter in der Adresszeile navigiert den aktiven Tab.
 */
public final class Toolbar extends JPanel {

    private final BrowserState state;
    private final JTextField addressField;
    private String shownTabId;

    public Toolbar(BrowserState state) {
        super(new BorderLayout(4, 0));
        this.state = state;
        setBackground(UiTheme.BACKGROUND);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        buttons.setOpaque(false);
        buttons.add(navButton("←", "Zurück"));   // TODO: Verlauf
        buttons.add(navButton("→", "Vorwärts")); // TODO: Verlauf
        JButton reload = navButton("⟳", "Neuladen");
        reload.addActionListener(e -> navigate());
        buttons.add(reload);
        add(buttons, BorderLayout.WEST);

        addressField = new JTextField();
        addressField.setFont(UiTheme.BODY);
        addressField.setToolTipText("URL eingeben");
        addressField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER, 1, true),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        addressField.addActionListener(e -> navigate());
        add(addressField, BorderLayout.CENTER);

        refresh();
    }

    private JButton navButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setFont(UiTheme.BODY);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void navigate() {
        String target = addressField.getText().strip();
        if (!target.isEmpty()) {
            state.updateUrl(state.getSelectedTabId(), target);
        }
    }

    /** Synchronisiert die Adresszeile mit dem aktiven Tab (bei Tab-Wechsel). */
    public void refresh() {
        BrowserTab tab = state.getSelectedTab();
        if (!tab.getId().equals(shownTabId)) {
            shownTabId = tab.getId();
            addressField.setText(tab.isBlank() ? "" : tab.getUrl());
        }
    }
}

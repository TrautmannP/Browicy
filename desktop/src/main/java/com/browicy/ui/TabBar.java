package com.browicy.ui;

import com.browicy.model.BrowserState;
import com.browicy.model.BrowserTab;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Tab-Leiste: ein Reiter pro {@link BrowserTab} mit Schließen-Knopf,
 * rechts ein Knopf für neue Tabs. Der aktive Tab bekommt eine
 * Unterstreichung in der Akzentfarbe.
 */
public final class TabBar extends JPanel {

    private final BrowserState state;
    private final JPanel tabsPanel;

    public TabBar(BrowserState state) {
        super(new BorderLayout());
        this.state = state;
        setBackground(UiTheme.SURFACE);
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER));

        tabsPanel = new JPanel();
        tabsPanel.setLayout(new BoxLayout(tabsPanel, BoxLayout.X_AXIS));
        tabsPanel.setOpaque(false);
        add(tabsPanel, BorderLayout.CENTER);

        JButton addButton = flatButton("+", "Neuer Tab");
        addButton.addActionListener(e -> state.addTab());
        JPanel east = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        east.setOpaque(false);
        east.add(addButton);
        add(east, BorderLayout.EAST);

        refresh();
    }

    /** Baut die Reiter entsprechend dem aktuellen Zustand neu auf. */
    public void refresh() {
        tabsPanel.removeAll();
        for (BrowserTab tab : state.getTabs()) {
            boolean selected = tab.getId().equals(state.getSelectedTabId());
            tabsPanel.add(new TabComponent(tab, selected));
        }
        tabsPanel.add(Box.createHorizontalGlue());
        tabsPanel.revalidate();
        tabsPanel.repaint();
    }

    private static JButton flatButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setFont(UiTheme.TAB);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private final class TabComponent extends JPanel {

        private final boolean selected;

        TabComponent(BrowserTab tab, boolean selected) {
            super(new FlowLayout(FlowLayout.LEFT, 6, 4));
            this.selected = selected;
            setOpaque(false);
            setMaximumSize(new Dimension(220, Integer.MAX_VALUE));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel label = new JLabel(shorten(tab.getTitle()));
            label.setFont(UiTheme.TAB);
            label.setForeground(selected ? UiTheme.PRIMARY : UiTheme.TEXT_SECONDARY);
            add(label);

            JButton close = flatButton("×", "Tab schließen");
            close.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            close.addActionListener(e -> state.removeTab(tab.getId()));
            add(close);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    state.selectTab(tab.getId());
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (selected) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(UiTheme.PRIMARY);
                g.fillRect(0, getHeight() - 3, getWidth(), 3);
            }
            super.paintComponent(g);
        }

        private String shorten(String title) {
            return title.length() > 24 ? title.substring(0, 23) + "…" : title;
        }
    }
}

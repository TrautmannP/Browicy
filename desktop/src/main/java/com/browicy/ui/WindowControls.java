package com.browicy.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JPanel;

/**
 * Minimieren / Maximieren / Schließen als flache, selbst gezeichnete Buttons
 * (keine Font-Abhängigkeit für die Symbole).
 */
public final class WindowControls extends JPanel {

    private enum Glyph { MINIMIZE, MAXIMIZE, RESTORE, CLOSE }

    public WindowControls(Runnable onMinimize, Runnable onMaximizeToggle, Runnable onClose,
                          BooleanSupplier isMaximized) {
        super(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        setOpaque(false);
        add(new ControlButton(Glyph.MINIMIZE, "Minimieren", onMinimize, null, null));
        add(new ControlButton(Glyph.MAXIMIZE, "Maximieren", onMaximizeToggle, isMaximized, null));
        add(new ControlButton(Glyph.CLOSE, "Schließen", onClose, null, UiTheme.CLOSE_HOVER));
    }

    private static final class ControlButton extends JButton {

        private final Glyph glyph;
        private final BooleanSupplier isMaximized;
        private final Color hoverBackground;
        private boolean hover;

        ControlButton(Glyph glyph, String tooltip, Runnable action,
                      BooleanSupplier isMaximized, Color hoverBackground) {
            this.glyph = glyph;
            this.isMaximized = isMaximized;
            this.hoverBackground = hoverBackground != null ? hoverBackground : UiTheme.SURFACE_HOVER;
            setToolTipText(tooltip);
            setPreferredSize(new Dimension(46, 36));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            addActionListener(e -> action.run());
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (hover) {
                g2.setColor(hoverBackground);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            boolean closeHovered = glyph == Glyph.CLOSE && hover;
            g2.setColor(closeHovered ? Color.WHITE : UiTheme.TEXT_PRIMARY);
            g2.setStroke(new BasicStroke(1.2f));

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int r = 5; // halbe Symbolgröße

            Glyph shown = glyph;
            if (glyph == Glyph.MAXIMIZE && isMaximized != null && isMaximized.getAsBoolean()) {
                shown = Glyph.RESTORE;
            }
            switch (shown) {
                case MINIMIZE -> g2.drawLine(cx - r, cy, cx + r, cy);
                case MAXIMIZE -> g2.drawRect(cx - r, cy - r, 2 * r, 2 * r);
                case RESTORE -> {
                    g2.drawRect(cx - r, cy - r + 2, 2 * r - 2, 2 * r - 2);
                    g2.drawLine(cx - r + 2, cy - r, cx + r, cy - r);
                    g2.drawLine(cx + r, cy - r, cx + r, cy + r - 2);
                }
                case CLOSE -> {
                    g2.drawLine(cx - r, cy - r, cx + r, cy + r);
                    g2.drawLine(cx - r, cy + r, cx + r, cy - r);
                }
            }
            g2.dispose();
        }
    }
}

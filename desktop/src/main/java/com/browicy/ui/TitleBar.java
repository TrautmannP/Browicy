package com.browicy.ui;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Eigene Titelleiste für das rahmenlose Fenster: App-Name links,
 * Fenster-Buttons rechts. Ziehen verschiebt das Fenster,
 * Doppelklick maximiert bzw. stellt wieder her.
 */
public final class TitleBar extends JPanel {

    private Point dragOffset;

    public TitleBar(JFrame frame, Runnable onMaximizeToggle) {
        super(new BorderLayout());
        setBackground(UiTheme.SURFACE);

        JLabel title = new JLabel("browicy");
        title.setFont(UiTheme.TITLE);
        title.setForeground(UiTheme.TEXT_SECONDARY);
        title.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        add(title, BorderLayout.CENTER);

        add(new WindowControls(
                () -> frame.setState(Frame.ICONIFIED),
                onMaximizeToggle,
                frame::dispose,
                () -> (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0
        ), BorderLayout.EAST);

        MouseAdapter dragHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragOffset = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                boolean maximized = (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0;
                if (dragOffset != null && !maximized) {
                    Point screen = e.getLocationOnScreen();
                    frame.setLocation(screen.x - dragOffset.x, screen.y - dragOffset.y);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onMaximizeToggle.run();
                }
            }
        };
        addMouseListener(dragHandler);
        addMouseMotionListener(dragHandler);
    }
}

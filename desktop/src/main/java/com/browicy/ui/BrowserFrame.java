package com.browicy.ui;

import com.browicy.engine.BrowicyEngine;
import com.browicy.model.BrowserState;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

/**
 * Das Browser-Hauptfenster: rahmenlos mit eigener Titelleiste,
 * Tab-Leiste, Adressleiste und Inhaltsbereich.
 */
public final class BrowserFrame extends JFrame {

    private final BrowserState state = new BrowserState();
    private final BrowicyEngine engine = new BrowicyEngine();

    public BrowserFrame() {
        super("browicy");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setUndecorated(true);
        setSize(new Dimension(1280, 800));
        setLocationRelativeTo(null);
        // Beim Maximieren die Taskleiste freihalten
        setMaximizedBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER, 1));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        TitleBar titleBar = new TitleBar(this, this::toggleMaximize);
        TabBar tabBar = new TabBar(state);
        Toolbar toolbar = new Toolbar(state);
        top.add(titleBar);
        top.add(tabBar);
        top.add(toolbar);
        root.add(top, BorderLayout.NORTH);

        ContentPanel content = new ContentPanel(state, engine);
        root.add(content, BorderLayout.CENTER);

        setContentPane(root);

        state.addListener(() -> {
            tabBar.refresh();
            toolbar.refresh();
            content.refresh();
        });
    }

    private void toggleMaximize() {
        if ((getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) {
            setExtendedState(Frame.NORMAL);
        } else {
            Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            setMaximizedBounds(bounds);
            setExtendedState(Frame.MAXIMIZED_BOTH);
        }
    }
}

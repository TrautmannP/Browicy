package com.browicy.ui;

import com.browicy.devtools.network.NetworkLog;
import com.browicy.devtools.ui.DevToolsPanel;
import com.browicy.engine.BrowicyEngine;
import com.browicy.model.BrowserState;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;

public final class BrowserFrame extends JFrame {

    private static final String ACTION_TOGGLE_DEV_TOOLS = "browicy.toggleDevTools";

    private final BrowserState state = new BrowserState();
    private final BrowicyEngine engine = new BrowicyEngine();
    private final NetworkLog networkLog = new NetworkLog();

    private final JPanel contentArea = new JPanel(new BorderLayout());
    private final ContentPanel content;
    private final DevToolsPanel devTools;
    private JSplitPane devToolsSplit;

    public BrowserFrame() {
        super("browicy");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setUndecorated(true);
        setSize(new Dimension(1280, 800));
        setLocationRelativeTo(null);
        setMaximizedBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());

        engine.addNetworkObserver(networkLog);
        content = new ContentPanel(state, engine);
        devTools = new DevToolsPanel(networkLog, this::hideDevTools);

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

        contentArea.add(content, BorderLayout.CENTER);
        root.add(contentArea, BorderLayout.CENTER);

        setContentPane(root);
        installDevToolsShortcut();

        state.addListener(() -> {
            tabBar.refresh();
            toolbar.refresh();
            content.refresh();
        });
    }

    private void installDevToolsShortcut() {
        JComponent rootPane = getRootPane();
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0), ACTION_TOGGLE_DEV_TOOLS);
        rootPane.getActionMap().put(ACTION_TOGGLE_DEV_TOOLS, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                toggleDevTools();
            }
        });
    }

    private void toggleDevTools() {
        if (devToolsSplit == null) {
            showDevTools();
        } else {
            hideDevTools();
        }
    }

    private void showDevTools() {
        contentArea.remove(content);
        devToolsSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, content, devTools);
        devToolsSplit.setResizeWeight(1.0);
        devToolsSplit.setBorder(null);
        contentArea.add(devToolsSplit, BorderLayout.CENTER);
        contentArea.revalidate();
        contentArea.repaint();
    }

    private void hideDevTools() {
        if (devToolsSplit == null) {
            return;
        }
        contentArea.remove(devToolsSplit);
        devToolsSplit = null;
        contentArea.add(content, BorderLayout.CENTER);
        contentArea.revalidate();
        contentArea.repaint();
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

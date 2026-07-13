package com.browicy.ui;

import com.browicy.devtools.ui.DevToolsPanel;
import org.junit.Assume;
import org.junit.Test;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import javax.swing.SwingUtilities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BrowserFrameDevToolsTest {

    @Test
    public void toggleActionShowsAndHidesDevTools() throws Exception {
        Assume.assumeFalse("Kein Display verfügbar", GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            BrowserFrame frame = new BrowserFrame();
            try {
                assertFalse(containsDevTools(frame));

                performToggle(frame);
                assertTrue(containsDevTools(frame));

                performToggle(frame);
                assertFalse(containsDevTools(frame));
            } finally {
                frame.dispose();
            }
        });
    }

    private static void performToggle(BrowserFrame frame) {
        Action action = frame.getRootPane().getActionMap().get("browicy.toggleDevTools");
        assertNotNull("F12-Aktion ist nicht registriert", action);
        action.actionPerformed(new ActionEvent(frame, ActionEvent.ACTION_PERFORMED, "toggle"));
    }

    private static boolean containsDevTools(Component component) {
        if (component instanceof DevToolsPanel) {
            return true;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (containsDevTools(child)) {
                    return true;
                }
            }
        }
        return false;
    }
}

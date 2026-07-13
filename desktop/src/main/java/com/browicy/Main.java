package com.browicy;

import com.browicy.ui.BrowserFrame;
import javax.swing.SwingUtilities;

/**
 * Einstiegspunkt des Browicy-Desktop-Browsers (reines Java/Swing,
 * damit später eine GraalVM-native-image-Kompilierung möglich ist).
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        SwingUtilities.invokeLater(() -> new BrowserFrame().setVisible(true));
    }
}

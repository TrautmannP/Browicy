package com.browicy;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import com.browicy.ui.BrowserFrame;
import javax.swing.SwingUtilities;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Main {

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        SwingUtilities.invokeLater(() -> new BrowserFrame().setVisible(true));
    }
}

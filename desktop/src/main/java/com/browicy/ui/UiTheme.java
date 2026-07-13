package com.browicy.ui;

import java.awt.Color;
import java.awt.Font;

/**
 * Zentrale Farben und Schriften der Browser-UI. Angelehnt an das helle
 * Material-3-Schema der früheren Compose-Oberfläche.
 */
public final class UiTheme {

    private UiTheme() {
    }

    public static final Color BACKGROUND = Color.WHITE;
    public static final Color SURFACE = new Color(0xF7F5FA);
    public static final Color SURFACE_HOVER = new Color(0xEAE7F0);
    public static final Color BORDER = new Color(0xE0DEE5);
    public static final Color PRIMARY = new Color(0x67, 0x50, 0xA4);
    public static final Color TEXT_PRIMARY = new Color(0x1C1B1F);
    public static final Color TEXT_SECONDARY = new Color(0x49454F);
    public static final Color CLOSE_HOVER = new Color(0xC4, 0x2B, 0x1C);

    public static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 15);
    public static final Font BODY_SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    public static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    public static final Font TAB = new Font(Font.SANS_SERIF, Font.PLAIN, 13);

    /** Schrift für eine Überschriften-/Textstufe, analog zu styleFor() der Compose-UI. */
    public static Font fontFor(String tagName) {
        return switch (tagName) {
            case "h1" -> new Font(Font.SANS_SERIF, Font.BOLD, 32);
            case "h2" -> new Font(Font.SANS_SERIF, Font.BOLD, 26);
            case "h3" -> new Font(Font.SANS_SERIF, Font.BOLD, 22);
            case "h4", "h5", "h6" -> new Font(Font.SANS_SERIF, Font.BOLD, 17);
            default -> BODY;
        };
    }
}

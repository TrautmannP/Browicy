package com.browicy.engine.render;

import java.util.Locale;
import java.util.Map;

/** Immutable CSS color value independent from a concrete UI toolkit. */
public record CssColor(int red, int green, int blue, int alpha) {

    private static final Map<String, CssColor> NAMED = Map.ofEntries(
            Map.entry("black", rgb(0x000000)),
            Map.entry("white", rgb(0xffffff)),
            Map.entry("red", rgb(0xff0000)),
            Map.entry("green", rgb(0x008000)),
            Map.entry("blue", rgb(0x0000ff)),
            Map.entry("yellow", rgb(0xffff00)),
            Map.entry("gray", rgb(0x808080)),
            Map.entry("grey", rgb(0x808080)),
            Map.entry("orange", rgb(0xffa500)),
            Map.entry("pink", rgb(0xffc0cb)),
            Map.entry("cyan", rgb(0x00ffff)),
            Map.entry("magenta", rgb(0xff00ff)),
            Map.entry("purple", rgb(0x800080)),
            Map.entry("transparent", new CssColor(0, 0, 0, 0))
    );

    public CssColor {
        requireChannel(red, "red");
        requireChannel(green, "green");
        requireChannel(blue, "blue");
        requireChannel(alpha, "alpha");
    }

    public static CssColor rgb(int rgb) {
        return new CssColor(rgb >> 16 & 0xff, rgb >> 8 & 0xff, rgb & 0xff, 0xff);
    }

    public static boolean isSupported(String value) {
        return parse(value) != null;
    }

    public static CssColor parse(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("#")) {
            String hex = normalized.substring(1);
            if (hex.length() == 3) {
                hex = "" + hex.charAt(0) + hex.charAt(0)
                        + hex.charAt(1) + hex.charAt(1)
                        + hex.charAt(2) + hex.charAt(2);
            }
            if (hex.length() != 6) {
                return null;
            }
            try {
                return rgb(Integer.parseInt(hex, 16));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return NAMED.get(normalized);
    }

    public boolean isTransparent() {
        return alpha == 0;
    }

    private static void requireChannel(int channel, String name) {
        if (channel < 0 || channel > 255) {
            throw new IllegalArgumentException(name + " channel outside 0..255: " + channel);
        }
    }
}

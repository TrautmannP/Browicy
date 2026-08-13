package com.browicy.engine.render;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable CSS color value independent from a concrete UI toolkit. */
public record CssColor(int red, int green, int blue, int alpha) {

    private static final Pattern RGB_FUNCTION = Pattern.compile(
            "rgba?\\s*\\((.*)\\)", Pattern.CASE_INSENSITIVE);

    private static final Pattern HSL_FUNCTION = Pattern.compile(
            "hsla?\\s*\\((.*)\\)", Pattern.CASE_INSENSITIVE);

    private static final Map<String, CssColor> NAMED = Map.ofEntries(
            Map.entry("black", rgb(0x000000)),
            Map.entry("white", rgb(0xffffff)),
            Map.entry("red", rgb(0xff0000)),
            Map.entry("green", rgb(0x008000)),
            Map.entry("blue", rgb(0x0000ff)),
            Map.entry("yellow", rgb(0xffff00)),
            Map.entry("gray", rgb(0x808080)),
            Map.entry("grey", rgb(0x808080)),
            Map.entry("dimgray", rgb(0x696969)),
            Map.entry("dimgrey", rgb(0x696969)),
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
            } else if (hex.length() == 4) {
                hex = "" + hex.charAt(0) + hex.charAt(0)
                        + hex.charAt(1) + hex.charAt(1)
                        + hex.charAt(2) + hex.charAt(2)
                        + hex.charAt(3) + hex.charAt(3);
            }
            if (hex.length() != 6 && hex.length() != 8) {
                return null;
            }
            try {
                long parsed = Long.parseLong(hex, 16);
                if (hex.length() == 6) return rgb((int) parsed);
                return new CssColor((int) (parsed >> 24) & 0xff, (int) (parsed >> 16) & 0xff,
                        (int) (parsed >> 8) & 0xff, (int) parsed & 0xff);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Matcher rgb = RGB_FUNCTION.matcher(normalized);
        if (rgb.matches()) {
            String body = rgb.group(1).replace('/', ',');
            String[] parts = body.split("\\s*,\\s*|\\s+");
            boolean withAlpha = normalized.startsWith("rgba") || parts.length == 4;
            if (parts.length != (withAlpha ? 4 : 3)) return null;
            try {
                int red = parseRgbChannel(parts[0]);
                int green = parseRgbChannel(parts[1]);
                int blue = parseRgbChannel(parts[2]);
                int alpha = withAlpha ? parseAlpha(parts[3]) : 255;
                return new CssColor(red, green, blue, alpha);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        Matcher hsl = HSL_FUNCTION.matcher(normalized);
        if (hsl.matches()) {
            String body = hsl.group(1).replace('/', ',');
            String[] parts = body.split("\\s*,\\s*|\\s+");
            boolean withAlpha = normalized.startsWith("hsla") || parts.length == 4;
            if (parts.length != (withAlpha ? 4 : 3)) return null;
            try {
                float hue = parseHue(parts[0]);
                float saturation = parsePercentage(parts[1]);
                float lightness = parsePercentage(parts[2]);
                int alpha = withAlpha ? parseAlpha(parts[3]) : 255;
                return fromHsl(hue, saturation, lightness, alpha);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return NAMED.get(normalized);
    }

    private static float parseHue(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        float hue;
        if (lower.endsWith("deg")) {
            hue = Float.parseFloat(lower.substring(0, lower.length() - 3));
        } else if (lower.endsWith("grad")) {
            hue = Float.parseFloat(lower.substring(0, lower.length() - 4)) * 0.9f;
        } else if (lower.endsWith("rad")) {
            hue = (float) Math.toDegrees(
                    Float.parseFloat(lower.substring(0, lower.length() - 3)));
        } else if (lower.endsWith("turn")) {
            hue = Float.parseFloat(lower.substring(0, lower.length() - 4)) * 360f;
        } else {
            hue = Float.parseFloat(lower);
        }
        if (!Float.isFinite(hue)) {
            throw new IllegalArgumentException();
        }
        float normalized = hue % 360f;
        return normalized < 0 ? normalized + 360f : normalized;
    }

    private static float parsePercentage(String value) {
        if (!value.endsWith("%")) {
            throw new IllegalArgumentException();
        }
        float parsed = Float.parseFloat(value.substring(0, value.length() - 1));
        if (!Float.isFinite(parsed) || parsed < 0 || parsed > 100) {
            throw new IllegalArgumentException();
        }
        return parsed / 100f;
    }

    private static CssColor fromHsl(float hue, float saturation, float lightness, int alpha) {
        float red;
        float green;
        float blue;
        if (saturation == 0) {
            red = green = blue = lightness;
        } else {
            float q = lightness < 0.5f
                    ? lightness * (1f + saturation)
                    : lightness + saturation - lightness * saturation;
            float p = 2f * lightness - q;
            red = hueToRgb(p, q, hue / 360f + 1f / 3f);
            green = hueToRgb(p, q, hue / 360f);
            blue = hueToRgb(p, q, hue / 360f - 1f / 3f);
        }
        return new CssColor(Math.round(red * 255f), Math.round(green * 255f),
                Math.round(blue * 255f), alpha);
    }

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1f / 6f) return p + (q - p) * 6f * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f;
        return p;
    }

    private static int parseRgbChannel(String value) {
        float parsed = value.endsWith("%")
                ? Float.parseFloat(value.substring(0, value.length() - 1)) * 2.55f
                : Float.parseFloat(value);
        if (!Float.isFinite(parsed) || parsed < 0 || parsed > 255) {
            throw new IllegalArgumentException();
        }
        return Math.round(parsed);
    }

    private static int parseAlpha(String value) {
        float parsed = value.endsWith("%")
                ? Float.parseFloat(value.substring(0, value.length() - 1)) / 100f
                : Float.parseFloat(value);
        if (!Float.isFinite(parsed) || parsed < 0 || parsed > 1) {
            throw new IllegalArgumentException();
        }
        return Math.round(parsed * 255);
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

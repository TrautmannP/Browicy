package com.browicy.engine.render;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record CssColor(int red, int green, int blue, int alpha) {

    private static final Pattern RGB_FUNCTION = Pattern.compile(
            "rgba?\\s*\\((.*)\\)", Pattern.CASE_INSENSITIVE);

    private static final Pattern HSL_FUNCTION = Pattern.compile(
            "hsla?\\s*\\((.*)\\)", Pattern.CASE_INSENSITIVE);

    private static final Map<String, CssColor> NAMED = Map.ofEntries(
            Map.entry("aliceblue", rgb(0xf0f8ff)),
            Map.entry("antiquewhite", rgb(0xfaebd7)),
            Map.entry("aqua", rgb(0x00ffff)),
            Map.entry("aquamarine", rgb(0x7fffd4)),
            Map.entry("azure", rgb(0xf0ffff)),
            Map.entry("beige", rgb(0xf5f5dc)),
            Map.entry("bisque", rgb(0xffe4c4)),
            Map.entry("black", rgb(0x000000)),
            Map.entry("blanchedalmond", rgb(0xffebcd)),
            Map.entry("blue", rgb(0x0000ff)),
            Map.entry("blueviolet", rgb(0x8a2be2)),
            Map.entry("brown", rgb(0xa52a2a)),
            Map.entry("burlywood", rgb(0xdeb887)),
            Map.entry("cadetblue", rgb(0x5f9ea0)),
            Map.entry("chartreuse", rgb(0x7fff00)),
            Map.entry("chocolate", rgb(0xd2691e)),
            Map.entry("coral", rgb(0xff7f50)),
            Map.entry("cornflowerblue", rgb(0x6495ed)),
            Map.entry("cornsilk", rgb(0xfff8dc)),
            Map.entry("crimson", rgb(0xdc143c)),
            Map.entry("cyan", rgb(0x00ffff)),
            Map.entry("darkblue", rgb(0x00008b)),
            Map.entry("darkcyan", rgb(0x008b8b)),
            Map.entry("darkgoldenrod", rgb(0xb8860b)),
            Map.entry("darkgray", rgb(0xa9a9a9)),
            Map.entry("darkgreen", rgb(0x006400)),
            Map.entry("darkgrey", rgb(0xa9a9a9)),
            Map.entry("darkkhaki", rgb(0xbdb76b)),
            Map.entry("darkmagenta", rgb(0x8b008b)),
            Map.entry("darkolivegreen", rgb(0x556b2f)),
            Map.entry("darkorange", rgb(0xff8c00)),
            Map.entry("darkorchid", rgb(0x9932cc)),
            Map.entry("darkred", rgb(0x8b0000)),
            Map.entry("darksalmon", rgb(0xe9967a)),
            Map.entry("darkseagreen", rgb(0x8fbc8f)),
            Map.entry("darkslateblue", rgb(0x483d8b)),
            Map.entry("darkslategray", rgb(0x2f4f4f)),
            Map.entry("darkslategrey", rgb(0x2f4f4f)),
            Map.entry("darkturquoise", rgb(0x00ced1)),
            Map.entry("darkviolet", rgb(0x9400d3)),
            Map.entry("deeppink", rgb(0xff1493)),
            Map.entry("deepskyblue", rgb(0x00bfff)),
            Map.entry("dimgray", rgb(0x696969)),
            Map.entry("dimgrey", rgb(0x696969)),
            Map.entry("dodgerblue", rgb(0x1e90ff)),
            Map.entry("firebrick", rgb(0xb22222)),
            Map.entry("floralwhite", rgb(0xfffaf0)),
            Map.entry("forestgreen", rgb(0x228b22)),
            Map.entry("fuchsia", rgb(0xff00ff)),
            Map.entry("gainsboro", rgb(0xdcdcdc)),
            Map.entry("ghostwhite", rgb(0xf8f8ff)),
            Map.entry("gold", rgb(0xffd700)),
            Map.entry("goldenrod", rgb(0xdaa520)),
            Map.entry("gray", rgb(0x808080)),
            Map.entry("green", rgb(0x008000)),
            Map.entry("greenyellow", rgb(0xadff2f)),
            Map.entry("grey", rgb(0x808080)),
            Map.entry("honeydew", rgb(0xf0fff0)),
            Map.entry("hotpink", rgb(0xff69b4)),
            Map.entry("indianred", rgb(0xcd5c5c)),
            Map.entry("indigo", rgb(0x4b0082)),
            Map.entry("ivory", rgb(0xfffff0)),
            Map.entry("khaki", rgb(0xf0e68c)),
            Map.entry("lavender", rgb(0xe6e6fa)),
            Map.entry("lavenderblush", rgb(0xfff0f5)),
            Map.entry("lawngreen", rgb(0x7cfc00)),
            Map.entry("lemonchiffon", rgb(0xfffacd)),
            Map.entry("lightblue", rgb(0xadd8e6)),
            Map.entry("lightcoral", rgb(0xf08080)),
            Map.entry("lightcyan", rgb(0xe0ffff)),
            Map.entry("lightgoldenrodyellow", rgb(0xfafad2)),
            Map.entry("lightgray", rgb(0xd3d3d3)),
            Map.entry("lightgreen", rgb(0x90ee90)),
            Map.entry("lightgrey", rgb(0xd3d3d3)),
            Map.entry("lightpink", rgb(0xffb6c1)),
            Map.entry("lightsalmon", rgb(0xffa07a)),
            Map.entry("lightseagreen", rgb(0x20b2aa)),
            Map.entry("lightskyblue", rgb(0x87cefa)),
            Map.entry("lightslategray", rgb(0x778899)),
            Map.entry("lightslategrey", rgb(0x778899)),
            Map.entry("lightsteelblue", rgb(0xb0c4de)),
            Map.entry("lightyellow", rgb(0xffffe0)),
            Map.entry("lime", rgb(0x00ff00)),
            Map.entry("limegreen", rgb(0x32cd32)),
            Map.entry("linen", rgb(0xfaf0e6)),
            Map.entry("magenta", rgb(0xff00ff)),
            Map.entry("maroon", rgb(0x800000)),
            Map.entry("mediumaquamarine", rgb(0x66cdaa)),
            Map.entry("mediumblue", rgb(0x0000cd)),
            Map.entry("mediumorchid", rgb(0xba55d3)),
            Map.entry("mediumpurple", rgb(0x9370db)),
            Map.entry("mediumseagreen", rgb(0x3cb371)),
            Map.entry("mediumslateblue", rgb(0x7b68ee)),
            Map.entry("mediumspringgreen", rgb(0x00fa9a)),
            Map.entry("mediumturquoise", rgb(0x48d1cc)),
            Map.entry("mediumvioletred", rgb(0xc71585)),
            Map.entry("midnightblue", rgb(0x191970)),
            Map.entry("mintcream", rgb(0xf5fffa)),
            Map.entry("mistyrose", rgb(0xffe4e1)),
            Map.entry("moccasin", rgb(0xffe4b5)),
            Map.entry("navajowhite", rgb(0xffdead)),
            Map.entry("navy", rgb(0x000080)),
            Map.entry("oldlace", rgb(0xfdf5e6)),
            Map.entry("olive", rgb(0x808000)),
            Map.entry("olivedrab", rgb(0x6b8e23)),
            Map.entry("orange", rgb(0xffa500)),
            Map.entry("orangered", rgb(0xff4500)),
            Map.entry("orchid", rgb(0xda70d6)),
            Map.entry("palegoldenrod", rgb(0xeee8aa)),
            Map.entry("palegreen", rgb(0x98fb98)),
            Map.entry("paleturquoise", rgb(0xafeeee)),
            Map.entry("palevioletred", rgb(0xdb7093)),
            Map.entry("papayawhip", rgb(0xffefd5)),
            Map.entry("peachpuff", rgb(0xffdab9)),
            Map.entry("peru", rgb(0xcd853f)),
            Map.entry("pink", rgb(0xffc0cb)),
            Map.entry("plum", rgb(0xdda0dd)),
            Map.entry("powderblue", rgb(0xb0e0e6)),
            Map.entry("purple", rgb(0x800080)),
            Map.entry("rebeccapurple", rgb(0x663399)),
            Map.entry("red", rgb(0xff0000)),
            Map.entry("rosybrown", rgb(0xbc8f8f)),
            Map.entry("royalblue", rgb(0x4169e1)),
            Map.entry("saddlebrown", rgb(0x8b4513)),
            Map.entry("salmon", rgb(0xfa8072)),
            Map.entry("sandybrown", rgb(0xf4a460)),
            Map.entry("seagreen", rgb(0x2e8b57)),
            Map.entry("seashell", rgb(0xfff5ee)),
            Map.entry("sienna", rgb(0xa0522d)),
            Map.entry("silver", rgb(0xc0c0c0)),
            Map.entry("skyblue", rgb(0x87ceeb)),
            Map.entry("slateblue", rgb(0x6a5acd)),
            Map.entry("slategray", rgb(0x708090)),
            Map.entry("slategrey", rgb(0x708090)),
            Map.entry("snow", rgb(0xfffafa)),
            Map.entry("springgreen", rgb(0x00ff7f)),
            Map.entry("steelblue", rgb(0x4682b4)),
            Map.entry("tan", rgb(0xd2b48c)),
            Map.entry("teal", rgb(0x008080)),
            Map.entry("thistle", rgb(0xd8bfd8)),
            Map.entry("tomato", rgb(0xff6347)),
            Map.entry("turquoise", rgb(0x40e0d0)),
            Map.entry("violet", rgb(0xee82ee)),
            Map.entry("wheat", rgb(0xf5deb3)),
            Map.entry("white", rgb(0xffffff)),
            Map.entry("whitesmoke", rgb(0xf5f5f5)),
            Map.entry("yellow", rgb(0xffff00)),
            Map.entry("yellowgreen", rgb(0x9acd32)),
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

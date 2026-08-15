package com.browicy.css21;

import java.awt.image.BufferedImage;

/**
 * Pixelweiser Vergleich zweier Screenshots.
 *
 * <p>Maße: {@code totalPixels} (beide Bilder identisch groß erwartet),
 * {@code differingPixels}, {@code diffRatio} (Anteil abweichender Pixel),
 * {@code meanAbsDiff} (mittlere maximale Kanaldifferenz über alle Pixel),
 * {@code maxAbsDiff} (größte Einzelkanaldifferenz).</p>
 */
public final class PixelComparator {

    /** Diff-Markierung im Diff-Bild: Magenta. */
    private static final int DIFF_COLOR = 0xFFFF00FF;

    private PixelComparator() {
    }

    public record Metrics(long totalPixels, long differingPixels, double diffRatio,
                          double meanAbsDiff, double maxAbsDiff) {
    }

    public static Metrics compare(BufferedImage expected, BufferedImage actual) {
        if (expected.getWidth() != actual.getWidth()
                || expected.getHeight() != actual.getHeight()) {
            throw new IllegalArgumentException("Bildgrößen weichen ab: erwartet "
                    + expected.getWidth() + "x" + expected.getHeight()
                    + ", ist " + actual.getWidth() + "x" + actual.getHeight());
        }
        long total = (long) expected.getWidth() * expected.getHeight();
        long differing = 0;
        double sum = 0;
        double max = 0;
        int width = expected.getWidth();
        int height = expected.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int expectedArgb = expected.getRGB(x, y);
                int actualArgb = actual.getRGB(x, y);
                if (expectedArgb != actualArgb) {
                    differing++;
                }
                double diff = channelDiff(expectedArgb, actualArgb);
                sum += diff;
                if (diff > max) {
                    max = diff;
                }
            }
        }
        return new Metrics(total, differing, (double) differing / total,
                sum / total, max);
    }

    /**
     * Diff-Bild für die Sichtprüfung: übernimmt die Browicy-Pixel und färbt
     * jede abweichende Stelle magenta ein.
     */
    public static BufferedImage diffImage(BufferedImage expected, BufferedImage actual) {
        int width = Math.min(expected.getWidth(), actual.getWidth());
        int height = Math.min(expected.getHeight(), actual.getHeight());
        BufferedImage diff = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int actualArgb = actual.getRGB(x, y);
                int expectedArgb = x < expected.getWidth() && y < expected.getHeight()
                        ? expected.getRGB(x, y) : 0;
                diff.setRGB(x, y, actualArgb == expectedArgb ? actualArgb : DIFF_COLOR);
            }
        }
        return diff;
    }

    private static double channelDiff(int expected, int actual) {
        int red = Math.abs(((expected >> 16) & 0xff) - ((actual >> 16) & 0xff));
        int green = Math.abs(((expected >> 8) & 0xff) - ((actual >> 8) & 0xff));
        int blue = Math.abs((expected & 0xff) - (actual & 0xff));
        int alpha = Math.abs(((expected >>> 24) & 0xff) - ((actual >>> 24) & 0xff));
        return Math.max(Math.max(red, green), Math.max(blue, alpha));
    }
}

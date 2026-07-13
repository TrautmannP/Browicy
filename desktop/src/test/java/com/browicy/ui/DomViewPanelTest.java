package com.browicy.ui;

import com.browicy.engine.dom.Document;
import com.browicy.engine.html.HtmlParser;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DomViewPanelTest {

    @Test
    public void rendersLargeDocumentsWithoutChildSwingComponents() {
        StringBuilder html = new StringBuilder("<body>");
        for (int index = 0; index < 1_500; index++) {
            html.append("<p>Absatz ").append(index)
                    .append(" mit ausreichend Text für den Rendering-Test.</p>");
        }
        html.append("</body>");

        DomViewPanel panel = new DomViewPanel(parse(html.toString()));

        assertEquals("Textblöcke dürfen keine Swing-Widgets mehr erzeugen",
                0, panel.getComponentCount());
        assertNull("Das Rendering darf nicht von einem Swing-Layout-Manager abhängen",
                panel.getLayout());
    }

    @Test
    public void reflowIncreasesPreferredHeightForNarrowerWidths() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body>
                  <p>Dieser Absatz enthält genügend Wörter, um bei einer schmalen Breite
                     auf deutlich mehr Zeilen als bei einer breiten Darstellung umzubrechen.</p>
                </body>
                """));

        panel.setSize(800, 1);
        Dimension wide = panel.getPreferredSize();
        panel.setSize(180, 1);
        Dimension narrow = panel.getPreferredSize();

        assertTrue("Schmaler Inhalt muss durch Word-Wrap höher werden",
                narrow.height > wide.height);
    }

    @Test
    public void paintLoopUsesComputedFontSizeAndTextColor() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body>
                  <p style="color: #ff0000; font-size: 30px">MMMM</p>
                </body>
                """));
        DomViewPanel normalSizePanel = new DomViewPanel(parse("""
                <body><p style="font-size: 12px">MMMM</p></body>
                """));
        panel.setSize(240, panel.getPreferredSize().height);
        normalSizePanel.setSize(240, normalSizePanel.getPreferredSize().height);

        assertTrue("Die berechnete Schriftgröße muss in die Reflow-Höhe eingehen",
                panel.getPreferredSize().height > normalSizePanel.getPreferredSize().height);

        BufferedImage image = new BufferedImage(
                panel.getWidth(), panel.getPreferredSize().height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            panel.paint(graphics);
        } finally {
            graphics.dispose();
        }

        assertTrue("Der Paint-Loop muss die berechnete rote Textfarbe verwenden",
                containsRedTextPixel(image));
    }

    private static Document parse(String html) {
        return new HtmlParser().parse(html);
    }

    private static boolean containsRedTextPixel(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = argb >>> 24;
                int red = argb >>> 16 & 0xff;
                int green = argb >>> 8 & 0xff;
                int blue = argb & 0xff;
                if (alpha > 0 && red > 150 && green < 120 && blue < 120) {
                    return true;
                }
            }
        }
        return false;
    }
}

package com.browicy.ui;

import com.browicy.engine.dom.Document;
import com.browicy.engine.html.HtmlParser;
import com.browicy.engine.render.CssColor;
import com.browicy.ui.render.RenderLayoutEngine.BoxFragment;
import com.browicy.ui.render.RenderLayoutEngine.TextFragment;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
    public void layoutKeepsNestedInlineStylesAsSeparateTextFragments() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><p style="color: blue">Normal
                  <span style="color: red; font-size: 24px"><strong>red</strong></span>
                  end</p></body>
                """));

        List<TextFragment> fragments = panel.layoutForTesting(400).fragments().stream()
                .filter(TextFragment.class::isInstance)
                .map(TextFragment.class::cast)
                .toList();
        TextFragment red = fragments.stream()
                .filter(fragment -> fragment.text().contains("red"))
                .findFirst()
                .orElse(null);
        TextFragment normal = fragments.stream()
                .filter(fragment -> fragment.text().contains("Normal"))
                .findFirst()
                .orElse(null);

        assertNotNull(red);
        assertNotNull(normal);
        assertEquals(CssColor.parse("red"), red.color());
        assertEquals(CssColor.parse("blue"), normal.color());
        assertTrue(red.font().isBold());
        assertEquals(24, red.font().getSize());
    }

    @Test
    public void blockBoxGeometryIncludesMarginBorderAndPadding() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div style="margin: 5px; padding: 10px;
                  border: 3px solid blue; background-color: yellow">X</div></body>
                """));

        BoxFragment box = panel.layoutForTesting(300).fragments().stream()
                .filter(BoxFragment.class::isInstance)
                .map(BoxFragment.class::cast)
                .filter(fragment -> fragment.box().tagName().equals("div"))
                .findFirst()
                .orElseThrow();

        assertEquals(21f, box.x(), 0.001f);
        assertEquals(21f, box.y(), 0.001f);
        assertEquals(258f, box.width(), 0.001f);

        DomViewPanel plain = new DomViewPanel(parse("<body><div>X</div></body>"));
        panel.setSize(300, 1);
        plain.setSize(300, 1);
        assertTrue(panel.getPreferredSize().height >= plain.getPreferredSize().height + 35);
    }

    @Test
    public void paintLoopDrawsBlockBackgroundBorderAndStyledText() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div style="margin: 5px; padding: 10px; border: 3px solid blue;
                  background-color: yellow; color: red; font-size: 30px">MMMM</div></body>
                """));
        panel.setSize(300, panel.getPreferredSize().height);

        BufferedImage image = paint(panel);

        assertColor(image, 21, 21, CssColor.parse("blue"));
        assertColor(image, 25, 25, CssColor.parse("yellow"));
        assertTrue("Der Paint-Loop muss die berechnete rote Textfarbe verwenden",
                containsRedTextPixel(image));
    }

    @Test
    public void paintLoopUsesComputedFontSizeInReflow() {
        DomViewPanel large = new DomViewPanel(parse("""
                <body><p style="font-size: 30px">MMMM</p></body>
                """));
        DomViewPanel small = new DomViewPanel(parse("""
                <body><p style="font-size: 12px">MMMM</p></body>
                """));
        large.setSize(240, 1);
        small.setSize(240, 1);

        assertTrue("Die berechnete Schriftgröße muss in die Reflow-Höhe eingehen",
                large.getPreferredSize().height > small.getPreferredSize().height);
    }

    private static Document parse(String html) {
        return new HtmlParser().parse(html);
    }

    private static BufferedImage paint(DomViewPanel panel) {
        int height = panel.getPreferredSize().height;
        panel.setSize(panel.getWidth(), height);
        BufferedImage image = new BufferedImage(
                panel.getWidth(), height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            panel.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void assertColor(BufferedImage image, int x, int y, CssColor expected) {
        int argb = image.getRGB(x, y);
        assertEquals(expected.alpha(), argb >>> 24);
        assertEquals(expected.red(), argb >>> 16 & 0xff);
        assertEquals(expected.green(), argb >>> 8 & 0xff);
        assertEquals(expected.blue(), argb & 0xff);
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

package com.browicy.ui;

import com.browicy.engine.dom.Document;
import com.browicy.engine.html.HtmlParser;
import com.browicy.engine.render.CssColor;
import com.browicy.ui.render.RenderLayoutEngine.BoxFragment;
import com.browicy.ui.render.RenderLayoutEngine.InlineBoxFragment;
import com.browicy.ui.render.RenderLayoutEngine.LayoutResult;
import com.browicy.ui.render.RenderLayoutEngine.LineBox;
import com.browicy.ui.render.RenderLayoutEngine.TextFragment;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void laysOutAndPaintsInlineBackgroundPaddingAndBorder() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><p>A <span style="background-color: red; padding: 4px;
                  border: 2px solid blue">B</span> C</p></body>
                """));
        panel.setSize(300, 1);

        LayoutResult layout = panel.layoutForTesting(300);
        InlineBoxFragment inline = layout.fragments().stream()
                .filter(InlineBoxFragment.class::isInstance)
                .map(InlineBoxFragment.class::cast)
                .findFirst()
                .orElseThrow();

        assertTrue(inline.firstFragment());
        assertTrue(inline.lastFragment());
        assertTrue(inline.width() > 12);
        assertTrue(inline.height() > 20);

        BufferedImage image = paint(panel);
        assertColor(image, (int) inline.x(), (int) inline.y(), CssColor.parse("blue"));
        assertColor(image, (int) inline.x() + 3, (int) inline.y() + 3,
                CssColor.parse("red"));
    }

    @Test
    public void splitsLongInlineBoxesIntoFirstMiddleAndLastFragments() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><p><span style="padding: 4px; border: 2px solid blue;
                  background-color: yellow">one two three four five six seven eight nine ten
                  eleven twelve thirteen fourteen</span></p></body>
                """));

        List<InlineBoxFragment> fragments = panel.layoutForTesting(150).fragments().stream()
                .filter(InlineBoxFragment.class::isInstance)
                .map(InlineBoxFragment.class::cast)
                .filter(fragment -> fragment.box().tagName().equals("span"))
                .toList();

        assertTrue("Der Span muss über mehrere Zeilen fragmentiert werden", fragments.size() >= 3);
        assertTrue(fragments.getFirst().firstFragment());
        assertFalse(fragments.getFirst().lastFragment());
        assertFalse(fragments.get(1).firstFragment());
        assertFalse(fragments.get(1).lastFragment());
        assertFalse(fragments.getLast().firstFragment());
        assertTrue(fragments.getLast().lastFragment());

        panel.setSize(150, 1);
        BufferedImage image = paint(panel);
        InlineBoxFragment first = fragments.getFirst();
        InlineBoxFragment middle = fragments.get(1);
        InlineBoxFragment last = fragments.getLast();
        assertColor(image, Math.round(first.x()), Math.round(first.y()) + 3,
                CssColor.parse("blue"));
        assertColor(image, Math.round(first.x() + first.width()) - 1,
                Math.round(first.y()) + 3, CssColor.parse("yellow"));
        assertColor(image, Math.round(middle.x()), Math.round(middle.y()) + 3,
                CssColor.parse("yellow"));
        assertColor(image, Math.round(last.x() + last.width()) - 1,
                Math.round(last.y()) + 3, CssColor.parse("blue"));
    }


    @Test
    public void longWordInsideInlineBoxDoesNotCreateEmptyContinuationLines() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><p><span style="padding: 3px; border: 1px solid blue">
                  Supercalifragilisticexpialidocious</span></p></body>
                """));

        LayoutResult layout = panel.layoutForTesting(90);

        assertTrue(layout.lineBoxes().size() >= 2);
        assertTrue(layout.lineBoxes().stream().allMatch(line -> line.fragments().stream()
                .anyMatch(TextFragment.class::isInstance)));
        assertTrue(layout.fragments().stream()
                .filter(InlineBoxFragment.class::isInstance)
                .map(InlineBoxFragment.class::cast)
                .allMatch(fragment -> fragment.width() > 0));
    }

    @Test
    public void preservesNestedInlineFragmentGeometry() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><p><span style="padding: 4px">normal
                  <strong style="border: 1px solid blue">bold</strong></span></p></body>
                """));

        List<InlineBoxFragment> fragments = panel.layoutForTesting(400).fragments().stream()
                .filter(InlineBoxFragment.class::isInstance)
                .map(InlineBoxFragment.class::cast)
                .toList();
        InlineBoxFragment span = fragments.stream()
                .filter(fragment -> fragment.box().tagName().equals("span"))
                .findFirst().orElseThrow();
        InlineBoxFragment strong = fragments.stream()
                .filter(fragment -> fragment.box().tagName().equals("strong"))
                .findFirst().orElseThrow();

        assertTrue(strong.x() >= span.x() + 4);
        assertTrue(strong.bottom() <= span.bottom());
        assertTrue(strong.top() >= span.top());
        assertTrue(strong.width() > 2);
    }

    @Test
    public void lineBoxUsesOneBaselineAndTallestInlineMetrics() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><p>small <span style="font-size: 32px">BIG</span> end</p></body>
                """));

        LayoutResult layout = panel.layoutForTesting(500);
        LineBox line = layout.lineBoxes().getFirst();
        List<TextFragment> texts = line.fragments().stream()
                .filter(TextFragment.class::isInstance)
                .map(TextFragment.class::cast)
                .toList();

        assertTrue(texts.size() >= 3);
        assertTrue(texts.stream().allMatch(text -> text.baseline() == line.baseline()));
        float smallHeight = texts.stream()
                .filter(text -> text.font().getSize() == 16)
                .findFirst().orElseThrow().height();
        assertTrue(line.height() > smallHeight);
    }

    @Test
    public void emptyInlineBoxStillProducesVisiblePaddingAndBorder() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><p>A<span style="padding: 5px; border: 1px solid blue"></span>B</p></body>
                """));
        panel.setSize(250, 1);

        InlineBoxFragment empty = panel.layoutForTesting(250).fragments().stream()
                .filter(InlineBoxFragment.class::isInstance)
                .map(InlineBoxFragment.class::cast)
                .findFirst()
                .orElseThrow();

        assertEquals(12f, empty.width(), 0.001f);
        assertTrue(empty.height() >= 30f);
        BufferedImage image = paint(panel);
        assertColor(image, (int) empty.x(), (int) empty.y(), CssColor.parse("blue"));
    }

    @Test
    public void mixedInlineAndBlockContentCreatesSeparateLineGroups() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div>before<p>paragraph</p>after</div></body>
                """));

        List<LineBox> lines = panel.layoutForTesting(400).lineBoxes();

        assertEquals(3, lines.size());
        assertTrue(lines.get(0).y() < lines.get(1).y());
        assertTrue(lines.get(1).y() < lines.get(2).y());
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

package com.browicy.ui;

import com.browicy.engine.css.StyleApplicator;
import com.browicy.engine.ImageResourceRegistry;
import com.browicy.engine.dom.Document;
import com.browicy.engine.html.HtmlParser;
import com.browicy.engine.js.PageRuntime;
import com.browicy.engine.net.BinaryResource;
import com.browicy.engine.net.NetworkResourceType;
import com.browicy.engine.render.CssColor;
import com.browicy.ui.render.RenderLayoutEngine.BoxFragment;
import com.browicy.ui.render.RenderLayoutEngine.InlineBoxFragment;
import com.browicy.ui.render.RenderLayoutEngine.ImageFragment;
import com.browicy.ui.render.RenderLayoutEngine.LayoutResult;
import com.browicy.ui.render.RenderLayoutEngine.LineBox;
import com.browicy.ui.render.RenderLayoutEngine.TextFragment;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DomViewPanelTest {

    @Test
    public void capturesViewportAndFullPageScreenshots() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body style="background: white; color: black">
                  <div style="height: 900px">Screenshot content</div>
                </body>
                """));
        try {
            BufferedImage viewport = panel.captureScreenshot(320, 200, false);
            BufferedImage fullPage = panel.captureScreenshot(320, 200, true);

            assertEquals(320, viewport.getWidth());
            assertEquals(200, viewport.getHeight());
            assertEquals(320, fullPage.getWidth());
            assertTrue(fullPage.getHeight() > viewport.getHeight());
        } finally {
            panel.dispose();
        }
    }

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

    @Test
    public void appliesFixedAndPercentageBlockDimensions() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div id="fixed" style="width:100px;height:40px"></div>
                <div id="percent" style="width:50%"></div></body>
                """));

        List<BoxFragment> boxes = panel.layoutForTesting(400).fragments().stream()
                .filter(BoxFragment.class::isInstance)
                .map(BoxFragment.class::cast)
                .filter(fragment -> fragment.box().source() != null)
                .toList();
        BoxFragment fixed = boxes.stream()
                .filter(fragment -> "fixed".equals(fragment.box().source().getAttribute("id")))
                .findFirst().orElseThrow();
        BoxFragment percent = boxes.stream()
                .filter(fragment -> "percent".equals(fragment.box().source().getAttribute("id")))
                .findFirst().orElseThrow();

        assertEquals(100f, fixed.width(), 0.001f);
        assertEquals(40f, fixed.height(), 0.001f);
        assertEquals(184f, percent.width(), 0.001f);
    }

    @Test
    public void centersFixedWidthBlocksWithAutomaticHorizontalMargins() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div style="width:100px;margin-left:auto;margin-right:auto">X</div></body>
                """));

        BoxFragment box = panel.layoutForTesting(400).fragments().stream()
                .filter(BoxFragment.class::isInstance)
                .map(BoxFragment.class::cast)
                .filter(fragment -> fragment.box().tagName().equals("div"))
                .findFirst().orElseThrow();

        assertEquals(150f, box.x(), 0.001f);
        assertEquals(100f, box.width(), 0.001f);
    }

    @Test
    public void alignsInlineLinesWithinTheirContainingBlock() {
        DomViewPanel centered = new DomViewPanel(parse("""
                <body><p style="text-align:center">centered</p></body>
                """));
        DomViewPanel right = new DomViewPanel(parse("""
                <body><p style="text-align:right">right</p></body>
                """));

        LineBox centerLine = centered.layoutForTesting(400).lineBoxes().getFirst();
        LineBox rightLine = right.layoutForTesting(400).lineBoxes().getFirst();

        assertEquals(200f, centerLine.x() + centerLine.width() / 2f, 1f);
        assertEquals(384f, rightLine.x() + rightLine.width(), 0.001f);
    }

    @Test
    public void laysOutInlineBlocksAtomicallyAndPaintsTheirBoxes() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div><span id="one" style="display:inline-block;width:70px;height:40px;
                  background-color:red;border:2px solid blue">one</span>
                <span id="two" style="display:inline-block;width:80px;height:30px;
                  background-color:yellow">two</span></div></body>
                """));
        panel.setSize(400, 1);

        LayoutResult layout = panel.layoutForTesting(400);
        List<BoxFragment> inlineBlocks = layout.fragments().stream()
                .filter(BoxFragment.class::isInstance)
                .map(BoxFragment.class::cast)
                .filter(fragment -> fragment.box().style().display()
                        == com.browicy.engine.render.RenderStyle.Display.INLINE_BLOCK)
                .toList();

        assertEquals(2, inlineBlocks.size());
        assertEquals(74f, inlineBlocks.get(0).width(), 0.001f);
        assertEquals(44f, inlineBlocks.get(0).height(), 0.001f);
        assertTrue(inlineBlocks.get(1).x() >= inlineBlocks.get(0).x() + 74f);
        assertEquals(1, layout.lineBoxes().stream()
                .filter(line -> line.height() >= 40f)
                .count());

        BufferedImage image = paint(panel);
        BoxFragment first = inlineBlocks.getFirst();
        assertColor(image, Math.round(first.x()), Math.round(first.y()),
                CssColor.parse("blue"));
        assertColor(image, Math.round(first.x()) + 3, Math.round(first.y()) + 3,
                CssColor.parse("red"));
    }

    @Test
    public void treatsDeclaredDimensionsAsContentBoxDimensions() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div id="box" style="width:100px;height:40px;padding:10px;
                  border:2px solid blue"></div></body>
                """));

        BoxFragment box = boxById(panel.layoutForTesting(400), "box");

        assertEquals(124f, box.width(), 0.001f);
        assertEquals(64f, box.height(), 0.001f);
    }

    @Test
    public void opacityDoesNotLeakIntoFollowingPaintFragments() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body style="background-color:white">
                  <div id="faded" style="width:40px;height:20px;background-color:red;opacity:.25"></div>
                  <div id="opaque" style="width:40px;height:20px;background-color:blue"></div>
                </body>
                """));
        panel.setSize(120, 1);
        LayoutResult layout = panel.layoutForTesting(120);
        BoxFragment faded = boxById(layout, "faded");
        BoxFragment opaque = boxById(layout, "opaque");
        BufferedImage image = paint(panel);

        int fadedPixel = image.getRGB(Math.round(faded.x() + 10), Math.round(faded.y() + 10));
        assertTrue(new java.awt.Color(fadedPixel, true).getRed() > 0);
        assertColor(image, Math.round(opaque.x() + 10), Math.round(opaque.y() + 10),
                CssColor.parse("blue"));
    }

    @Test
    public void rgbaBackgroundIsAlphaCompositedOverThePage() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body style="background-color:white">
                  <div id="alpha" style="width:30px;height:20px;background-color:rgba(255,0,0,.5)"></div>
                </body>
                """));
        panel.setSize(80, 1);
        BoxFragment box = boxById(panel.layoutForTesting(80), "alpha");
        java.awt.Color pixel = new java.awt.Color(paint(panel).getRGB(
                Math.round(box.x() + 10), Math.round(box.y() + 10)), true);

        assertEquals(255, pixel.getRed(), 2);
        assertEquals(127, pixel.getGreen(), 2);
        assertEquals(127, pixel.getBlue(), 2);
    }

    @Test
    public void mediaQueriesAreReevaluatedForTheLayoutViewport() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <html><head><style>
                  .label { color:red }
                  @media (max-width:400px) { .label { color:blue } }
                </style></head><body><span class="label">responsive</span></body></html>
                """));

        TextFragment wide = panel.layoutForTesting(800, 600).fragments().stream()
                .filter(TextFragment.class::isInstance).map(TextFragment.class::cast)
                .filter(fragment -> fragment.text().contains("responsive")).findFirst().orElseThrow();
        TextFragment narrow = panel.layoutForTesting(360, 600).fragments().stream()
                .filter(TextFragment.class::isInstance).map(TextFragment.class::cast)
                .filter(fragment -> fragment.text().contains("responsive")).findFirst().orElseThrow();

        assertEquals(CssColor.parse("red"), wide.color());
        assertEquals(CssColor.parse("blue"), narrow.color());
    }

    @Test
    public void subtractsPaddingAndBorderFromBorderBoxDimensions() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div id="box" style="box-sizing:border-box;width:100px;height:40px;
                  padding:10px;border:2px solid blue"><div id="child" style="height:100%"></div>
                </div><div id="limited" style="box-sizing:border-box;width:200px;max-width:80px;
                  padding:10px;border:2px solid blue"></div></body>
                """));

        LayoutResult layout = panel.layoutForTesting(400);
        BoxFragment box = boxById(layout, "box");
        BoxFragment child = boxById(layout, "child");
        BoxFragment limited = boxById(layout, "limited");

        assertEquals(100f, box.width(), 0.001f);
        assertEquals(40f, box.height(), 0.001f);
        assertEquals(76f, child.width(), 0.001f);
        assertEquals(16f, child.height(), 0.001f);
        assertEquals(80f, limited.width(), 0.001f);
    }

    @Test
    public void shrinkWrapsAutomaticInlineBlockWidthAroundItsContent() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div><span id="chip" style="display:inline-block;padding:4px;
                  border:1px solid black">compact</span></div></body>
                """));

        LayoutResult layout = panel.layoutForTesting(400);
        BoxFragment chip = boxById(layout, "chip");
        TextFragment text = layout.fragments().stream()
                .filter(TextFragment.class::isInstance)
                .map(TextFragment.class::cast)
                .filter(fragment -> fragment.text().equals("compact"))
                .findFirst().orElseThrow();

        assertEquals(text.width() + 10f, chip.width(), 0.001f);
        assertTrue(chip.width() < 120f);
    }

    @Test
    public void resolvesPercentageHeightsOnlyAgainstDefiniteContainingHeights() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div><div id="auto-percent" style="height:50%">content</div></div>
                <div style="height:100px"><div id="fixed-percent" style="height:50%"></div></div>
                </body>
                """));

        LayoutResult layout = panel.layoutForTesting(400);
        BoxFragment automatic = boxById(layout, "auto-percent");
        BoxFragment definite = boxById(layout, "fixed-percent");

        assertTrue(automatic.height() > 10f);
        assertTrue(automatic.height() < 40f);
        assertEquals(50f, definite.height(), 0.001f);
    }

    @Test
    public void resolvesRootPercentageHeightAgainstViewport() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <html style="height:100%"><body style="height:100%;margin:0">
                  <div id="page" style="height:100%;display:flex;flex-direction:column">
                    <div style="height:40px"></div>
                    <div id="content" style="flex:1 1 0%"></div>
                    <div style="height:30px"></div>
                  </div>
                </body></html>
                """));

        LayoutResult layout = panel.layoutForTesting(400, 700);

        assertEquals(700f, boxById(layout, "page").height(), 0.001f);
        assertEquals(630f, boxById(layout, "content").height(), 0.001f);
    }

    @Test
    public void resolvesSimpleCalcPercentageDimensions() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div id="box" style="width:calc(100% - 40px);height:200px">
                  <div id="child" style="height:calc(100% - 25px)"></div>
                </div></body>
                """));

        LayoutResult layout = panel.layoutForTesting(400, 600);

        assertEquals(328f, boxById(layout, "box").width(), 0.001f);
        assertEquals(175f, boxById(layout, "child").height(), 0.001f);
    }

    @Test
    public void appliesMinMaxConstraintsAndMarksOverflowForClipping() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div id="limited" style="width:40px;min-width:80px;max-width:120px;
                  height:80px;min-height:20px;max-height:30px;overflow:hidden">
                  overflowing content that cannot fit</div></body>
                """));

        LayoutResult layout = panel.layoutForTesting(400);
        BoxFragment limited = boxById(layout, "limited");
        TextFragment clippedText = layout.fragments().stream()
                .filter(TextFragment.class::isInstance)
                .map(TextFragment.class::cast)
                .filter(fragment -> fragment.clip() != null)
                .findFirst().orElseThrow();

        assertEquals(80f, limited.width(), 0.001f);
        assertEquals(30f, limited.height(), 0.001f);
        assertNotNull(clippedText.clip());
        assertEquals(limited.width(), clippedText.clip().width(), 0.001f);
        assertEquals(limited.height(), clippedText.clip().height(), 0.001f);
    }

    @Test
    public void verticallyAlignsDifferentlySizedInlineBlocksAtLineEdges() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div><span id="top" style="display:inline-block;width:20px;height:20px;
                  vertical-align:top"></span><span id="bottom" style="display:inline-block;
                  width:20px;height:40px;vertical-align:bottom"></span></div></body>
                """));

        LayoutResult layout = panel.layoutForTesting(400);
        BoxFragment top = boxById(layout, "top");
        BoxFragment bottom = boxById(layout, "bottom");
        LineBox line = layout.lineBoxes().stream()
                .filter(candidate -> candidate.height() >= 40f)
                .findFirst().orElseThrow();

        assertEquals(line.y(), top.y(), 0.001f);
        assertEquals(line.y() + line.height(), bottom.y() + bottom.height(), 0.001f);
    }

    @Test
    public void usesLastLineOfVisibleInlineBlockAsItsBaseline() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div><span id="multi" style="display:inline-block">first<br>
                  <span style="font-size:24px">last</span></span><span id="single"
                  style="display:inline-block">single</span></div></body>
                """));

        LayoutResult layout = panel.layoutForTesting(400);
        TextFragment last = textByValue(layout, "last");
        TextFragment single = textByValue(layout, "single");

        assertEquals(last.baseline(), single.baseline(), 0.001f);
    }

    @Test
    public void usesInlineBlockBottomAsBaselineWhenOverflowIsNotVisible() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div><span id="clipped" style="display:inline-block;overflow:hidden">
                  first<br><span style="font-size:24px">last</span></span><span
                  style="display:inline-block">single</span></div></body>
                """));

        LayoutResult layout = panel.layoutForTesting(400);
        BoxFragment clipped = boxById(layout, "clipped");
        TextFragment single = textByValue(layout, "single");

        assertEquals(clipped.y() + clipped.height(), single.baseline(), 0.001f);
    }

    @Test
    public void alignsFlexItemsOnTheirFirstTextBaselines() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div style="display:flex;align-items:baseline">
                  <div><span style="font-size:30px">Large</span><br>second</div>
                  <div style="font-size:12px">Small</div>
                </div></body>
                """));

        LayoutResult layout = panel.layoutForTesting(400);

        assertEquals(textByValue(layout, "Large").baseline(),
                textByValue(layout, "Small").baseline(), 0.001f);
    }

    private static TextFragment textByValue(LayoutResult layout, String value) {
        return layout.fragments().stream()
                .filter(TextFragment.class::isInstance)
                .map(TextFragment.class::cast)
                .filter(fragment -> fragment.text().strip().equals(value))
                .findFirst().orElseThrow();
    }

    private static BoxFragment boxById(LayoutResult layout, String id) {
        return layout.fragments().stream()
                .filter(BoxFragment.class::isInstance)
                .map(BoxFragment.class::cast)
                .filter(fragment -> fragment.box().source() != null)
                .filter(fragment -> id.equals(fragment.box().source().getAttribute("id")))
                .findFirst().orElseThrow();
    }

    @Test
    public void collapsesAdjacentVerticalBlockMarginsToTheLargerMargin() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div id="first" style="height:10px;margin-bottom:30px"></div>
                  <div id="second" style="height:10px;margin-top:20px"></div></body>
                """));

        LayoutResult layout = panel.layoutForTesting(400);
        BoxFragment first = boxById(layout, "first");
        BoxFragment second = boxById(layout, "second");

        assertEquals(30f, second.y() - (first.y() + first.height()), 0.001f);
    }

    @Test
    public void resolvesRemAndViewportUnitsFromTheCurrentViewport() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <html style="font-size:20px"><body>
                  <div id="viewport-box" style="width:50vw;height:25vh;margin-left:1rem"></div>
                </body></html>
                """));

        BoxFragment small = boxById(panel.layoutForTesting(400, 600), "viewport-box");
        BoxFragment large = boxById(panel.layoutForTesting(600, 800), "viewport-box");

        assertEquals(200f, small.width(), 0.001f);
        assertEquals(150f, small.height(), 0.001f);
        assertEquals(36f, small.x(), 0.001f);
        assertEquals(300f, large.width(), 0.001f);
        assertEquals(200f, large.height(), 0.001f);
        assertEquals(36f, large.x(), 0.001f);
    }

    @Test
    public void absoluteBlocksArePositionedAndRemovedFromNormalFlow() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div id="container" style="position: relative; width: 200px; height: 100px">
                  <div id="before" style="height: 20px"></div>
                  <div id="overlay" style="position: absolute; left: 20px; top: 10px; width: 30px; height: 70px"></div>
                  <div id="after" style="height: 20px"></div>
                </div></body>
                """));

        LayoutResult layout = panel.layoutForTesting(400);
        BoxFragment container = boxById(layout, "container");
        BoxFragment before = boxById(layout, "before");
        BoxFragment overlay = boxById(layout, "overlay");
        BoxFragment after = boxById(layout, "after");

        assertEquals(container.x() + 20, overlay.x(), 0.001f);
        assertEquals(container.y() + 10, overlay.y(), 0.001f);
        assertEquals(before.y() + before.height(), after.y(), 0.001f);
    }

    @Test
    public void relativeBlocksMoveVisuallyButKeepTheirFlowPosition() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div id="container" style="width: 200px">
                  <div id="shifted" style="position: relative; left: 11px; top: 7px; height: 20px"></div>
                  <div id="after" style="height: 20px"></div>
                </div></body>
                """));

        LayoutResult layout = panel.layoutForTesting(400);
        BoxFragment container = boxById(layout, "container");
        BoxFragment shifted = boxById(layout, "shifted");
        BoxFragment after = boxById(layout, "after");

        assertEquals(container.x() + 11, shifted.x(), 0.001f);
        assertEquals(container.y() + 7, shifted.y(), 0.001f);
        assertEquals(container.y() + 20, after.y(), 0.001f);
    }

    @Test
    public void relativeInlineBoxesMoveWithoutChangingTheLineFlow() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><p><span id="shifted" style="position: relative; left: 10px; top: 5px">A</span><span id="after">B</span></p></body>
                """));

        LayoutResult layout = panel.layoutForTesting(400);
        InlineBoxFragment shifted = inlineBoxById(layout, "shifted");
        InlineBoxFragment after = inlineBoxById(layout, "after");
        LineBox line = layout.lineBoxes().getFirst();

        assertEquals(line.x() + 10, shifted.x(), 0.001f);
        assertEquals(line.y() + 5, shifted.y(), 0.001f);
        assertEquals(line.y(), after.y(), 0.001f);
    }

    @Test
    public void absoluteBlocksUseTheNearestPositionedAncestorThroughStaticBoxes() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div id="outer" style="position: relative; width: 200px; height: 100px">
                  <div id="static-wrapper" style="padding: 30px">
                    <div id="overlay" style="position: absolute; right: 10px; bottom: 5px; width: 20px; height: 10px"></div>
                  </div>
                </div></body>
                """));

        LayoutResult layout = panel.layoutForTesting(400);
        BoxFragment outer = boxById(layout, "outer");
        BoxFragment overlay = boxById(layout, "overlay");

        assertEquals(outer.x() + outer.width() - 10 - overlay.width(), overlay.x(), 0.001f);
        assertEquals(outer.y() + outer.height() - 5 - overlay.height(), overlay.y(), 0.001f);
    }

    private static InlineBoxFragment inlineBoxById(LayoutResult layout, String id) {
        return layout.fragments().stream()
                .filter(InlineBoxFragment.class::isInstance)
                .map(InlineBoxFragment.class::cast)
                .filter(fragment -> id.equals(fragment.box().source().getAttribute("id")))
                .findFirst().orElseThrow();
    }

    @Test
    public void refreshFromDocumentRebuildsRenderTreeAfterStyleChanges() {
        Document document = parse("""
                <body><p id="message" style="color: red">Text</p></body>
                """);
        DomViewPanel panel = new DomViewPanel(document);
        panel.setSize(300, 1);
        TextFragment before = panel.layoutForTesting(300).fragments().stream()
                .filter(TextFragment.class::isInstance)
                .map(TextFragment.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(CssColor.parse("red"), before.color());

        document.getElementById("message").setAttribute("style", "color: blue");
        new StyleApplicator().apply(document);
        panel.refreshFromDocument();

        TextFragment after = panel.layoutForTesting(300).fragments().stream()
                .filter(TextFragment.class::isInstance)
                .map(TextFragment.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(CssColor.parse("blue"), after.color());
    }

    @Test
    public void usesIntrinsicImageRatioAndPaintsTheScaledBitmap() throws Exception {
        Document document = parse("""
                <body><p>before<img id="image" src="https://example.test/image.png"
                  style="width:80px">after</p></body>
                """);
        BufferedImage source = new BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sourceGraphics = source.createGraphics();
        sourceGraphics.setColor(java.awt.Color.RED);
        sourceGraphics.fillRect(0, 0, 40, 20);
        sourceGraphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);
        ImageResourceRegistry images = new ImageResourceRegistry();
        images.register(document.getElementById("image"), new BinaryResource(
                URI.create("https://example.test/image.png"), 200, bytes.toByteArray(),
                NetworkResourceType.IMAGE));
        DomViewPanel panel = new DomViewPanel(document, PageRuntime.closed(), images);
        panel.setSize(300, 1);

        ImageFragment fragment = panel.layoutForTesting(300).fragments().stream()
                .filter(ImageFragment.class::isInstance)
                .map(ImageFragment.class::cast)
                .findFirst().orElseThrow();

        assertEquals(80f, fragment.width(), 0.001f);
        assertEquals(40f, fragment.height(), 0.001f);
        assertNotNull(fragment.bitmap());
        BufferedImage painted = paint(panel);
        assertColor(painted, Math.round(fragment.x() + fragment.width() / 2),
                Math.round(fragment.y() + fragment.height() / 2), CssColor.parse("red"));
    }

    @Test
    public void reservesHtmlSizedPlaceholderAndClipsItWithItsParent() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div style="height:20px;overflow:hidden">
                  <img width="90" height="60" src="https://example.test/missing.png">
                </div></body>
                """));

        ImageFragment fragment = panel.layoutForTesting(250).fragments().stream()
                .filter(ImageFragment.class::isInstance)
                .map(ImageFragment.class::cast)
                .findFirst().orElseThrow();

        assertEquals(90f, fragment.width(), 0.001f);
        assertEquals(60f, fragment.height(), 0.001f);
        assertNull(fragment.bitmap());
        assertNotNull(fragment.clip());
        assertEquals(20f, fragment.clip().height(), 0.001f);
    }

    @Test
    public void refusesToDecodeImagesExceedingTheDimensionLimitAndShowsPlaceholder()
            throws Exception {
        Document document = parse("""
                <body><img id="riesig" width="90" height="60"
                  src="https://example.test/riesig.png"></body>
                """);
        BufferedImage oversized = new BufferedImage(9000, 1, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(oversized, "png", bytes);
        ImageResourceRegistry images = new ImageResourceRegistry();
        images.register(document.getElementById("riesig"), new BinaryResource(
                URI.create("https://example.test/riesig.png"), 200, bytes.toByteArray(),
                NetworkResourceType.IMAGE));
        DomViewPanel panel = new DomViewPanel(document, PageRuntime.closed(), images);
        panel.setSize(300, 1);

        ImageFragment fragment = panel.layoutForTesting(300).fragments().stream()
                .filter(ImageFragment.class::isInstance)
                .map(ImageFragment.class::cast)
                .findFirst().orElseThrow();

        assertNull(fragment.bitmap());
        assertEquals(90f, fragment.width(), 0.001f);
        assertEquals(60f, fragment.height(), 0.001f);
    }

    @Test
    public void laysOutImagesNestedBelowLegacyCenterElements() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><center><br><div>
                  <img id="hplogo" width="272" height="92"
                    src="https://example.test/google.png">
                </div></center></body>
                """));

        ImageFragment logo = panel.layoutForTesting(1000).fragments().stream()
                .filter(ImageFragment.class::isInstance)
                .map(ImageFragment.class::cast)
                .filter(fragment -> "hplogo".equals(
                        fragment.image().source().getAttribute("id")))
                .findFirst().orElseThrow();

        assertEquals(272f, logo.width(), 0.001f);
        assertEquals(92f, logo.height(), 0.001f);
    }

    @Test
    public void jsvgPaintsInlineSvgShapesAndGradients() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body style="background-color:white"><svg width="40" height="20" viewBox="0 0 40 20">
                  <defs><linearGradient id="paint"><stop offset="0" stop-color="red"></stop>
                    <stop offset="1" stop-color="blue"></stop></linearGradient></defs>
                  <rect width="40" height="20" fill="url(#paint)"></rect>
                </svg></body>
                """));
        panel.setSize(100, 1);
        ImageFragment fragment = panel.layoutForTesting(100).fragments().stream()
                .filter(ImageFragment.class::isInstance).map(ImageFragment.class::cast)
                .findFirst().orElseThrow();
        BufferedImage image = paint(panel);

        java.awt.Color left = new java.awt.Color(image.getRGB(
                Math.round(fragment.x() + 3), Math.round(fragment.y() + 10)), true);
        java.awt.Color right = new java.awt.Color(image.getRGB(
                Math.round(fragment.x() + 36), Math.round(fragment.y() + 10)), true);
        assertTrue(left.getRed() > left.getBlue());
        assertTrue(right.getBlue() > right.getRed());
    }

    @Test
    public void jsvgReceivesCascadedCurrentColor() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <html><head><style>.icon { fill:currentColor }</style></head>
                  <body style="color:#4285f4;background-color:white">
                    <svg class="icon" width="20" height="20" viewBox="0 0 20 20">
                      <rect width="20" height="20"></rect>
                    </svg>
                  </body></html>
                """));
        panel.setSize(80, 1);
        ImageFragment fragment = panel.layoutForTesting(80).fragments().stream()
                .filter(ImageFragment.class::isInstance).map(ImageFragment.class::cast)
                .findFirst().orElseThrow();

        assertColor(paint(panel), Math.round(fragment.x() + 10), Math.round(fragment.y() + 10),
                CssColor.parse("#4285f4"));
    }

    @Test
    public void flexShorthandControlsBasisShrinkAndGrowthInLayout() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div style="display:flex;width:300px">
                  <div id="fixed" style="flex:0 0 100px;height:20px"></div>
                  <div id="grown" style="flex:1 1 0%;height:20px"></div>
                </div></body>
                """));
        LayoutResult layout = panel.layoutForTesting(400);

        assertEquals(100f, boxById(layout, "fixed").width(), .01f);
        assertEquals(200f, boxById(layout, "grown").width(), .01f);
    }

    @Test
    public void laysOutTableCellsInSharedColumnsAndRows() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><table id="table" style="border-collapse:collapse">
                  <tbody><tr id="row-one">
                    <td id="a" style="width:100px;padding:2px">short</td>
                    <td id="b" style="width:60px;padding:2px">one<br>two</td>
                  </tr><tr id="row-two">
                    <td id="c">a much wider first-column value</td>
                    <td id="d">last</td>
                  </tr></tbody>
                </table></body>
                """));

        LayoutResult layout = panel.layoutForTesting(500);
        BoxFragment table = boxById(layout, "table");
        BoxFragment rowOne = boxById(layout, "row-one");
        BoxFragment rowTwo = boxById(layout, "row-two");
        BoxFragment a = boxById(layout, "a");
        BoxFragment b = boxById(layout, "b");
        BoxFragment c = boxById(layout, "c");
        BoxFragment d = boxById(layout, "d");

        assertEquals(a.y(), b.y(), 0.001f);
        assertEquals(c.y(), d.y(), 0.001f);
        assertEquals(a.x(), c.x(), 0.001f);
        assertEquals(b.x(), d.x(), 0.001f);
        assertEquals(a.x() + a.width(), b.x(), 0.001f);
        assertEquals(rowOne.y() + rowOne.height(), rowTwo.y(), 0.001f);
        assertEquals(table.width(), rowOne.width(), 0.001f);
        assertTrue(rowOne.height() > a.box().style().fontSizePx());
    }

    @Test
    public void floatsBlocksAndHonorsClear() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body>
                  <div id="float" style="float:right;width:100px;height:70px">float</div>
                  <div id="beside" style="height:20px">beside</div>
                  <div id="clear" style="clear:both;height:10px">clear</div>
                </body>
                """));

        LayoutResult layout = panel.layoutForTesting(400);
        BoxFragment floated = boxById(layout, "float");
        BoxFragment beside = boxById(layout, "beside");
        BoxFragment clear = boxById(layout, "clear");

        assertEquals(284f, floated.x(), 0.001f);
        assertEquals(268f, beside.width(), 0.001f);
        assertEquals(floated.y(), beside.y(), 0.001f);
        assertEquals(floated.y() + floated.height(), clear.y(), 0.001f);
        assertEquals(368f, clear.width(), 0.001f);
    }

    @Test
    public void appliesFontShorthandFamilyAndLineHeightToTextLayout() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div style="font:italic bold 20px/40px monospace">Text</div></body>
                """));

        LayoutResult layout = panel.layoutForTesting(300);
        TextFragment text = layout.fragments().stream()
                .filter(TextFragment.class::isInstance)
                .map(TextFragment.class::cast)
                .filter(fragment -> fragment.text().equals("Text"))
                .findFirst().orElseThrow();
        LineBox line = layout.lineBoxes().getFirst();

        assertEquals(20, text.font().getSize());
        assertTrue(text.font().isBold());
        assertTrue(text.font().isItalic());
        assertEquals(java.awt.Font.MONOSPACED, text.font().getFamily());
        assertEquals(40f, line.height(), 0.001f);
    }

    @Test
    public void reappliesHoverSelectorsWhenPointerTargetChanges() {
        Document document = parse("""
                <html><head><style>
                  #link:hover { display:block; color:red }
                </style></head><body><a id="link">Hover me</a></body></html>
                """);
        DomViewPanel panel = new DomViewPanel(document);
        panel.setSize(300, 100);
        TextFragment text = panel.layoutForTesting(300).fragments().stream()
                .filter(TextFragment.class::isInstance)
                .map(TextFragment.class::cast)
                .filter(fragment -> fragment.text().contains("Hover"))
                .findFirst().orElseThrow();

        assertEquals("inline", document.getElementById("link").getComputedStyles()
                .getOrDefault("display", "inline"));
        panel.dispatchEvent(new java.awt.event.MouseEvent(
                panel, java.awt.event.MouseEvent.MOUSE_MOVED, 0, 0,
                Math.round(text.x() + 1), Math.round(text.top() + 1), 0, false));

        assertTrue(document.getElementById("link").isHovered());
        assertEquals("block", document.getElementById("link").getComputedStyles().get("display"));
        assertEquals("red", document.getElementById("link").getComputedStyles().get("color"));
    }

    @Test
    public void paintsPositionedNonRepeatingCssBackgroundImages() throws Exception {
        URI uri = URI.create("https://example.test/background.png");
        Document document = parse("""
                <body><div id="box" style="width:20px;height:20px;background-color:blue;
                  background-image:url('https://example.test/background.png');
                  background-repeat:no-repeat;background-position:right bottom"></div></body>
                """);
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sourceGraphics = source.createGraphics();
        sourceGraphics.setColor(java.awt.Color.RED);
        sourceGraphics.fillRect(0, 0, 2, 2);
        sourceGraphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);
        ImageResourceRegistry images = new ImageResourceRegistry();
        images.register(uri, new BinaryResource(
                uri, 200, bytes.toByteArray(), NetworkResourceType.IMAGE));
        DomViewPanel panel = new DomViewPanel(document, PageRuntime.closed(), images);
        panel.setSize(100, 1);
        BoxFragment box = boxById(panel.layoutForTesting(100), "box");

        BufferedImage painted = paint(panel);

        assertColor(painted, Math.round(box.x()), Math.round(box.y()), CssColor.rgb(0x0000ff));
        assertColor(painted, Math.round(box.x() + box.width() - 1),
                Math.round(box.y() + box.height() - 1), CssColor.rgb(0xff0000));
    }

    @Test
    public void refusesOversizedCssBackgroundBeforePixelAllocation() throws Exception {
        URI uri = URI.create("https://example.test/oversized-background.png");
        Document document = parse("""
                <body><div id="box" style="width:20px;height:20px;background-color:blue;
                  background-image:url('https://example.test/oversized-background.png')"></div></body>
                """);
        BufferedImage oversized = new BufferedImage(9000, 1, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(oversized, "png", bytes);
        ImageResourceRegistry images = new ImageResourceRegistry();
        images.register(uri, new BinaryResource(
                uri, 200, bytes.toByteArray(), NetworkResourceType.IMAGE));
        DomViewPanel panel = new DomViewPanel(document, PageRuntime.closed(), images);
        panel.setSize(100, 1);

        BufferedImage painted = paint(panel);
        BoxFragment box = boxById(panel.layoutForTesting(100), "box");

        assertColor(painted, Math.round(box.x() + 5), Math.round(box.y() + 5),
                CssColor.rgb(0x0000ff));
    }

    @Test
    public void paintsRoundedBackgroundWithoutFillingItsCorner() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div id="box" style="width:40px;height:40px;background-color:red;
                  border-radius:12px;outline:2px solid blue"></div></body>
                """));
        panel.setSize(100, 1);
        BoxFragment box = boxById(panel.layoutForTesting(100), "box");

        BufferedImage painted = paint(panel);

        int corner = painted.getRGB(Math.round(box.x() + 1), Math.round(box.y() + 1));
        int center = painted.getRGB(Math.round(box.x() + 20), Math.round(box.y() + 20));
        assertFalse("Eine gerundete Ecke darf nicht mit der Hintergrundfarbe gefüllt sein",
                (corner & 0x00ffffff) == 0x00ff0000);
        assertEquals(0x00ff0000, center & 0x00ffffff);
    }

    @Test
    public void carriesUnderlineIntoPaintFragment() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><a style="color:red;text-decoration:underline blue">decorated</a></body>
                """));

        TextFragment text = panel.layoutForTesting(200).fragments().stream()
                .filter(TextFragment.class::isInstance)
                .map(TextFragment.class::cast)
                .filter(fragment -> fragment.text().contains("decorated"))
                .findFirst().orElseThrow();

        assertTrue(text.underline());
        assertEquals(CssColor.parse("blue"), text.decorationColor());
    }

    @Test
    public void paintsAbsolutelyPositionedBoxesByZIndex() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div style="position:relative;height:30px">
                  <div id="high" style="position:absolute;z-index:10;width:20px;height:20px"></div>
                  <div id="low" style="position:absolute;z-index:1;width:20px;height:20px"></div>
                </div></body>
                """));
        var fragments = panel.layoutForTesting(200).fragments();
        int lowIndex = fragments.indexOf(boxById(
                new LayoutResult(200, 0, fragments, List.of()), "low"));
        int highIndex = fragments.indexOf(boxById(
                new LayoutResult(200, 0, fragments, List.of()), "high"));

        assertTrue("Ein höherer z-index muss später und damit oben gezeichnet werden",
                highIndex > lowIndex);
    }

    @Test
    public void changesSwingCursorForCssCursorProperty() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div id="target" style="cursor:pointer;width:50px;height:20px">x</div></body>
                """));
        panel.setSize(200, 1);
        BoxFragment target = boxById(panel.layoutForTesting(200), "target");

        panel.dispatchEvent(new java.awt.event.MouseEvent(
                panel, java.awt.event.MouseEvent.MOUSE_MOVED, 0, 0,
                Math.round(target.x() + 1), Math.round(target.y() + 1), 0, false));

        assertEquals(java.awt.Cursor.HAND_CURSOR, panel.getCursor().getType());
    }

    @Test
    public void laysOutFlexRowsWithGrowthAndCrossAxisAlignment() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div id="flex" style="display:flex;width:300px;height:100px;
                  align-items:center">
                  <div id="one" style="width:50px;height:20px"></div>
                  <div id="grown" style="flex-grow:1;height:40px"></div>
                  <div id="three" style="width:50px;height:20px"></div>
                </div></body>
                """));

        LayoutResult layout = panel.layoutForTesting(400);
        BoxFragment flex = boxById(layout, "flex");
        BoxFragment one = boxById(layout, "one");
        BoxFragment grown = boxById(layout, "grown");
        BoxFragment three = boxById(layout, "three");

        assertEquals(300f, flex.width(), 0.001f);
        assertEquals(50f, one.width(), 0.001f);
        assertEquals(200f, grown.width(), 0.001f);
        assertEquals(50f, three.width(), 0.001f);
        assertEquals(one.x() + one.width(), grown.x(), 0.001f);
        assertEquals(grown.x() + grown.width(), three.x(), 0.001f);
        assertEquals(flex.y() + 40f, one.y(), 0.001f);
        assertEquals(flex.y() + 30f, grown.y(), 0.001f);
    }

    @Test
    public void honorsFlexDirectionJustificationAndInlineFlex() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body>
                  <div id="column" style="display:flex;flex-direction:column-reverse;
                    justify-content:space-between;align-items:center;width:200px;height:160px">
                    <div id="first" style="width:40px;height:20px"></div>
                    <div id="second" style="width:60px;height:30px"></div>
                  </div>
                  <p>before <span id="inline-flex" style="display:inline-flex;width:80px">
                    <i id="left" style="width:30px">L</i><i id="right" style="flex-grow:1">R</i>
                  </span> after</p>
                </body>
                """));

        LayoutResult layout = panel.layoutForTesting(400);
        BoxFragment column = boxById(layout, "column");
        BoxFragment first = boxById(layout, "first");
        BoxFragment second = boxById(layout, "second");
        BoxFragment inlineFlex = boxById(layout, "inline-flex");
        BoxFragment left = boxById(layout, "left");
        BoxFragment right = boxById(layout, "right");

        assertEquals(column.y() + column.height() - first.height(), first.y(), 0.001f);
        assertEquals(column.y(), second.y(), 0.001f);
        assertEquals(column.x() + (column.width() - first.width()) / 2f, first.x(), 0.001f);
        assertEquals(80f, inlineFlex.width(), 0.001f);
        assertEquals(30f, left.width(), 0.001f);
        assertEquals(50f, right.width(), 0.001f);
        assertEquals(left.x() + left.width(), right.x(), 0.001f);
    }

    @Test
    public void wrapsTextWithinShrunkFlexItems() {
        DomViewPanel panel = new DomViewPanel(parse("""
                <body><div style="display:flex;width:180px">
                  <div id="text" style="flex-grow:1">one two three four five six seven</div>
                  <div id="fixed" style="width:90px;height:20px"></div>
                </div></body>
                """));

        LayoutResult layout = panel.layoutForTesting(300);
        BoxFragment text = boxById(layout, "text");
        BoxFragment fixed = boxById(layout, "fixed");
        long textLines = layout.lineBoxes().stream()
                .filter(line -> line.fragments().stream()
                        .filter(TextFragment.class::isInstance)
                        .map(TextFragment.class::cast)
                        .anyMatch(fragment -> fragment.text().matches(".*(one|two|three|four|five|six|seven).*")))
                .count();

        assertEquals(90f, text.width(), 0.001f);
        assertEquals(text.x() + text.width(), fixed.x(), 0.001f);
        assertTrue("Text in einem geschrumpften Flex-Item muss umbrechen", textLines > 1);
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

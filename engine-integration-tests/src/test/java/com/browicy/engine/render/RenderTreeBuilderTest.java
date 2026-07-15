package com.browicy.engine.render;

import com.browicy.engine.dom.Document;
import com.browicy.engine.html.HtmlParser;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RenderTreeBuilderTest {

    @Test
    public void preservesBlockHierarchyAndNestedInlineStyles() {
        RenderTree tree = build("""
                <body><section><p style="color: blue; font-size: 20px">Before
                <span style="color: red; font-size: .5em">small <strong>bold</strong></span>
                after</p></section></body>
                """);

        RenderBox section = boxChildren(tree.root()).getFirst();
        RenderBox paragraph = boxChildren(section).getFirst();
        RenderInlineBox span = inlineChildren(paragraph).getFirst();
        RenderInlineBox strong = inlineChildren(span).getFirst();
        List<RenderTextRun> runs = textRunsRecursively(paragraph);

        assertEquals("section", section.tagName());
        assertEquals("p", paragraph.tagName());
        assertEquals("span", span.tagName());
        assertEquals("strong", strong.tagName());
        assertEquals(4, runs.size());
        assertEquals(20f, runs.get(0).style().fontSizePx(), 0.001f);
        assertEquals(CssColor.parse("blue"), runs.get(0).style().color());
        assertEquals(10f, runs.get(1).style().fontSizePx(), 0.001f);
        assertEquals(CssColor.parse("red"), runs.get(1).style().color());
        assertTrue(runs.get(2).style().bold());
        assertEquals(CssColor.parse("red"), runs.get(2).style().color());
        assertEquals(CssColor.parse("blue"), runs.get(3).style().color());
    }

    @Test
    public void resolvesBoxModelLengthsBackgroundAndBorder() {
        RenderBox box = boxChildren(build("""
                <body><div style="font-size: 20px; margin: 1px 2px 3px 4px;
                  padding: .5em; border: 2px solid #123456; background-color: yellow;
                  box-sizing: border-box">x</div></body>
                """).root()).getFirst();
        RenderStyle style = box.style();

        assertEquals(new BoxEdges(1, 2, 3, 4), style.margin());
        assertEquals(new BoxEdges(10, 10, 10, 10), style.padding());
        assertEquals(new BoxEdges(2, 2, 2, 2), style.borderWidth());
        assertEquals(CssColor.parse("yellow"), style.backgroundColor());
        assertEquals(CssColor.parse("#123456"), style.borderColor().top());
        assertTrue(style.borderStyle().left());
        assertEquals(RenderStyle.BoxSizing.BORDER_BOX, style.boxSizing());
    }

    @Test
    public void insertsBeforeAndAfterGeneratedContentInTreeOrder() {
        RenderTree tree = build("""
                <html><head><style>
                  .badge::before { content:"New "; color:red }
                  .badge::after { content:attr(data-suffix); color:blue }
                </style></head><body><span class="badge" data-suffix="!">Item</span></body></html>
                """);
        List<RenderTextRun> runs = textRunsRecursively(tree.root());

        assertEquals(List.of("New ", "Item", "!"), runs.stream().map(RenderTextRun::text).toList());
        assertEquals(CssColor.parse("red"), runs.getFirst().style().color());
        assertEquals(CssColor.parse("blue"), runs.getLast().style().color());
        assertNull(runs.getFirst().source());
        assertNull(runs.getLast().source());
    }

    @Test
    public void resolvesBorderRadiusAndOutline() {
        RenderBox box = boxChildren(build("""
                <body><div style="border-radius:8px;outline:blue 2px solid">x</div></body>
                """).root()).getFirst();

        assertEquals(8f, box.style().borderRadius(), 0.001f);
        assertEquals(2f, box.style().outlineWidth(), 0.001f);
        assertEquals(CssColor.parse("blue"), box.style().outlineColor());
        assertTrue(box.style().outlineVisible());
    }

    @Test
    public void inheritsListStyleAndCarriesTextDecoration() {
        RenderBox list = boxChildren(build("""
                <body><ul style="list-style:none"><li><a style="text-decoration:underline blue">x</a></li></ul></body>
                """).root()).getFirst();
        RenderBox item = boxChildren(list).getFirst();
        RenderTextRun text = textRunsRecursively(item).getFirst();

        assertEquals(RenderStyle.ListStyleType.NONE, item.style().listStyleType());
        assertTrue(text.style().underline());
        assertEquals(CssColor.parse("blue"), text.style().textDecorationColor());
    }

    @Test
    public void resolvesZIndexAndInheritedCursor() {
        RenderBox parent = boxChildren(build("""
                <body><div style="cursor:pointer"><span style="position:absolute;z-index:7">x</span></div></body>
                """).root()).getFirst();
        RenderBox child = boxChildren(parent).getFirst();

        assertEquals(7, child.style().zIndex());
        assertEquals(RenderStyle.Cursor.POINTER, child.style().cursor());
    }

    @Test
    public void excludesDisplayNoneSubtreesFromRenderTree() {
        RenderBox paragraph = boxChildren(build("""
                <body><p>visible <span style="display:none">hidden</span> text</p></body>
                """).root()).getFirst();

        String renderedText = textRunsRecursively(paragraph).stream()
                .map(RenderTextRun::text)
                .reduce("", String::concat);
        assertTrue(renderedText.contains("visible"));
        assertTrue(renderedText.contains("text"));
        assertFalse(renderedText.contains("hidden"));
    }


    @Test
    public void createsAnonymousBlocksAroundInlineRunsMixedWithBlocks() {
        RenderBox container = boxChildren(build("""
                <body><div>before<p>paragraph</p>after</div></body>
                """).root()).getFirst();

        assertEquals(3, container.children().size());
        RenderBox before = (RenderBox) container.children().get(0);
        RenderBox paragraph = (RenderBox) container.children().get(1);
        RenderBox after = (RenderBox) container.children().get(2);

        assertEquals("#anonymous", before.tagName());
        assertEquals("p", paragraph.tagName());
        assertEquals("#anonymous", after.tagName());
        assertEquals("before", textRunsRecursively(before).getFirst().text());
        assertEquals("after", textRunsRecursively(after).getFirst().text());
    }

    @Test
    public void resolvesDimensionsAutoMarginsAlignmentAndInlineBlockNodes() {
        RenderBox container = boxChildren(build("""
                <body><div style="text-align:right"><span style="display:inline-block;
                  width: 5em; height: 25%; margin-left:auto">content</span></div></body>
                """).root()).getFirst();

        assertEquals(RenderStyle.TextAlign.RIGHT, container.style().textAlign());
        RenderInlineBlock inlineBlock = (RenderInlineBlock) container.children().getFirst();
        RenderStyle style = inlineBlock.box().style();
        assertEquals(RenderStyle.Display.INLINE_BLOCK, style.display());
        assertEquals(new RenderLength(80, RenderLength.Unit.PX), style.width());
        assertEquals(new RenderLength(25, RenderLength.Unit.PERCENT), style.height());
        assertTrue(style.autoMargins().left());
        assertEquals(RenderStyle.TextAlign.RIGHT, style.textAlign());
    }

    @Test
    public void resolvesRemAgainstHtmlAndCarriesViewportDimensions() {
        Document document = new HtmlParser().parse("""
                <html style="font-size: 20px"><body>
                  <div style="font-size: 2rem; margin: 1rem; padding: 10vw;
                    width: 12rem; height: 25vh">content</div>
                </body></html>
                """);

        RenderTree tree = new RenderTreeBuilder().build(document, 500, 400);
        RenderStyle style = boxChildren(tree.root()).stream()
                .filter(box -> "div".equals(box.tagName()))
                .findFirst().orElseThrow().style();

        assertEquals(20f, tree.rootFontSizePx(), 0.001f);
        assertEquals(40f, style.fontSizePx(), 0.001f);
        assertEquals(new BoxEdges(20, 20, 20, 20), style.margin());
        assertEquals(new BoxEdges(50, 50, 50, 50), style.padding());
        assertEquals(new RenderLength(12, RenderLength.Unit.REM), style.width());
        assertEquals(new RenderLength(25, RenderLength.Unit.VH), style.height());
    }

    @Test
    public void rootRemFontSizeUsesTheInitialFontSizeAsItsBase() {
        RenderTree tree = build("""
                <html style="font-size: 2rem"><body><div style="font-size: 1rem">x</div>
                </body></html>
                """);

        assertEquals(32f, tree.rootFontSizePx(), 0.001f);
        RenderBox div = boxChildren(tree.root()).stream()
                .filter(box -> "div".equals(box.tagName()))
                .findFirst().orElseThrow();
        assertEquals(32f, div.style().fontSizePx(), 0.001f);
    }

    @Test
    public void resolvesPercentageFontSizesAndTransformsRenderedText() {
        RenderTree tree = build("""
                <body style="font-size:20px;text-transform:uppercase">
                  <p style="font-size:75%">Hello <span style="text-transform:capitalize">world wide</span></p>
                </body>
                """);
        List<RenderTextRun> runs = textRunsRecursively(tree.root());

        assertEquals(15f, runs.stream().filter(run -> run.text().contains("HELLO"))
                .findFirst().orElseThrow().style().fontSizePx(), 0.001f);
        assertTrue(runs.stream().anyMatch(run -> run.text().contains("HELLO")));
        assertTrue(runs.stream().anyMatch(run -> run.text().contains("World Wide")));
    }

    @Test
    public void createsSpecializedImageNodesWithHtmlFallbackDimensions() {
        Document document = new HtmlParser().parse("""
                <body><p>before<img id="hero" src="hero.png" width="120" height="45">after</p></body>
                """);
        byte[] bytes = {1, 2, 3};

        RenderTree tree = new RenderTreeBuilder(element -> bytes).build(document);
        RenderBox paragraph = boxChildren(tree.root()).getFirst();
        RenderImage image = paragraph.children().stream()
                .filter(RenderImage.class::isInstance)
                .map(RenderImage.class::cast)
                .findFirst().orElseThrow();

        assertEquals(document.getElementById("hero"), image.source());
        assertEquals(Integer.valueOf(120), image.htmlWidth());
        assertEquals(Integer.valueOf(45), image.htmlHeight());
        assertEquals(RenderStyle.Display.INLINE, image.style().display());
        assertEquals(3, image.data().length);
    }

    @Test
    public void treatsLegacyCenterContainersAsBlocksSoNestedImagesReachLayout() {
        RenderTree tree = build("""
                <body><center><br><div><img id="logo" width="272" height="92"></div></center></body>
        """);

        RenderBox center = boxChildren(tree.root()).getFirst();
        RenderBox container = boxChildren(center).stream()
                .filter(box -> "div".equals(box.tagName()))
                .findFirst().orElseThrow();

        assertEquals("center", center.tagName());
        assertEquals("div", container.tagName());
        assertTrue(container.children().stream().anyMatch(RenderImage.class::isInstance));
    }

    @Test
    public void createsScalableRenderImageForInlineSvgPaths() {
        RenderTree tree = build("""
                <body style="color:#4285f4"><svg width="24" height="24" viewBox="0 0 48 48"
                  style="fill:currentColor;opacity:.75">
                  <path d="M4 4h40v40H4z"></path>
                </svg></body>
                """);
        RenderImage image = imagesRecursively(tree.root()).getFirst();

        assertEquals(Integer.valueOf(24), image.htmlWidth());
        assertEquals(48f, image.svg().width(), .001f);
        assertTrue(image.svg().source().contains("viewBox=\"0 0 48 48\""));
        assertTrue(image.svg().source().contains("<path d=\"M4 4h40v40H4z\""));
        assertTrue(image.svg().source().contains("color:rgba(66,133,244,1.0)"));
        assertTrue(image.svg().source().contains("fill:currentcolor"));
        assertEquals(.75f, image.style().opacity(), .001f);
    }

    private static RenderTree build(String html) {
        Document document = new HtmlParser().parse(html);
        return new RenderTreeBuilder().build(document);
    }

    private static List<RenderBox> boxChildren(RenderBox box) {
        return box.children().stream()
                .filter(RenderBox.class::isInstance)
                .map(RenderBox.class::cast)
                .toList();
    }

    private static List<RenderInlineBox> inlineChildren(RenderNode node) {
        List<RenderNode> children;
        if (node instanceof RenderBox box) {
            children = box.children();
        } else if (node instanceof RenderInlineBox inlineBox) {
            children = inlineBox.children();
        } else if (node instanceof RenderInlineBlock inlineBlock) {
            children = inlineBlock.box().children();
        } else {
            return List.of();
        }
        return children.stream()
                .filter(RenderInlineBox.class::isInstance)
                .map(RenderInlineBox.class::cast)
                .toList();
    }

    private static List<RenderTextRun> textRunsRecursively(RenderNode node) {
        if (node instanceof RenderTextRun run) {
            return List.of(run);
        }
        List<RenderNode> children;
        if (node instanceof RenderBox box) {
            children = box.children();
        } else if (node instanceof RenderInlineBox inlineBox) {
            children = inlineBox.children();
        } else {
            return List.of();
        }
        return children.stream()
                .flatMap(child -> textRunsRecursively(child).stream())
                .toList();
    }

    private static List<RenderImage> imagesRecursively(RenderNode node) {
        if (node instanceof RenderImage image) return List.of(image);
        List<RenderNode> children;
        if (node instanceof RenderBox box) children = box.children();
        else if (node instanceof RenderInlineBox inline) children = inline.children();
        else if (node instanceof RenderInlineBlock block) children = block.box().children();
        else return List.of();
        return children.stream().flatMap(child -> imagesRecursively(child).stream()).toList();
    }
}

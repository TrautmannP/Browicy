package com.browicy.engine.render;

import com.browicy.engine.dom.Document;
import com.browicy.engine.html.HtmlParser;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        List<RenderTextRun> runs = textRuns(paragraph);

        assertEquals("section", section.tagName());
        assertEquals("p", paragraph.tagName());
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
                  padding: .5em; border: 2px solid #123456; background-color: yellow">x</div></body>
                """).root()).getFirst();
        RenderStyle style = box.style();

        assertEquals(new BoxEdges(1, 2, 3, 4), style.margin());
        assertEquals(new BoxEdges(10, 10, 10, 10), style.padding());
        assertEquals(new BoxEdges(2, 2, 2, 2), style.borderWidth());
        assertEquals(CssColor.parse("yellow"), style.backgroundColor());
        assertEquals(CssColor.parse("#123456"), style.borderColor().top());
        assertTrue(style.borderStyle().left());
    }

    @Test
    public void excludesDisplayNoneSubtreesFromRenderTree() {
        RenderBox paragraph = boxChildren(build("""
                <body><p>visible <span style="display:none">hidden</span> text</p></body>
                """).root()).getFirst();

        String renderedText = textRuns(paragraph).stream()
                .map(RenderTextRun::text)
                .reduce("", String::concat);
        assertTrue(renderedText.contains("visible"));
        assertTrue(renderedText.contains("text"));
        assertFalse(renderedText.contains("hidden"));
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

    private static List<RenderTextRun> textRuns(RenderBox box) {
        return box.children().stream()
                .filter(RenderTextRun.class::isInstance)
                .map(RenderTextRun.class::cast)
                .toList();
    }
}

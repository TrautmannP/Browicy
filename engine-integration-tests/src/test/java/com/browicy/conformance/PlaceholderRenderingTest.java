package com.browicy.conformance;

import com.browicy.engine.css.StyleApplicator;
import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.html.HtmlParser;
import com.browicy.engine.render.RenderBox;
import com.browicy.engine.render.RenderInlineBlock;
import com.browicy.engine.render.RenderInlineBox;
import com.browicy.engine.render.RenderNode;
import com.browicy.engine.render.RenderTextRun;
import com.browicy.engine.render.RenderTree;
import com.browicy.engine.render.RenderTreeBuilder;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Placeholder- und value-Rendering von Inputs plus Vendor-Pseudo-Selektor. */
public final class PlaceholderRenderingTest {

    private static List<String> textRuns(RenderNode node, List<String> out) {
        if (node instanceof RenderTextRun run) {
            out.add(run.text());
        }
        if (node instanceof RenderBox box) {
            for (RenderNode child : box.children()) {
                textRuns(child, out);
            }
        }
        if (node instanceof RenderInlineBox inline) {
            for (RenderNode child : inline.children()) {
                textRuns(child, out);
            }
        }
        if (node instanceof RenderInlineBlock block) {
            textRuns(block.box(), out);
        }
        return out;
    }

    private static void dump(RenderNode node, int depth) {
        System.out.println("  ".repeat(depth) + node.getClass().getSimpleName()
                + (node instanceof RenderTextRun run ? " '" + run.text() + "'" : ""));
        if (node instanceof RenderBox box) {
            for (RenderNode child : box.children()) {
                dump(child, depth + 1);
            }
        }
        if (node instanceof RenderInlineBox inline) {
            for (RenderNode child : inline.children()) {
                dump(child, depth + 1);
            }
        }
        if (node instanceof RenderInlineBlock block) {
            dump(block.box(), depth + 1);
        }
    }

    @Test
    public void inputsRenderPlaceholderOrValueAsText() {
        Document document = new HtmlParser().parse("""
                <html><body>
                  <div>
                    <input id="search" placeholder="Suche nach Ort oder PLZ">
                    <input id="filled" value="Berlin">
                    <input id="empty">
                  </div>
                </body></html>
                """);
        new StyleApplicator().apply(document, 1280, 900);
        RenderTree tree = new RenderTreeBuilder().build(document);
        List<String> runs = new ArrayList<>();
        textRuns(tree.root(), runs);
        runs.removeIf(String::isBlank);
        assertEquals(List.of("Suche nach Ort oder PLZ", "Berlin"), runs);
    }

    @Test
    public void webkitInputPlaceholderSelectorStylingPlaceholderText() {
        Document document = new HtmlParser().parse("""
                <html><head><style>
                  input::-webkit-input-placeholder { color: #123456; }
                  input::-moz-placeholder { font-weight: 700; }
                </style></head><body>
                  <div><input id="search" placeholder="Suche"></div>
                </body></html>
                """);
        new StyleApplicator().apply(document, 1280, 900);
        Element search = document.getElementById("search");
        assertEquals("#123456", search.getPseudoComputedStyles("placeholder").get("color"));
        assertEquals("700", search.getPseudoComputedStyles("placeholder").get("font-weight"));
        RenderTree tree = new RenderTreeBuilder().build(document);
        List<String> runs = new ArrayList<>();
        textRuns(tree.root(), runs);
        assertTrue(runs.contains("Suche"));
    }
}

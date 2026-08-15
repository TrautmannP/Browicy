package com.browicy.engine.render;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.DocumentType;
import com.browicy.engine.dom.Element;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Presentational Hints (CSS2.1 §6.4.4): die HTML-Attribute {@code width}/
 * {@code height} auf {@code table} werden als Style-Dimensionen übernommen
 * (Priorität: UA-Defaults &lt; Attribut &lt; Autor-CSS).
 */
public class TablePreshintTest {

    @Test
    public void widthAttributeBecomesPixelLength() {
        RenderBox tableBox = renderTable("width", "300");
        assertEquals(RenderLength.Unit.PX, tableBox.style().width().unit());
        assertEquals(300f, tableBox.style().width().value(), 0.001);
    }

    @Test
    public void percentWidthAttributeKeepsPercent() {
        RenderBox tableBox = renderTable("width", "50%");
        assertEquals(RenderLength.Unit.PERCENT, tableBox.style().width().unit());
        assertEquals(50f, tableBox.style().width().value(), 0.001);
    }

    @Test
    public void heightAttributeBecomesPixelLength() {
        RenderBox tableBox = renderTable("height", "20");
        assertEquals(RenderLength.Unit.PX, tableBox.style().height().unit());
        assertEquals(20f, tableBox.style().height().value(), 0.001);
    }

    @Test
    public void authorCssWinsOverWidthAttribute() {
        Document document = documentWithBody();
        Element table = document.createElement("table");
        table.setAttribute("width", "300");
        table.setComputedStyle("width", "500px");
        document.getBody().appendChild(table);
        RenderBox tableBox = findBox(new RenderTreeBuilder().build(document).root(), table);
        assertEquals(500f, tableBox.style().width().value(), 0.001);
        assertEquals(RenderLength.Unit.PX, tableBox.style().width().unit());
    }

    @Test
    public void nonTableElementIgnoresWidthAttribute() {
        Document document = documentWithBody();
        Element div = document.createElement("div");
        div.setAttribute("width", "300");
        document.getBody().appendChild(div);
        RenderBox divBox = findBox(new RenderTreeBuilder().build(document).root(), div);
        assertTrue(divBox.style().width().isAuto());
    }

    @Test
    public void invalidDimensionValuesAreIgnored() {
        Document document = documentWithBody();
        Element table = document.createElement("table");
        table.setAttribute("width", "abc");
        table.setAttribute("height", "-5");
        document.getBody().appendChild(table);
        RenderBox tableBox = findBox(new RenderTreeBuilder().build(document).root(), table);
        assertTrue(tableBox.style().width().isAuto());
        assertTrue(tableBox.style().height().isAuto());
    }

    private static RenderBox renderTable(String attribute, String value) {
        Document document = documentWithBody();
        Element table = document.createElement("table");
        table.setAttribute(attribute, value);
        document.getBody().appendChild(table);
        return findBox(new RenderTreeBuilder().build(document).root(), table);
    }

    private static Document documentWithBody() {
        Document document = new Document("about:blank");
        document.appendChild(new DocumentType("html", "", ""));
        Element html = document.createElement("html");
        html.appendChild(document.createElement("head"));
        html.appendChild(document.createElement("body"));
        document.appendChild(html);
        return document;
    }

    private static RenderBox findBox(RenderNode node, Element source) {
        if (node instanceof RenderBox box) {
            if (box.source() == source) {
                return box;
            }
            for (RenderNode child : box.children()) {
                RenderBox match = findBox(child, source);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }
}

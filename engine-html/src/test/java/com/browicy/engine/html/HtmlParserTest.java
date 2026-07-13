package com.browicy.engine.html;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.TextNode;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HtmlParserTest {

    private final HtmlParser parser = new HtmlParser();

    @Test
    public void parsesHelloWorldDocument() {
        Document document = parser.parse("""
                <!DOCTYPE html>
                <html>
                  <head><title>Hallo Welt</title></head>
                  <body>
                    <h1>Hallo Welt!</h1>
                    <p>Erster Absatz.</p>
                  </body>
                </html>
                """);

        assertEquals("Hallo Welt", document.getTitle());

        Element body = document.getBody();
        assertNotNull(body);
        List<Element> blocks = body.getChildElements();
        assertEquals(2, blocks.size());
        assertEquals("h1", blocks.get(0).getTagName());
        assertEquals("Hallo Welt!", blocks.get(0).getTextContent());
        assertEquals("p", blocks.get(1).getTagName());
        assertEquals("Erster Absatz.", blocks.get(1).getTextContent());
    }

    @Test
    public void parsesAttributes() {
        Document document = parser.parse(
                "<body><a href=\"https://example.org\" target=_blank disabled>Link</a></body>");

        Element link = document.getBody().getChildElements().get(0);
        assertEquals("a", link.getTagName());
        assertEquals("https://example.org", link.getAttribute("href"));
        assertEquals("_blank", link.getAttribute("target"));
        assertTrue(link.hasAttribute("disabled"));
    }

    @Test
    public void handlesVoidElementsWithoutEndTag() {
        Document document = parser.parse("<body><p>Zeile 1<br>Zeile 2</p><hr></body>");

        List<Element> blocks = document.getBody().getChildElements();
        assertEquals(2, blocks.size());
        Element paragraph = blocks.get(0);
        assertEquals("p", paragraph.getTagName());
        // br darf p nicht "verschlucken": beide Textteile sind Kinder von p
        assertEquals("Zeile 1Zeile 2", paragraph.getTextContent());
        assertEquals("hr", blocks.get(1).getTagName());
    }

    @Test
    public void decodesEntities() {
        Document document = parser.parse("<body><p>1 &lt; 2 &amp;&amp; 3 &gt; 2 &#8212; ok</p></body>");

        assertEquals("1 < 2 && 3 > 2 — ok", document.getBody().getTextContent());
    }

    @Test
    public void decodesTypographicEntities() {
        Document document = parser.parse(
                "<body><p>Hallo &ndash; Welt &rarr; sch&ouml;n &amp; gr&uuml;n</p></body>");

        assertEquals("Hallo – Welt → schön & grün", document.getBody().getTextContent());
    }

    @Test
    public void ignoresCommentsAndUnmatchedEndTags() {
        Document document = parser.parse("<body><!-- Kommentar --><p>Text</p></span></body>");

        List<Element> blocks = document.getBody().getChildElements();
        assertEquals(1, blocks.size());
        assertEquals("Text", blocks.get(0).getTextContent());
    }

    @Test
    public void treatsScriptContentAsRawText() {
        Document document = parser.parse(
                "<body><script>if (a < b) { render('<p>nope</p>'); }</script><p>Sichtbar</p></body>");

        List<Element> blocks = document.getBody().getChildElements();
        assertEquals(2, blocks.size());
        Element script = blocks.get(0);
        assertEquals("script", script.getTagName());
        assertTrue(script.getChildren().get(0) instanceof TextNode);
        assertEquals("if (a < b) { render('<p>nope</p>'); }", script.getTextContent());
        assertEquals("Sichtbar", blocks.get(1).getTextContent());
    }

    @Test
    public void bodyFallsBackToRootForFragments() {
        Document document = parser.parse("<div><p>Fragment</p></div>");

        assertNotNull(document.getBody());
        assertEquals("Fragment", document.getBody().getTextContent().strip());
    }

    @Test
    public void insertsImplicitTbodyForRowsDirectlyInsideTable() {
        Document document = parser.parse("<table><tr><td>Eins</td></tr></table>");

        Element table = document.getDocumentElement();
        assertEquals("table", table.getTagName());
        Element tbody = table.getChildElements().getFirst();
        assertEquals("tbody", tbody.getTagName());
        Element row = tbody.getChildElements().getFirst();
        assertEquals("tr", row.getTagName());
        assertEquals("Eins", row.getTextContent());
    }

    @Test
    public void insertsMissingTableSectionAndRowForCells() {
        Document document = parser.parse("<table><td>Eins<td>Zwei</table>");

        Element table = document.getDocumentElement();
        Element tbody = table.getChildElements().getFirst();
        Element row = tbody.getChildElements().getFirst();
        List<Element> cells = row.getChildElements();

        assertEquals("tbody", tbody.getTagName());
        assertEquals("tr", row.getTagName());
        assertEquals(2, cells.size());
        assertEquals("td", cells.get(0).getTagName());
        assertEquals("Eins", cells.get(0).getTextContent());
        assertEquals("Zwei", cells.get(1).getTextContent());
    }

    @Test
    public void autoClosesRowsAndCellsWithOmittedEndTags() {
        Document document = parser.parse(
                "<table><tr><td>A<td>B<tr><th>C<th>D</table>");

        Element tbody = document.getDocumentElement().getChildElements().getFirst();
        List<Element> rows = tbody.getChildElements();

        assertEquals(2, rows.size());
        assertEquals(2, rows.get(0).getChildElements().size());
        assertEquals("A", rows.get(0).getChildElements().get(0).getTextContent());
        assertEquals("B", rows.get(0).getChildElements().get(1).getTextContent());
        assertEquals(2, rows.get(1).getChildElements().size());
        assertEquals("C", rows.get(1).getChildElements().get(0).getTextContent());
        assertEquals("D", rows.get(1).getChildElements().get(1).getTextContent());
    }

    @Test
    public void autoClosesParagraphForParagraphAndBlockStarts() {
        Document document = parser.parse(
                "<body><p>Eins<p>Zwei<div>Drei</div>Vier</body>");

        Element body = document.getBody();
        List<Element> blocks = body.getChildElements();

        assertEquals(3, blocks.size());
        assertEquals("p", blocks.get(0).getTagName());
        assertEquals("Eins", blocks.get(0).getTextContent());
        assertEquals("p", blocks.get(1).getTagName());
        assertEquals("Zwei", blocks.get(1).getTextContent());
        assertEquals("div", blocks.get(2).getTagName());
        assertEquals("Drei", blocks.get(2).getTextContent());
        assertEquals("EinsZweiDreiVier", body.getTextContent());
    }

    @Test
    public void autoClosesListItemsWhenTheNextItemStarts() {
        Document document = parser.parse("<ul><li>Eins<li>Zwei<li>Drei</ul>");

        List<Element> items = document.getDocumentElement().getChildElements();
        assertEquals(3, items.size());
        assertEquals("Eins", items.get(0).getTextContent());
        assertEquals("Zwei", items.get(1).getTextContent());
        assertEquals("Drei", items.get(2).getTextContent());
    }

    @Test
    public void listItemScopePreservesOuterItemAcrossNestedLists() {
        Document document = parser.parse(
                "<ul><li>Außen<ul><li>Innen</li></ul><li>Danach</ul>");

        Element outerList = document.getDocumentElement();
        List<Element> outerItems = outerList.getChildElements();

        assertEquals(2, outerItems.size());
        assertEquals("li", outerItems.get(0).getTagName());
        assertEquals("AußenInnen", outerItems.get(0).getTextContent());
        assertEquals(1, outerItems.get(0).getElementsByTagName("ul").size());
        assertEquals("Danach", outerItems.get(1).getTextContent());
    }

    @Test
    public void autoClosesDescriptionListItems() {
        Document document = parser.parse("<dl><dt>Begriff<dd>Erklärung<dt>Nächster</dl>");

        List<Element> entries = document.getDocumentElement().getChildElements();
        assertEquals(3, entries.size());
        assertEquals("dt", entries.get(0).getTagName());
        assertEquals("dd", entries.get(1).getTagName());
        assertEquals("dt", entries.get(2).getTagName());
    }

}

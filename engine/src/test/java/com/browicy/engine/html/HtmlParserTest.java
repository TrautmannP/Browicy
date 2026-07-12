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
}

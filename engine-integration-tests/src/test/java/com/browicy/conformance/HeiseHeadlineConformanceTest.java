package com.browicy.conformance;

import com.browicy.conformance.model.ElementLayoutBox;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class HeiseHeadlineConformanceTest extends LayoutConformanceTestBase {
    @Test
    public void headlineDoesNotCollapseIntoOneCharacterLines() {
        String html = """
                <!doctype html>
                <html><head><style>
                  html, body { margin: 0; padding: 0; }
                  .grid { width: 1056px; display: grid; gap: 24px;
                          grid-auto-columns: minmax(0, 1fr); grid-auto-flow: column; }
                  article { min-width: 0; }
                  h3 { display: flex; flex-direction: column; min-width: 0; margin: 0; font-size: 16px; }
                  h3 > span { display: block; max-width: 65ch; font-size: 20px;
                               font-weight: 600; line-height: 27.5px; }
                </style></head><body><section class="grid">
                  <article><h3><span id="headline">Jetzt muss das Bundeskartellamt endlich ran!</span></h3></article>
                  <article><h3><span>Jetzt muss das Bundeskartellamt endlich ran!</span></h3></article>
                  <article><h3><span>Jetzt muss das Bundeskartellamt endlich ran!</span></h3></article>
                </section></body></html>
                """;
        assertConforms("heise-headline", html, 1280, 900, 2.0f);
    }
    @Test
    public void lowerPasskeyHeadlineKeepsFlexTextWidth() {
        String html = """
                <!doctype html>
                <html><head><style>
                  html, body { margin: 0; padding: 0; }
                  #teaser { width: 768px; display: flex; }
                  figure { width: 40%; height: 171px; margin: 0 16px 0 0; }
                  #copy { width: 60%; }
                  h3 { display: flex; flex-direction: column; min-width: 0;
                       margin: 0; font-size: 16px; }
                  h3 > span { display: block; max-width: 65ch; font-size: 20px;
                               font-weight: 600; line-height: 27.5px; }
                </style></head><body><a id="teaser">
                  <figure><div style="width:100%;height:100%"></div></figure>
                  <div id="copy"><header><h3><span id="lower-headline">
                    Studie: Umgang mit Passkeys für Menschen teilweise überraschend schwierig
                  </span></h3></header></div>
                </a></body></html>
                """;
        assertConforms("heise-lower-passkey-headline", html, 1280, 900, 2.0f);
    }
    @Test
    public void lowerPasskeyHeadlineKeepsWidthWithCustomImage() {
        String html = """
                <!doctype html>
                <html><head><style>
                  html, body { margin: 0; padding: 0; }
                  #teaser { width: 768px; display: flex; }
                  figure { width: 40%; height: 171px; margin: 0 16px 0 0; }
                  #copy { width: 60%; }
                  h3 { display: flex; flex-direction: column; min-width: 0;
                       margin: 0; font-size: 16px; }
                  h3 > span { display: block; max-width: 65ch; font-size: 20px;
                               font-weight: 600; line-height: 27.5px; }
                </style></head><body><a id="teaser">
                  <figure><a-img><img width="6000" height="3371"
                    style="width:100%;height:100%;object-fit:cover"></a-img></figure>
                  <div id="copy"><header><h3><span id="lower-headline">
                    Studie: Umgang mit Passkeys für Menschen teilweise überraschend schwierig
                  </span></h3></header></div>
                </a></body></html>
                """;
        Map<String, ElementLayoutBox> expected = chrome.extract(html, 1280, 900);
        Map<String, ElementLayoutBox> actual = browicy.extract(html, 1280, 900);
        ElementLayoutBox expectedHeadline = expected.get("#lower-headline");
        ElementLayoutBox actualHeadline = actual.get("#lower-headline");
        assertNotNull("Chrome muss die Passkey-Überschrift finden", expectedHeadline);
        assertNotNull("Browicy muss die Passkey-Überschrift finden", actualHeadline);
        assertEquals(expectedHeadline.x(), actualHeadline.x(), 2.0f);
        assertEquals(expectedHeadline.y(), actualHeadline.y(), 2.0f);
        assertEquals(expectedHeadline.width(), actualHeadline.width(), 2.0f);
        assertEquals(expectedHeadline.height(), actualHeadline.height(), 2.0f);
        assertTrue("Die Überschrift darf nicht auf einzelne Buchstaben umbrechen",
                actualHeadline.width() > 200 && actualHeadline.height() < 100);
    }


}

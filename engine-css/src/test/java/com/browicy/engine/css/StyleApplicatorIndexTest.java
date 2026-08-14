package com.browicy.engine.css;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StyleApplicatorIndexTest {

    private static final int RULE_COUNT = 10_000;
    private static final int ELEMENT_COUNT = 100;
    private static final String[] TAGS = {"div", "span", "p"};

    @Test
    public void indexedMatchingProducesIdenticalCascadeResults() {
        List<CssRule> rules = new CssParser().parse(cssWith(RULE_COUNT));
        Document linearDocument = documentWith(ELEMENT_COUNT);
        Document indexedDocument = documentWith(ELEMENT_COUNT);

        StyleApplicator applicator = new StyleApplicator();
        applicator.applyRulesLinear(linearDocument, rules, 800, 600);
        applicator.applyRules(indexedDocument, rules, 800, 600);

        List<Element> linearElements = linearDocument.getElementsByTagName("*");
        List<Element> indexedElements = indexedDocument.getElementsByTagName("*");
        assertEquals(linearElements.size(), indexedElements.size());
        for (int index = 0; index < linearElements.size(); index++) {
            Map<String, String> linearStyles =
                    linearElements.get(index).getComputedStyles();
            Map<String, String> indexedStyles =
                    indexedElements.get(index).getComputedStyles();
            assertEquals("Computed Styles für Element " + index + " ("
                            + linearElements.get(index).getTagName() + ")",
                    linearStyles, indexedStyles);
        }
    }

    @Test
    public void specificityAndSourceOrderStillDecideTheCascade() {
        List<CssRule> rules = new CssParser().parse("""
                #el7 { color: rgb(7, 0, 49); }
                .c7 { color: rgb(0, 7, 128); }
                div { color: rgb(200, 200, 200); }
                .c7 { background-color: rgb(1, 2, 3); }
                """);
        Document document = documentWith(ELEMENT_COUNT);
        new StyleApplicator().applyRules(document, rules, 800, 600);

        Element seven = document.getElementById("el7");
        assertEquals("rgb(7, 0, 49)", seven.getComputedStyles().get("color"));
        assertEquals("rgb(1, 2, 3)", seven.getComputedStyles().get("background-color"));
    }

    @Test
    public void indexedApplicationIsFasterThanTheLinearScan() {
        List<CssRule> rules = new CssParser().parse(cssWith(RULE_COUNT));
        StyleApplicator applicator = new StyleApplicator();

        applicator.applyRules(documentWith(ELEMENT_COUNT), rules, 800, 600);

        Document timed = documentWith(ELEMENT_COUNT);
        long indexedStart = System.nanoTime();
        applicator.applyRules(timed, rules, 800, 600);
        long indexedNanos = System.nanoTime() - indexedStart;

        Document linear = documentWith(ELEMENT_COUNT);
        long linearStart = System.nanoTime();
        applicator.applyRulesLinear(linear, rules, 800, 600);
        long linearNanos = System.nanoTime() - linearStart;

        assertTrue("Indexierter Durchlauf (" + indexedNanos + " ns) muss schneller "
                        + "sein als linear (" + linearNanos + " ns)",
                indexedNanos < linearNanos);
        assertTrue("Indexierter Durchlauf über " + RULE_COUNT + " Regeln und "
                        + ELEMENT_COUNT + " Elementen dauert zu lange: "
                        + indexedNanos + " ns",
                indexedNanos < 1_000_000_000L);
    }

    private static String cssWith(int ruleCount) {
        StringBuilder css = new StringBuilder(ruleCount * 40);
        int written = 0;
        for (int index = 0; index < 5_000 && written < ruleCount; index++, written++) {
            css.append("#el").append(index)
                    .append("{color:rgb(").append(index % 255)
                    .append(",0,").append((index * 7) % 255).append(")}\n");
        }
        for (int index = 0; index < 3_000 && written < ruleCount; index++, written++) {
            css.append(".c").append(index % 100)
                    .append("{color:rgb(0,").append(index % 255).append(",128)}\n");
        }
        for (int index = 0; index < 2_000 && written < ruleCount; index++, written++) {
            String tag = TAGS[index % TAGS.length];
            css.append(tag).append("{border-width:").append(index % 9)
                    .append("px;margin-top:").append(index % 25).append("px}\n");
        }
        return css.toString();
    }

    private static Document documentWith(int elementCount) {
        Document document = new Document("about:index-test");
        Element html = document.createElement("html");
        Element body = document.createElement("body");
        document.appendChild(html);
        html.appendChild(body);
        for (int index = 0; index < elementCount; index++) {
            Element element = document.createElement(TAGS[index % TAGS.length]);
            element.setAttribute("id", "el" + index);
            element.setAttribute("class", "c" + index
                    + " c" + (index + 33) % 100
                    + " c" + (index + 67) % 100);
            body.appendChild(element);
        }
        return document;
    }
}

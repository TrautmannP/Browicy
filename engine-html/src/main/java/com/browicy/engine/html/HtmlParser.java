package com.browicy.engine.html;

import com.browicy.engine.css.StyleApplicator;
import com.browicy.engine.dom.Document;

import java.util.List;

/**
 * Zerlegt HTML in Tokens und baut daraus einen fehlertoleranten DOM-Baum.
 *
 * <p>Die eigentliche Tree-Construction ist in {@link HtmlTreeBuilder}
 * gekapselt. Dort werden unter anderem optionale End-Tags geschlossen und
 * strukturell notwendige Tabellenelemente implizit erzeugt. Die Regeln sind
 * separat in {@link HtmlTreeConstructionRules} beschrieben, damit weitere
 * HTML5-Regeln ohne wachsende Fallunterscheidungen im Parser ergänzt werden
 * können.</p>
 */
public final class HtmlParser {

    public Document parse(String html) {
        return parse(html, "about:blank");
    }

    public Document parse(String html, String url) {
        List<HtmlToken> tokens = new HtmlTokenizer(html).tokenize();
        Document document = new HtmlTreeBuilder(new Document(url)).build(tokens);
        new StyleApplicator().apply(document);
        return document;
    }
}

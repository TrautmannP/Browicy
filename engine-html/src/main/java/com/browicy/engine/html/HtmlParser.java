package com.browicy.engine.html;

import com.browicy.engine.css.StyleApplicator;
import com.browicy.engine.dom.Document;

import java.util.List;

public final class HtmlParser {

    public Document parse(String html) {
        return parse(html, "about:blank");
    }

    public Document parse(String html, String url) {
        List<HtmlToken> tokens = new HtmlTokenizer(html).tokenize();
        Document document = new HtmlTreeBuilder(new Document(url)).build(tokens);
        DocumentBaseUriResolver.apply(document);
        new StyleApplicator().apply(document);
        return document;
    }
}

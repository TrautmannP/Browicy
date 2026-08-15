package com.browicy.engine.html;

import com.browicy.engine.css.StyleApplicator;
import com.browicy.engine.dom.Document;

public final class HtmlParser {

    public Document parse(String html) {
        return parse(html, "about:blank");
    }

    public Document parse(String html, String url) {
        Document document = new Document(url);
        HtmlTreeBuilder builder = new HtmlTreeBuilder(document);
        new HtmlTokenizer(html).tokenize(builder::accept);
        DocumentBaseUriResolver.apply(document);
        new StyleApplicator().apply(document);
        return document;
    }
}

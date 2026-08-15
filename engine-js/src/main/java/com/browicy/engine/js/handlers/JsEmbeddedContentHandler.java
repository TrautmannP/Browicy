package com.browicy.engine.js.handlers;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.DocumentFragment;
import com.browicy.engine.dom.DocumentReadyState;
import com.browicy.engine.dom.Node;
import com.browicy.engine.html.HtmlParser;
import com.browicy.engine.js.JsDocument;
import com.browicy.engine.js.JsElement;
import org.graalvm.polyglot.Value;

import java.util.List;

import static com.browicy.engine.js.handlers.JsMemberHandler.orEmpty;
import static com.browicy.engine.js.handlers.JsMemberHandler.tag;
import static com.browicy.engine.js.handlers.JsMemberHandler.toText;

public final class JsEmbeddedContentHandler implements JsMemberHandler {

    private static final List<String> KEYS = List.of(
            "srcdoc", "contentDocument", "contentWindow", "content");

    @Override
    public List<String> keys() {
        return KEYS;
    }

    @Override
    public boolean canHandle(String key) {
        return KEYS.contains(key);
    }

    @Override
    public Object get(String key, JsElement element, JsDocument doc) {
        return switch (key) {
            case "srcdoc" -> orEmpty(element.unwrap().getAttribute("srcdoc"));
            case "contentDocument" -> embeddedDocument(element, doc);
            case "contentWindow" -> {
                JsDocument content = embeddedDocument(element, doc);
                yield content == null ? null : content.defaultView();
            }
            case "content" -> templateContent(element, doc);
            default -> null;
        };
    }

    @Override
    public boolean set(String key, Value value, JsElement element, JsDocument doc) {
        if (!"srcdoc".equals(key)) {
            return false;
        }
        element.unwrap().setAttribute("srcdoc", toText(value));
        return true;
    }

    private static Object templateContent(JsElement element, JsDocument doc) {
        if (!"template".equals(tag(element.unwrap()))) {
            return null;
        }
        if (element.contentFragment() == null) {
            DocumentFragment fragment = element.unwrap().getOwnerDocument().createDocumentFragment();
            for (Node child : List.copyOf(element.unwrap().getChildren())) {
                fragment.appendChild(child);
            }
            element.setContentFragment(fragment);
        }
        return doc.wrap(element.contentFragment());
    }

    private static JsDocument embeddedDocument(JsElement element, JsDocument doc) {
        if (!"iframe".equals(tag(element.unwrap()))) {
            return null;
        }
        String srcdoc = element.unwrap().getAttribute("srcdoc");
        String source = srcdoc == null
                ? "src:" + orEmpty(element.unwrap().getAttribute("src")) : "srcdoc:" + srcdoc;
        if (element.embeddedDocument() != null && source.equals(element.embeddedDocumentSource())) {
            return element.embeddedDocument();
        }
        Document content = srcdoc == null
                ? new HtmlParser().parse(
                "<!doctype html><html><head></head><body></body></html>", "about:blank")
                : new HtmlParser().parse(
                "<!doctype html><html><head></head><body>" + srcdoc + "</body></html>",
                "about:srcdoc");
        content.transitionTo(DocumentReadyState.COMPLETE);
        element.setEmbeddedDocument(doc.wrapDocument(content));
        element.setEmbeddedDocumentSource(source);
        return element.embeddedDocument();
    }
}

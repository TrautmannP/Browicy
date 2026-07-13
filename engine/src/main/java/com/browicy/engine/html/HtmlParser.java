package com.browicy.engine.html;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import com.browicy.engine.dom.TextNode;
import com.browicy.engine.dom.CommentNode;
import com.browicy.engine.dom.DocumentType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Baut aus der Token-Folge des {@link HtmlTokenizer} einen DOM-Baum
 * (Tree-Construction-Schritt, stark vereinfacht).
 *
 * <p>Vereinfachungen gegenüber der Spezifikation: keine Fehlerkorrektur für
 * falsch verschachtelte Tags (End-Tags schließen bis zum nächsten passenden
 * offenen Element), kein implizites Erzeugen von {@code html}/{@code head}/
 * {@code body}. Void-Elemente wie {@code <br>} werden korrekt ohne Kinder
 * behandelt.</p>
 */
public final class HtmlParser {

    /** Elemente ohne End-Tag laut HTML-Spezifikation. */
    private static final Set<String> VOID_ELEMENTS = Set.of(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "source", "track", "wbr"
    );

    public Document parse(String html) {
        return parse(html, "about:blank");
    }

    public Document parse(String html, String url) {
        List<HtmlToken> tokens = new HtmlTokenizer(html).tokenize();
        Document document = new Document(url);
        Deque<Node> openNodes = new ArrayDeque<>();
        openNodes.push(document);

        for (HtmlToken token : tokens) {
            switch (token.type()) {
                case START_TAG -> {
                    Element element = new Element(token.name(), token.attributes());
                    openNodes.peek().appendChild(element);
                    boolean isVoid = VOID_ELEMENTS.contains(token.name()) || token.selfClosing();
                    if (!isVoid) {
                        openNodes.push(element);
                    }
                }
                case END_TAG -> closeElement(openNodes, token.name());
                case TEXT -> {
                    if (!token.data().isEmpty()
                            && !(openNodes.peek() instanceof Document && token.data().isBlank())) {
                        openNodes.peek().appendChild(new TextNode(token.data()));
                    }
                }
                case COMMENT -> openNodes.peek().appendChild(new CommentNode(token.data()));
                case DOCTYPE -> openNodes.peek().appendChild(new DocumentType(token.data()));
            }
        }
        return document;
    }

    /**
     * Schließt Elemente bis einschließlich des nächsten offenen Elements mit
     * passendem Tag-Namen. Gibt es keines, wird das End-Tag ignoriert.
     */
    private static void closeElement(Deque<Node> openNodes, String tagName) {
        boolean isOpen = openNodes.stream()
                .anyMatch(node -> node instanceof Element element && element.getTagName().equals(tagName));
        if (!isOpen) {
            return;
        }
        while (openNodes.peek() instanceof Element element) {
            openNodes.pop();
            if (element.getTagName().equals(tagName)) {
                return;
            }
        }
    }
}

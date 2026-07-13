package com.browicy.engine.html;

import com.browicy.engine.dom.CommentNode;
import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.DocumentType;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import com.browicy.engine.dom.TextNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Zustandsbehafteter Tree-Construction-Schritt des HTML-Parsers.
 *
 * <p>Der Builder besitzt den Stack offener Elemente und führt die in
 * {@link HtmlTreeConstructionRules} definierten Korrekturregeln aus. Er ist pro
 * Parse-Vorgang kurzlebig und enthält keine CSS- oder Tokenizer-Verantwortung.</p>
 */
final class HtmlTreeBuilder {

    private final Document document;
    private final Deque<Node> openNodes = new ArrayDeque<>();

    HtmlTreeBuilder(Document document) {
        this.document = document;
        openNodes.push(document);
    }

    Document build(List<HtmlToken> tokens) {
        for (HtmlToken token : tokens) {
            process(token);
        }
        return document;
    }

    private void process(HtmlToken token) {
        switch (token.type()) {
            case START_TAG -> insertStartTag(token);
            case END_TAG -> closeExplicitElement(token.name());
            case TEXT -> insertText(token.data());
            case COMMENT -> currentNode().appendChild(new CommentNode(token.data()));
            case DOCTYPE -> currentNode().appendChild(new DocumentType(token.data()));
        }
    }

    private void insertStartTag(HtmlToken token) {
        applyAutoClosingRules(token.name());
        insertImpliedParents(token.name());

        Element element = appendElement(token.name(), token.attributes());
        if (!token.selfClosing() && !HtmlTreeConstructionRules.isVoidElement(token.name())) {
            openNodes.push(element);
        }
    }

    private void applyAutoClosingRules(String startTagName) {
        for (HtmlTreeConstructionRules.AutoCloseRule rule
                : HtmlTreeConstructionRules.autoCloseRulesFor(startTagName)) {
            closeNearestInScope(rule.targetTags(), rule.scopeBoundaries());
        }
    }

    private void insertImpliedParents(String childTagName) {
        String currentParentTagName = currentElementTagName();
        for (String impliedTagName : HtmlTreeConstructionRules.impliedParentChain(
                childTagName, currentParentTagName)) {
            openNodes.push(appendElement(impliedTagName, Map.of()));
        }
    }

    private Element appendElement(String tagName, Map<String, String> attributes) {
        Element element = new Element(tagName, attributes);
        currentNode().appendChild(element);
        return element;
    }

    private void insertText(String data) {
        if (!data.isEmpty() && !(currentNode() instanceof Document && data.isBlank())) {
            currentNode().appendChild(new TextNode(data));
        }
    }

    /**
     * Schließt bis einschließlich des nächsten offenen Elements mit passendem
     * Namen. Nicht geöffnete End-Tags werden wie bisher ignoriert.
     */
    private void closeExplicitElement(String tagName) {
        Element target = findOpenElement(Set.of(tagName), Set.of());
        if (target != null) {
            popThrough(target);
        }
    }

    private void closeNearestInScope(Set<String> targetTags, Set<String> scopeBoundaries) {
        Element target = findOpenElement(targetTags, scopeBoundaries);
        if (target != null) {
            popThrough(target);
        }
    }

    private Element findOpenElement(Set<String> targetTags, Set<String> scopeBoundaries) {
        for (Node node : openNodes) {
            if (node instanceof Element element) {
                String tagName = element.getTagName();
                if (targetTags.contains(tagName)) {
                    return element;
                }
                if (scopeBoundaries.contains(tagName)) {
                    return null;
                }
            }
        }
        return null;
    }

    private void popThrough(Element target) {
        while (openNodes.peek() != target) {
            openNodes.pop();
        }
        openNodes.pop();
    }

    private Node currentNode() {
        return openNodes.peek();
    }

    private String currentElementTagName() {
        return currentNode() instanceof Element element ? element.getTagName() : null;
    }
}

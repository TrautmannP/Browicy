package com.browicy.engine.html;

import com.browicy.engine.dom.CommentNode;
import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.DocumentType;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import com.browicy.engine.dom.TextNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;

final class HtmlTreeBuilder {

    private final Document document;
    private final Deque<Node> openNodes = new ArrayDeque<>();

    HtmlTreeBuilder(Document document) {
        this.document = document;
        openNodes.push(document);
    }

    void accept(HtmlToken token) {
        process(token);
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

    private void closeExplicitElement(String tagName) {
        Element target = findOpenElement(tagName);
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

    private Element findOpenElement(String targetTag) {
        for (Node node : openNodes) {
            if (node instanceof Element element && targetTag.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
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

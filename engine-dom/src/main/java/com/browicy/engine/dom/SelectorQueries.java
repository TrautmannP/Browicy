package com.browicy.engine.dom;

import com.browicy.engine.selectors.SelectorList;
import com.browicy.engine.selectors.SelectorParseException;
import com.browicy.engine.selectors.SelectorParser;
import java.util.ArrayList;
import java.util.List;

/** DOM-Baumabfragen auf Basis der gemeinsamen Selektor-Engine. */
final class SelectorQueries {

    private static final SelectorParser PARSER = new SelectorParser();

    private SelectorQueries() {
    }

    static Element querySelector(Node root, String source) {
        return findFirst(root, parse(source));
    }

    static List<Element> querySelectorAll(Node root, String source) {
        SelectorList selectors = parse(source);
        List<Element> result = new ArrayList<>();
        collect(root, selectors, result);
        return List.copyOf(result);
    }

    private static SelectorList parse(String source) {
        try {
            return PARSER.parse(source);
        } catch (SelectorParseException exception) {
            throw DomException.syntax(exception.getMessage());
        }
    }

    private static Element findFirst(Node root, SelectorList selectors) {
        for (Node child : root.getChildren()) {
            if (child instanceof Element element) {
                if (selectors.matchesAny(element, DomSelectorAdapter.INSTANCE)) {
                    return element;
                }
                Element descendant = findFirst(element, selectors);
                if (descendant != null) {
                    return descendant;
                }
            } else {
                Element descendant = findFirst(child, selectors);
                if (descendant != null) {
                    return descendant;
                }
            }
        }
        return null;
    }

    private static void collect(Node root, SelectorList selectors, List<Element> result) {
        for (Node child : root.getChildren()) {
            if (child instanceof Element element) {
                if (selectors.matchesAny(element, DomSelectorAdapter.INSTANCE)) {
                    result.add(element);
                }
                collect(element, selectors, result);
            } else {
                collect(child, selectors, result);
            }
        }
    }
}

package com.browicy.engine.css;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Liest interne style-Elemente aus dem head und weist passende Regeln zu. */
public final class StyleApplicator {

    public void apply(Document document) {
        Element root = document.getDocumentElement();
        Element head = root == null ? null
                : "head".equals(root.getTagName()) ? root : root.findFirst("head");
        CssParser parser = new CssParser();
        List<CssRule> rules = new ArrayList<>();
        if (head != null && "style".equals(head.getTagName())) {
            rules.addAll(parser.parse(head.getTextContent()));
        }
        if (head != null) {
            for (Element style : head.getElementsByTagName("style")) {
                rules.addAll(parser.parse(style.getTextContent()));
            }
        }
        applyToNode(document, rules, parser);
    }

    private static void applyToNode(Node node, List<CssRule> rules, CssParser parser) {
        if (node instanceof Element element) {
            element.clearComputedStyles();
            Map<String, Candidate> winners = new HashMap<>();
            for (int index = 0; index < rules.size(); index++) {
                CssRule rule = rules.get(index);
                if (rule.selector().equals(element.getTagName())) {
                    addCandidates(winners, rule.declarations(), rule.specificity(), index);
                }
            }
            addCandidates(winners, parser.parseDeclarations(element.getAttribute("style")),
                    1_000, rules.size());
            winners.forEach((property, candidate) ->
                    element.setComputedStyle(property, candidate.value()));
        }
        for (Node child : node.getChildren()) {
            applyToNode(child, rules, parser);
        }
    }

    private static void addCandidates(Map<String, Candidate> winners,
                                      Map<String, String> declarations,
                                      int specificity,
                                      int sourceOrder) {
        declarations.forEach((property, value) -> {
            Candidate candidate = new Candidate(value, specificity, sourceOrder);
            Candidate current = winners.get(property);
            if (current == null || candidate.specificity() > current.specificity()
                    || candidate.specificity() == current.specificity()
                    && candidate.sourceOrder() >= current.sourceOrder()) {
                winners.put(property, candidate);
            }
        });
    }

    private record Candidate(String value, int specificity, int sourceOrder) {
    }
}

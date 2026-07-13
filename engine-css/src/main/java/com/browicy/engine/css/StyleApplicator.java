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
            appendRules(rules, parser, head.getTextContent());
        }
        if (head != null) {
            for (Element style : head.getElementsByTagName("style")) {
                appendRules(rules, parser, style.getTextContent());
            }
        }
        applyToNode(document, rules, parser);
    }

    private static void appendRules(List<CssRule> rules, CssParser parser, String css) {
        int nextSourceOrder = rules.stream()
                .mapToInt(CssRule::sourceOrder)
                .max()
                .orElse(-1) + 1;
        rules.addAll(parser.parse(css, nextSourceOrder));
    }

    private static void applyToNode(Node node, List<CssRule> rules, CssParser parser) {
        if (node instanceof Element element) {
            element.clearComputedStyles();
            Map<String, DeclarationCandidate> winners = new HashMap<>();
            for (CssRule rule : rules) {
                if (rule.selector().matches(element)) {
                    addCandidates(winners, rule.declarations(),
                            new CascadePriority(false, rule.specificity(), rule.sourceOrder()));
                }
            }
            addCandidates(winners, parser.parseDeclarations(element.getAttribute("style")),
                    new CascadePriority(true, Specificity.ZERO, rules.size()));
            winners.forEach((property, candidate) ->
                    element.setComputedStyle(property, candidate.value()));
        }
        for (Node child : node.getChildren()) {
            applyToNode(child, rules, parser);
        }
    }

    private static void addCandidates(Map<String, DeclarationCandidate> winners,
                                      Map<String, String> declarations,
                                      CascadePriority priority) {
        declarations.forEach((property, value) -> {
            DeclarationCandidate candidate = new DeclarationCandidate(value, priority);
            DeclarationCandidate current = winners.get(property);
            if (current == null || candidate.priority().compareTo(current.priority()) >= 0) {
                winners.put(property, candidate);
            }
        });
    }

    private record DeclarationCandidate(String value, CascadePriority priority) {
    }
}

package com.browicy.engine.css;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.DomSelectorAdapter;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import com.browicy.engine.selectors.Specificity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StyleApplicator {

    public void apply(Document document) {
        StyleSheetRegistry registry = new StyleSheetRegistry();
        int sourceOrder = 0;
        for (Element style : document.getElementsByTagName("style")) {
            registry.register(sourceOrder++, style.getTextContent());
        }
        apply(document, registry);
    }

    public void apply(Document document, StyleSheetRegistry registry) {
        applyRules(document, registry.rules());
    }

    public void applyRules(Document document, List<CssRule> rules) {
        applyToNode(document, List.copyOf(rules), new CssParser());
    }

    private static void applyToNode(Node node, List<CssRule> rules, CssParser parser) {
        if (node instanceof Element element) {
            element.clearComputedStyles();
            Map<String, DeclarationCandidate> winners = new HashMap<>();
            for (CssRule rule : rules) {
                if (rule.selector().matches(element, DomSelectorAdapter.INSTANCE)) {
                    addCandidates(winners, rule.declarations(),
                            new CascadePriority(false, rule.specificity(), rule.sourceOrder()));
                }
            }
            addCandidates(winners, parser.parseDeclarations(element.getAttribute("style")),
                    new CascadePriority(true, Specificity.ZERO, Long.MAX_VALUE));
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

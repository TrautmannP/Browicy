package com.browicy.engine.css;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.DomSelectorAdapter;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import com.browicy.engine.selectors.ComplexSelector;
import com.browicy.engine.selectors.CompoundSelector;
import com.browicy.engine.selectors.Specificity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StyleApplicator {

    private static final float DEFAULT_VIEWPORT_WIDTH = 800;
    private static final float DEFAULT_VIEWPORT_HEIGHT = 600;
    private static final int INDEX_THRESHOLD = 64;

    public void apply(Document document) {
        apply(document, DEFAULT_VIEWPORT_WIDTH, DEFAULT_VIEWPORT_HEIGHT);
    }

    public void apply(Document document, float viewportWidth, float viewportHeight) {
        StyleSheetRegistry registry = new StyleSheetRegistry();
        int sourceOrder = 0;
        for (Element style : document.getElementsByTagName("style")) {
            registry.register(sourceOrder++, style.getTextContent());
        }
        apply(document, registry, viewportWidth, viewportHeight);
    }

    public void apply(Document document, StyleSheetRegistry registry) {
        apply(document, registry, DEFAULT_VIEWPORT_WIDTH, DEFAULT_VIEWPORT_HEIGHT);
    }

    public void apply(Document document, StyleSheetRegistry registry,
                      float viewportWidth, float viewportHeight) {
        applyRules(document, registry.rules(), viewportWidth, viewportHeight);
    }

    public void applyRules(Document document, List<CssRule> rules) {
        applyRules(document, rules, DEFAULT_VIEWPORT_WIDTH, DEFAULT_VIEWPORT_HEIGHT);
    }

    public void applyRules(Document document, List<CssRule> rules,
                           float viewportWidth, float viewportHeight) {
        List<CssRule> ruleSnapshot = List.copyOf(rules);
        if (ruleSnapshot.size() >= INDEX_THRESHOLD) {
            RuleIndex index = RuleIndex.build(ruleSnapshot);
            applyIndexedToNode(document, index, new CssParser(), Map.of(),
                    viewportWidth, viewportHeight);
        } else {
            applyToNode(document, ruleSnapshot, new CssParser(), Map.of(),
                    viewportWidth, viewportHeight);
        }
    }

    void applyRulesLinear(Document document, List<CssRule> rules,
                          float viewportWidth, float viewportHeight) {
        applyToNode(document, List.copyOf(rules), new CssParser(), Map.of(),
                viewportWidth, viewportHeight);
    }

    private static void applyToNode(Node node, List<CssRule> rules, CssParser parser,
                                    Map<String, String> inheritedVariables,
                                    float viewportWidth, float viewportHeight) {
        Map<String, String> childVariables = inheritedVariables;
        if (node instanceof Element element) {
            childVariables = applyElementStyles(
                    element, rules, parser, inheritedVariables,
                    viewportWidth, viewportHeight);
        }
        for (Node child : node.getChildren()) {
            applyToNode(child, rules, parser, childVariables, viewportWidth, viewportHeight);
        }
    }

    private static void applyIndexedToNode(Node node, RuleIndex index, CssParser parser,
                                           Map<String, String> inheritedVariables,
                                           float viewportWidth, float viewportHeight) {
        Map<String, String> childVariables = inheritedVariables;
        if (node instanceof Element element) {
            childVariables = applyElementStyles(element, index.candidatesFor(element),
                    parser, inheritedVariables, viewportWidth, viewportHeight);
        }
        for (Node child : node.getChildren()) {
            applyIndexedToNode(child, index, parser, childVariables,
                    viewportWidth, viewportHeight);
        }
    }

    private static Map<String, String> applyElementStyles(
            Element element, List<CssRule> rules, CssParser parser,
            Map<String, String> inheritedVariables,
            float viewportWidth, float viewportHeight) {
        element.clearComputedStyles();
        Map<String, DeclarationCandidate> winners = new LinkedHashMap<>();
        Map<String, Map<String, DeclarationCandidate>> pseudoWinners = new LinkedHashMap<>();
        for (CssRule rule : rules) {
            if (rule.mediaCondition().matches(viewportWidth, viewportHeight)
                    && rule.selector().matches(element, DomSelectorAdapter.INSTANCE)) {
                Map<String, DeclarationCandidate> target = rule.selector().pseudoElement() == null
                        ? winners : pseudoWinners.computeIfAbsent(
                                rule.selector().pseudoElement(), ignored -> new LinkedHashMap<>());
                addCandidates(target, rule.declarations(), rule.importantProperties(),
                        false, rule.specificity(), rule.sourceOrder());
            }
        }
        CssParser.ParsedDeclarationBlock inline =
                parser.parseDeclarationBlock(element.getAttribute("style"));
        addCandidates(winners, inline.declarations(), inline.importantProperties(),
                true, Specificity.ZERO, Long.MAX_VALUE);
        Map<String, String> variables = new LinkedHashMap<>(inheritedVariables);
        winners.forEach((property, candidate) -> {
            if (property.startsWith("--")) variables.put(property, candidate.value());
        });
        Map<String, String> childVariables = Map.copyOf(variables);
        variables.forEach(element::setComputedStyle);
        Map<String, DeclarationCandidate> resolvedWinners = new LinkedHashMap<>();
        for (Map.Entry<String, DeclarationCandidate> winner : winners.entrySet()) {
            String property = winner.getKey();
            if (property.startsWith("--")) continue;
            String value = resolveVariables(winner.getValue().value(), variables,
                    new HashSet<>(), 0);
            if (value == null) continue;
            if (winner.getValue().value().toLowerCase(java.util.Locale.ROOT).contains("var(")) {
                addCandidates(resolvedWinners,
                        parser.parseDeclarations(property + ":" + value),
                        winner.getValue().priority());
            } else {
                addCandidate(resolvedWinners, property,
                        new DeclarationCandidate(value, winner.getValue().priority()));
            }
        }
        resolvedWinners.forEach((property, candidate) ->
                element.setComputedStyle(property, candidate.value()));
        pseudoWinners.forEach((pseudo, candidates) -> applyPseudoStyles(
                element, pseudo, candidates, variables, parser));
        return childVariables;
    }

    private static void applyPseudoStyles(Element element,
                                          String pseudo,
                                          Map<String, DeclarationCandidate> candidates,
                                          Map<String, String> inheritedVariables,
                                          CssParser parser) {
        Map<String, String> variables = new LinkedHashMap<>(inheritedVariables);
        candidates.forEach((property, candidate) -> {
            if (property.startsWith("--")) variables.put(property, candidate.value());
        });
        variables.forEach((property, value) ->
                element.setPseudoComputedStyle(pseudo, property, value));
        Map<String, DeclarationCandidate> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, DeclarationCandidate> entry : candidates.entrySet()) {
            if (entry.getKey().startsWith("--")) continue;
            String value = resolveVariables(entry.getValue().value(), variables,
                    new HashSet<>(), 0);
            if (value == null) continue;
            if (entry.getValue().value().toLowerCase(java.util.Locale.ROOT).contains("var(")) {
                addCandidates(resolved, parser.parseDeclarations(entry.getKey() + ":" + value),
                        entry.getValue().priority());
            } else {
                addCandidate(resolved, entry.getKey(),
                        new DeclarationCandidate(value, entry.getValue().priority()));
            }
        }
        resolved.forEach((property, candidate) ->
                element.setPseudoComputedStyle(pseudo, property, candidate.value()));
    }

    private static String resolveVariables(String value, Map<String, String> variables,
                                           HashSet<String> resolving, int depth) {
        if (value == null || depth > 32) return null;
        StringBuilder result = new StringBuilder();
        int offset = 0;
        while (true) {
            int start = indexOfVar(value, offset);
            if (start < 0) {
                result.append(value.substring(offset));
                return result.toString().strip();
            }
            result.append(value, offset, start);
            int open = value.indexOf('(', start);
            int close = matchingParenthesis(value, open);
            if (close < 0) return null;
            String arguments = value.substring(open + 1, close);
            int comma = topLevelComma(arguments);
            String name = (comma < 0 ? arguments : arguments.substring(0, comma)).strip();
            if (!name.startsWith("--") || !resolving.add(name)) return null;
            String replacement = variables.get(name);
            if (replacement == null && comma >= 0) replacement = arguments.substring(comma + 1);
            replacement = resolveVariables(replacement, variables, resolving, depth + 1);
            resolving.remove(name);
            if (replacement == null) return null;
            result.append(replacement);
            offset = close + 1;
        }
    }

    private static int indexOfVar(String value, int offset) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        int found = lower.indexOf("var(", offset);
        return found;
    }

    private static int matchingParenthesis(String value, int open) {
        int depth = 0;
        for (int index = open; index < value.length(); index++) {
            if (value.charAt(index) == '(') depth++;
            else if (value.charAt(index) == ')' && --depth == 0) return index;
        }
        return -1;
    }

    private static int topLevelComma(String value) {
        int depth = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '(') depth++;
            else if (current == ')') depth--;
            else if (current == ',' && depth == 0) return index;
        }
        return -1;
    }

    private static void addCandidates(Map<String, DeclarationCandidate> winners,
                                      Map<String, String> declarations,
                                      CascadePriority priority) {
        declarations.forEach((property, value) -> {
            addCandidate(winners, property, new DeclarationCandidate(value, priority));
        });
    }

    private static void addCandidates(Map<String, DeclarationCandidate> winners,
                                      Map<String, String> declarations,
                                      java.util.Set<String> importantProperties,
                                      boolean inlineStyle,
                                      Specificity specificity,
                                      long sourceOrder) {
        declarations.forEach((property, value) -> addCandidate(winners, property,
                new DeclarationCandidate(value, new CascadePriority(
                        importantProperties.contains(property), inlineStyle,
                        specificity, sourceOrder))));
    }

    private static void addCandidate(Map<String, DeclarationCandidate> winners,
                                     String property, DeclarationCandidate candidate) {
        DeclarationCandidate current = winners.get(property);
        if (current == null || candidate.priority().compareTo(current.priority()) >= 0) {
            winners.put(property, candidate);
        }
    }

    private static final class RuleIndex {

        private final Map<String, List<CssRule>> rulesById = new HashMap<>();
        private final Map<String, List<CssRule>> rulesByClass = new HashMap<>();
        private final Map<String, List<CssRule>> rulesByTag = new HashMap<>();
        private final List<CssRule> universalRules = new ArrayList<>();

        private static RuleIndex build(List<CssRule> rules) {
            RuleIndex index = new RuleIndex();
            for (CssRule rule : rules) {
                if (!(rule.selector() instanceof ComplexSelector complex)) {
                    index.universalRules.add(rule);
                    continue;
                }
                CompoundSelector rightmost = complex.steps().getLast().selector();
                boolean constrained = false;
                if (rightmost.id() != null) {
                    index.rulesById.computeIfAbsent(
                            rightmost.id(), ignored -> new ArrayList<>()).add(rule);
                    constrained = true;
                }
                for (String className : rightmost.classes()) {
                    index.rulesByClass.computeIfAbsent(
                            className, ignored -> new ArrayList<>()).add(rule);
                    constrained = true;
                }
                if (rightmost.typeName() != null && !"*".equals(rightmost.typeName())) {
                    index.rulesByTag.computeIfAbsent(
                            rightmost.typeName().toLowerCase(java.util.Locale.ROOT),
                            ignored -> new ArrayList<>()).add(rule);
                    constrained = true;
                }
                if (!constrained) {
                    index.universalRules.add(rule);
                }
            }
            return index;
        }

        List<CssRule> candidatesFor(Element element) {
            HashSet<CssRule> candidates = new HashSet<>(universalRules);
            String id = element.getId();
            if (id != null) {
                List<CssRule> byId = rulesById.get(id);
                if (byId != null) {
                    candidates.addAll(byId);
                }
            }
            for (String className : element.getClassNames()) {
                List<CssRule> byClass = rulesByClass.get(className);
                if (byClass != null) {
                    candidates.addAll(byClass);
                }
            }
            List<CssRule> byTag = rulesByTag.get(
                    element.getTagName().toLowerCase(java.util.Locale.ROOT));
            if (byTag != null) {
                candidates.addAll(byTag);
            }
            if (element.getNamespaceUri() != null) {
                List<CssRule> byLocalName = rulesByTag.get(
                        element.getLocalName().toLowerCase(java.util.Locale.ROOT));
                if (byLocalName != null) {
                    candidates.addAll(byLocalName);
                }
            }
            if (candidates.isEmpty()) {
                return List.of();
            }
            if (candidates.size() == 1) {
                return List.copyOf(candidates);
            }
            List<CssRule> sorted = new ArrayList<>(candidates);
            sorted.sort(Comparator.comparingLong(CssRule::sourceOrder));
            return sorted;
        }
    }

    private record DeclarationCandidate(String value, CascadePriority priority) {
    }
}

package com.browicy.engine.html;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deklarative Regeln für den vereinfachten HTML-Tree-Construction-Schritt.
 *
 * <p>Die Klasse enthält ausschließlich statische HTML-Semantik. Änderungen an
 * optionalen End-Tags oder impliziten Elternknoten bleiben dadurch von der
 * Stack- und DOM-Manipulation im {@link HtmlTreeBuilder} getrennt.</p>
 */
final class HtmlTreeConstructionRules {

    private static final Set<String> VOID_ELEMENTS = Set.of(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "source", "track", "wbr"
    );

    private static final Set<String> PARAGRAPH_CLOSING_START_TAGS = Set.of(
            "address", "article", "aside", "blockquote", "center", "details", "dialog",
            "dir", "div", "dl", "dt", "dd", "fieldset", "figcaption", "figure",
            "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header",
            "hgroup", "hr", "li", "listing", "main", "menu", "nav", "ol", "p",
            "pre", "search", "section", "summary", "table", "tbody", "td", "tfoot",
            "th", "thead", "tr", "ul"
    );

    private static final Set<String> TABLE_SECTIONS = Set.of("tbody", "thead", "tfoot");
    private static final Set<String> TABLE_CELLS = Set.of("td", "th");

    private static final Set<String> BUTTON_SCOPE_BOUNDARIES = Set.of(
            "applet", "button", "caption", "html", "marquee", "object", "table",
            "td", "th", "template"
    );
    private static final Set<String> LIST_ITEM_SCOPE_BOUNDARIES = Set.of(
            "applet", "caption", "html", "marquee", "object", "ol", "table",
            "td", "th", "template", "ul"
    );
    private static final Set<String> DESCRIPTION_LIST_SCOPE_BOUNDARIES = Set.of(
            "dl", "html", "table", "template"
    );
    private static final Set<String> TABLE_CELL_SCOPE_BOUNDARIES = Set.of(
            "html", "table", "tbody", "tfoot", "th", "thead", "tr", "td", "template"
    );
    private static final Set<String> TABLE_ROW_SCOPE_BOUNDARIES = Set.of(
            "html", "table", "tbody", "tfoot", "thead", "template"
    );
    private static final Set<String> TABLE_SECTION_SCOPE_BOUNDARIES = Set.of(
            "html", "table", "template"
    );

    private static final List<AutoCloseRule> AUTO_CLOSE_RULES = List.of(
            new AutoCloseRule(PARAGRAPH_CLOSING_START_TAGS, Set.of("p"), BUTTON_SCOPE_BOUNDARIES),
            new AutoCloseRule(Set.of("li"), Set.of("li"), LIST_ITEM_SCOPE_BOUNDARIES),
            new AutoCloseRule(Set.of("dt", "dd"), Set.of("dt", "dd"),
                    DESCRIPTION_LIST_SCOPE_BOUNDARIES),
            new AutoCloseRule(TABLE_CELLS, TABLE_CELLS, TABLE_CELL_SCOPE_BOUNDARIES),
            new AutoCloseRule(Set.of("tr"), TABLE_CELLS, TABLE_CELL_SCOPE_BOUNDARIES),
            new AutoCloseRule(Set.of("tr"), Set.of("tr"), TABLE_ROW_SCOPE_BOUNDARIES),
            new AutoCloseRule(TABLE_SECTIONS, TABLE_CELLS, TABLE_CELL_SCOPE_BOUNDARIES),
            new AutoCloseRule(TABLE_SECTIONS, Set.of("tr"), TABLE_ROW_SCOPE_BOUNDARIES),
            new AutoCloseRule(TABLE_SECTIONS, TABLE_SECTIONS, TABLE_SECTION_SCOPE_BOUNDARIES)
    );

    private static final Map<String, List<AutoCloseRule>> AUTO_CLOSE_RULES_BY_START_TAG =
            indexAutoCloseRules();

    private static final Map<InsertionContext, List<String>> IMPLIED_PARENT_CHAINS = Map.ofEntries(
            Map.entry(new InsertionContext("tr", "table"), List.of("tbody")),
            Map.entry(new InsertionContext("td", "table"), List.of("tbody", "tr")),
            Map.entry(new InsertionContext("th", "table"), List.of("tbody", "tr")),
            Map.entry(new InsertionContext("td", "tbody"), List.of("tr")),
            Map.entry(new InsertionContext("th", "tbody"), List.of("tr")),
            Map.entry(new InsertionContext("td", "thead"), List.of("tr")),
            Map.entry(new InsertionContext("th", "thead"), List.of("tr")),
            Map.entry(new InsertionContext("td", "tfoot"), List.of("tr")),
            Map.entry(new InsertionContext("th", "tfoot"), List.of("tr"))
    );

    private HtmlTreeConstructionRules() {
    }

    static boolean isVoidElement(String tagName) {
        return VOID_ELEMENTS.contains(tagName);
    }

    static List<AutoCloseRule> autoCloseRulesFor(String startTagName) {
        return AUTO_CLOSE_RULES_BY_START_TAG.getOrDefault(startTagName, List.of());
    }

    static List<String> impliedParentChain(String childTagName, String currentParentTagName) {
        if (currentParentTagName == null) {
            return List.of();
        }
        return IMPLIED_PARENT_CHAINS.getOrDefault(
                new InsertionContext(childTagName, currentParentTagName), List.of());
    }

    private static Map<String, List<AutoCloseRule>> indexAutoCloseRules() {
        Map<String, List<AutoCloseRule>> indexed = new LinkedHashMap<>();
        for (AutoCloseRule rule : AUTO_CLOSE_RULES) {
            for (String trigger : rule.triggeringStartTags()) {
                indexed.computeIfAbsent(trigger, ignored -> new ArrayList<>()).add(rule);
            }
        }
        indexed.replaceAll((ignored, rules) -> List.copyOf(rules));
        return Map.copyOf(indexed);
    }

    record AutoCloseRule(
            Set<String> triggeringStartTags,
            Set<String> targetTags,
            Set<String> scopeBoundaries) {

        AutoCloseRule {
            triggeringStartTags = Set.copyOf(triggeringStartTags);
            targetTags = Set.copyOf(targetTags);
            scopeBoundaries = Set.copyOf(scopeBoundaries);
        }
    }

    private record InsertionContext(String childTagName, String parentTagName) {
    }
}

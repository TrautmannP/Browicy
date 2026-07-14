package com.browicy.tools;

import com.browicy.engine.css.CssParser;
import com.browicy.engine.css.CssStyleSheet;
import com.browicy.engine.css.StyleSheetRegistry;
import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.js.JsExecutionResult;
import com.browicy.engine.selectors.SelectorParseException;
import com.browicy.engine.selectors.SelectorParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CompatibilityReport {

    private static final int MAX_EXAMPLES = 3;
    private static final Pattern JS_REFERENCE_ERROR = Pattern.compile(
            "(?:ReferenceError:\\s*)?([A-Za-z_$][\\w$]*) is not defined");
    private static final Pattern JS_MISSING_FUNCTION = Pattern.compile(
            "(?:TypeError:\\s*)?([^\\r\\n(]{1,120}) is not a function");
    private static final Pattern CSS_AT_RULE = Pattern.compile("^@([\\w-]+)");
    private static final Pattern CSS_AT_RULE_USAGE = Pattern.compile("@([\\w-]+)\\b");
    private static final Map<String, String> SPECIALIZED_HTML_FEATURES = Map.ofEntries(
            Map.entry("canvas", "Canvas 2D/WebGL rendering"),
            Map.entry("video", "HTML media playback"),
            Map.entry("audio", "HTML media playback"),
            Map.entry("iframe", "nested browsing contexts"),
            Map.entry("svg", "SVG rendering"),
            Map.entry("math", "MathML rendering"),
            Map.entry("object", "embedded object content"),
            Map.entry("embed", "embedded content"),
            Map.entry("template", "HTML template content"),
            Map.entry("slot", "Shadow DOM slots"));

    private final CssParser cssParser = new CssParser();
    private final SelectorParser selectorParser = new SelectorParser();
    private final Map<IssueKey, MutableIssue> issues = new LinkedHashMap<>();

    static Map<String, Object> build(Document document,
                                     StyleSheetRegistry styleSheets,
                                     JsExecutionResult javascript) {
        CompatibilityReport report = new CompatibilityReport();
        report.inspectStyleSheets(styleSheets);
        report.inspectInlineStyles(document);
        report.inspectJavaScript(javascript);
        report.inspectHtml(document);
        return report.toMap();
    }

    private void inspectStyleSheets(StyleSheetRegistry registry) {
        int inlineIndex = 0;
        for (CssStyleSheet sheet : registry.styleSheets()) {
            String source = sheet.href();
            if (source == null || source.isBlank()) {
                source = "inline-stylesheet-" + (++inlineIndex);
            }
            Matcher atRules = CSS_AT_RULE_USAGE.matcher(sheet.sourceText());
            while (atRules.find()) {
                String name = atRules.group(1).toLowerCase(Locale.ROOT);
                if (!name.equals("font-face")) {
                    add("css", "at-rule:@" + name, "unsupported-at-rule", source,
                            "@" + name, "certain");
                }
            }
            for (String rule : sheet.ruleTexts()) {
                inspectRule(rule, source);
            }
        }
    }

    private void inspectRule(String rule, String source) {
        int openBrace = rule.indexOf('{');
        int closeBrace = rule.lastIndexOf('}');
        if (openBrace < 1 || closeBrace <= openBrace) return;
        String selector = rule.substring(0, openBrace).strip();
        Matcher atRule = CSS_AT_RULE.matcher(selector.toLowerCase(Locale.ROOT));
        if (atRule.find()) {
            if (atRule.group(1).equals("font-face")) return;
        } else {
            try {
                selectorParser.parse(selector);
            } catch (SelectorParseException failure) {
                add("css", "selector:" + selector, "unsupported-selector", source,
                        abbreviate(rule, 240), "certain");
            }
        }
        inspectDeclarations(rule.substring(openBrace + 1, closeBrace), source,
                abbreviate(selector, 120));
    }

    private void inspectInlineStyles(Document document) {
        for (Element element : document.getElementsByTagName("*")) {
            String style = element.getAttribute("style");
            if (style != null && !style.isBlank()) {
                inspectDeclarations(style, elementPath(element), abbreviate(style, 160));
            }
        }
    }

    private void inspectDeclarations(String declarations, String source, String context) {
        for (String declaration : declarations.split(";")) {
            int separator = declaration.indexOf(':');
            if (separator < 1) continue;
            String property = declaration.substring(0, separator).strip().toLowerCase(Locale.ROOT);
            String value = declaration.substring(separator + 1).strip();
            if (property.isEmpty() || value.isEmpty() || cssParser.supports(property, value)) continue;
            if (!cssParser.supportsProperty(property)) {
                add("css", "property:" + property, "unsupported-property", source,
                        context + " { " + property + ": " + abbreviate(value, 120) + " }", "certain");
            } else {
                add("css", "value:" + property, "unsupported-value", source,
                        context + " { " + property + ": " + abbreviate(value, 120) + " }", "certain");
            }
        }
    }

    private void inspectJavaScript(JsExecutionResult javascript) {
        for (String error : javascript.errors()) {
            Matcher reference = JS_REFERENCE_ERROR.matcher(error);
            if (reference.find()) {
                add("javascript", "global:" + reference.group(1), "missing-browser-api",
                        sourceLocation(error), abbreviate(error, 240), "likely");
                continue;
            }
            Matcher function = JS_MISSING_FUNCTION.matcher(error);
            if (function.find()) {
                String api = function.group(1).strip();
                add("javascript", "api:" + api, "missing-or-incomplete-browser-api",
                        sourceLocation(error), abbreviate(error, 240), "likely");
            }
        }
    }

    private void inspectHtml(Document document) {
        for (Map.Entry<String, String> entry : SPECIALIZED_HTML_FEATURES.entrySet()) {
            List<Element> elements = document.getElementsByTagName(entry.getKey());
            if (!elements.isEmpty()) {
                for (Element element : elements) {
                    add("html", "element:" + entry.getKey(), "specialized-behavior-not-implemented",
                            elementPath(element), entry.getValue(), "certain");
                }
            }
        }
        for (Element element : document.getElementsByTagName("*")) {
            if (element.getTagName().contains("-")) {
                add("html", "custom-elements", "custom-elements-not-implemented",
                        elementPath(element), "<" + element.getTagName() + ">", "certain");
            }
        }
    }

    private void add(String category, String feature, String reason, String source,
                     String example, String confidence) {
        IssueKey key = new IssueKey(category, feature, reason);
        MutableIssue issue = issues.computeIfAbsent(key,
                ignored -> new MutableIssue(category, feature, reason, confidence));
        issue.occurrences++;
        if (source != null && !source.isBlank() && issue.sources.size() < MAX_EXAMPLES
                && !issue.sources.contains(source)) {
            issue.sources.add(source);
        }
        if (example != null && !example.isBlank() && issue.examples.size() < MAX_EXAMPLES
                && !issue.examples.contains(example)) {
            issue.examples.add(example);
        }
    }

    private Map<String, Object> toMap() {
        Map<String, Integer> categories = new TreeMap<>();
        List<Map<String, Object>> entries = issues.values().stream()
                .sorted((left, right) -> {
                    int category = left.category.compareTo(right.category);
                    return category != 0 ? category : left.feature.compareTo(right.feature);
                })
                .map(issue -> {
                    categories.merge(issue.category, 1, Integer::sum);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("category", issue.category);
                    item.put("feature", issue.feature);
                    item.put("reason", issue.reason);
                    item.put("confidence", issue.confidence);
                    item.put("occurrences", issue.occurrences);
                    item.put("sources", List.copyOf(issue.sources));
                    item.put("examples", List.copyOf(issue.examples));
                    return item;
                }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unsupportedFeatures", entries.size());
        result.put("occurrences", issues.values().stream().mapToInt(issue -> issue.occurrences).sum());
        result.put("byCategory", categories);
        result.put("issues", entries);
        return result;
    }

    static void log(Map<String, Object> report) {
        Object count = report.get("unsupportedFeatures");
        if (!(count instanceof Number number) || number.intValue() == 0) return;
        System.err.println("[compatibility] " + count + " unsupported page features detected");
        Object rawIssues = report.get("issues");
        if (rawIssues instanceof Iterable<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> issue) {
                    System.err.println("[compatibility] " + issue.get("category") + " "
                            + issue.get("feature") + " (" + issue.get("occurrences") + "x)");
                }
            }
        }
    }

    private static String elementPath(Element element) {
        String id = element.getAttribute("id");
        if (id != null && !id.isBlank()) return element.getTagName() + "#" + id;
        String classes = element.getAttribute("class");
        if (classes != null && !classes.isBlank()) {
            return element.getTagName() + "." + classes.strip().split("\\s+")[0];
        }
        return element.getTagName();
    }

    private static String sourceLocation(String error) {
        int open = error.lastIndexOf(" (");
        return open < 0 || !error.endsWith(")") ? "javascript" : error.substring(open + 2, error.length() - 1);
    }

    private static String abbreviate(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length) + "...";
    }

    private record IssueKey(String category, String feature, String reason) { }

    private static final class MutableIssue {
        final String category;
        final String feature;
        final String reason;
        final String confidence;
        final List<String> sources = new ArrayList<>();
        final List<String> examples = new ArrayList<>();
        int occurrences;

        MutableIssue(String category, String feature, String reason, String confidence) {
            this.category = category;
            this.feature = feature;
            this.reason = reason;
            this.confidence = confidence;
        }
    }
}

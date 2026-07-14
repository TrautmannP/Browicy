package com.browicy.engine.css;

import com.browicy.engine.dom.Element;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CssStyleSheet {

    private static final long RULE_ORDER_RANGE = 1L << 32;

    private final CssParser parser;
    private final int sourceOrder;
    private final Element ownerNode;
    private final String href;
    private final List<String> ruleTexts = new ArrayList<>();
    private List<CssFontFace> fontFaces = List.of();
    private String sourceText;

    CssStyleSheet(CssParser parser, int sourceOrder, Element ownerNode, String href, String css) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.sourceOrder = sourceOrder;
        this.ownerNode = ownerNode;
        this.href = href;
        this.sourceText = css == null ? "" : css;
        ruleTexts.addAll(parser.ruleSources(css));
        fontFaces = parser.fontFaces(css);
    }

    public Element ownerNode() {
        return ownerNode;
    }

    public String href() {
        return href;
    }

    public synchronized int ruleCount() {
        return ruleTexts.size();
    }

    public synchronized String ruleText(int index) {
        return ruleTexts.get(index);
    }

    public synchronized List<String> ruleTexts() {
        return List.copyOf(ruleTexts);
    }

    public synchronized String sourceText() {
        return sourceText;
    }

    synchronized void replaceRules(String css) {
        sourceText = css == null ? "" : css;
        ruleTexts.clear();
        ruleTexts.addAll(parser.ruleSources(css));
        fontFaces = parser.fontFaces(css);
    }

    public synchronized List<CssFontFace> fontFaces() {
        return fontFaces;
    }

    public synchronized int insertRule(String rule, int index) {
        Objects.requireNonNull(rule, "rule");
        if (index < 0 || index > ruleTexts.size()) {
            throw new IndexOutOfBoundsException("CSS-Regelindex ausserhalb der Regelliste: " + index);
        }
        List<String> parsed = parser.ruleSources(rule);
        if (parsed.size() != 1) {
            throw new IllegalArgumentException("Ungueltige CSS-Regel");
        }
        ruleTexts.add(index, parsed.getFirst());
        sourceText = String.join(System.lineSeparator(), ruleTexts);
        return index;
    }

    public synchronized void deleteRule(int index) {
        if (index < 0 || index >= ruleTexts.size()) {
            throw new IndexOutOfBoundsException("CSS-Regelindex ausserhalb der Regelliste: " + index);
        }
        ruleTexts.remove(index);
        sourceText = String.join(System.lineSeparator(), ruleTexts);
    }

    synchronized List<CssRule> parsedRules() {
        List<CssRule> parsed = new ArrayList<>();
        long nextOrder = Math.multiplyExact((long) sourceOrder, RULE_ORDER_RANGE);
        for (String ruleText : ruleTexts) {
            List<CssRule> rules = parser.parse(ruleText, nextOrder);
            parsed.addAll(rules);
            nextOrder++;
        }
        return List.copyOf(parsed);
    }
}

package com.browicy.engine.css;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

public final class StyleSheetRegistry {

    private static final long RULE_ORDER_RANGE = 1L << 32;

    private final CssParser parser;
    private final NavigableMap<Integer, List<CssRule>> rulesBySource = new TreeMap<>();

    public StyleSheetRegistry() {
        this(new CssParser());
    }

    StyleSheetRegistry(CssParser parser) {
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    public synchronized void register(int sourceOrder, String css) {
        if (sourceOrder < 0) {
            throw new IllegalArgumentException("Die Stylesheet-Reihenfolge darf nicht negativ sein");
        }
        long ruleOrderStart = Math.multiplyExact((long) sourceOrder, RULE_ORDER_RANGE);
        rulesBySource.put(sourceOrder, List.copyOf(parser.parse(css, ruleOrderStart)));
    }

    public synchronized boolean contains(int sourceOrder) {
        return rulesBySource.containsKey(sourceOrder);
    }

    public synchronized int size() {
        return rulesBySource.size();
    }

    public synchronized List<CssRule> rules() {
        List<CssRule> snapshot = new ArrayList<>();
        rulesBySource.values().forEach(snapshot::addAll);
        return List.copyOf(snapshot);
    }
}

package com.browicy.engine.css;

import com.browicy.engine.dom.Element;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class StyleSheetRegistry {

    private final CssParser parser;
    private final NavigableMap<Integer, CssStyleSheet> sheetsBySource = new TreeMap<>();
    private final IdentityHashMap<Element, CssStyleSheet> sheetsByOwner = new IdentityHashMap<>();

    public StyleSheetRegistry() {
        this(new CssParser());
    }

    StyleSheetRegistry(CssParser parser) {
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    public synchronized CssStyleSheet register(int sourceOrder, String css) {
        return register(sourceOrder, null, null, css);
    }

    public synchronized CssStyleSheet register(int sourceOrder, Element ownerNode, String css) {
        return register(sourceOrder, ownerNode, null, css);
    }

    public synchronized CssStyleSheet register(
            int sourceOrder, Element ownerNode, String href, String css) {
        if (sourceOrder < 0) {
            throw new IllegalArgumentException("Die Stylesheet-Reihenfolge darf nicht negativ sein");
        }
        CssStyleSheet previous = sheetsBySource.get(sourceOrder);
        if (previous != null && previous.ownerNode() == ownerNode
                && Objects.equals(previous.href(), href)) {
            previous.replaceRules(css);
            return previous;
        }
        previous = sheetsBySource.remove(sourceOrder);
        if (previous != null && previous.ownerNode() != null) {
            sheetsByOwner.remove(previous.ownerNode());
        }
        CssStyleSheet sheet = new CssStyleSheet(parser, sourceOrder, ownerNode, href, css);
        sheetsBySource.put(sourceOrder, sheet);
        if (ownerNode != null) {
            sheetsByOwner.put(ownerNode, sheet);
        }
        return sheet;
    }

    public synchronized CssStyleSheet ensureStyleSheet(Element ownerNode, String css) {
        Objects.requireNonNull(ownerNode, "ownerNode");
        CssStyleSheet existing = sheetsByOwner.get(ownerNode);
        if (existing != null) {
            return existing;
        }
        int sourceOrder = sheetsBySource.isEmpty() ? 0 : sheetsBySource.lastKey() + 1;
        return register(sourceOrder, ownerNode, css);
    }

    public synchronized boolean contains(int sourceOrder) {
        return sheetsBySource.containsKey(sourceOrder);
    }

    public synchronized int size() {
        return sheetsBySource.size();
    }

    public synchronized Optional<CssStyleSheet> styleSheet(Element ownerNode) {
        return Optional.ofNullable(sheetsByOwner.get(ownerNode));
    }

    public synchronized List<CssStyleSheet> styleSheets() {
        return List.copyOf(sheetsBySource.values());
    }

    public synchronized List<CssRule> rules() {
        List<CssRule> snapshot = new ArrayList<>();
        sheetsBySource.values().forEach(sheet -> snapshot.addAll(sheet.parsedRules()));
        return List.copyOf(snapshot);
    }
}

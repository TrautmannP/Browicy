package com.browicy.tools;

import com.browicy.engine.BrowicyEngine;
import com.browicy.engine.PageSession;
import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import com.browicy.engine.js.JsExecutionResult;
import com.browicy.engine.net.NetworkRequestEvent;
import com.browicy.engine.render.RenderBox;
import com.browicy.engine.render.RenderInlineBox;
import com.browicy.engine.render.RenderLineBreak;
import com.browicy.engine.render.RenderNode;
import com.browicy.engine.render.RenderTextRun;
import com.browicy.engine.render.RenderTreeBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Loads a page without Swing and emits a deterministic JSON diagnostics report. */
public final class BrowserInspector {

    private BrowserInspector() { }

    public static void main(String[] arguments) throws Exception {
        Options options = Options.parse(arguments);
        Instant started = Instant.now();
        List<NetworkRequestEvent> network = java.util.Collections.synchronizedList(new ArrayList<>());
        Map<String, Object> report;
        try (BrowicyEngine engine = new BrowicyEngine()) {
            engine.addRequestObserver(network::add);
            try (PageSession session = engine.loadPageSession(options.url(), ignored -> { })) {
                session.awaitResources();
                report = buildReport(options.url(), started, session, List.copyOf(network));
            }
        }
        String json = Json.write(report, options.pretty(), 0) + System.lineSeparator();
        if (options.output() == null) {
            System.out.print(json);
        } else {
            Path parent = options.output().toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(options.output(), json, StandardCharsets.UTF_8);
        }
    }

    private static Map<String, Object> buildReport(String requestedUrl,
                                                    Instant started,
                                                    PageSession session,
                                                    List<NetworkRequestEvent> network) {
        Document document = session.document();
        JsExecutionResult js = session.runtime().snapshot();
        DomStats dom = new DomStats();
        dom.visit(document);
        RenderStats render = new RenderStats();
        render.visit(new RenderTreeBuilder().build(document).root());

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("url", document.getUrl());
        page.put("title", document.getTitle());
        page.put("readyState", document.getReadyState().scriptValue());
        page.put("textPreview", abbreviate(document.getTextContent().replaceAll("\\s+", " ").strip(), 500));
        Element score = document.getElementById("score");
        if (score != null) page.put("detectedScore", score.getTextContent());

        Map<String, Object> domReport = new LinkedHashMap<>();
        domReport.put("nodes", dom.nodes);
        domReport.put("elements", dom.elements);
        domReport.put("textNodes", dom.textNodes);
        domReport.put("tags", dom.tags);

        Map<String, Object> css = new LinkedHashMap<>();
        css.put("stylesheets", session.styleSheets().size());
        css.put("acceptedRules", session.styleSheets().rules().size());

        Map<String, Object> renderReport = new LinkedHashMap<>();
        renderReport.put("nodes", render.nodes);
        renderReport.put("blockBoxes", render.blocks);
        renderReport.put("inlineBoxes", render.inlineBoxes);
        renderReport.put("textRuns", render.textRuns);
        renderReport.put("lineBreaks", render.lineBreaks);

        Map<String, Object> javascript = new LinkedHashMap<>();
        javascript.put("console", js.consoleMessages());
        javascript.put("errors", js.errors());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1);
        result.put("generatedAt", Instant.now().toString());
        result.put("durationMs", Duration.between(started, Instant.now()).toMillis());
        result.put("requestedUrl", requestedUrl);
        result.put("page", page);
        result.put("dom", domReport);
        result.put("css", css);
        result.put("renderTree", renderReport);
        result.put("javascript", javascript);
        result.put("network", network.stream().map(BrowserInspector::networkEvent).toList());
        result.put("healthy", !document.getUrl().equals("about:error") && js.errors().isEmpty());
        return result;
    }

    private static Map<String, Object> networkEvent(NetworkRequestEvent event) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("requestId", event.requestId());
        item.put("at", event.at().toString());
        item.put("resourceType", event.resourceType().name().toLowerCase());
        switch (event) {
            case NetworkRequestEvent.Started started -> {
                item.put("phase", "started"); item.put("url", started.url());
            }
            case NetworkRequestEvent.Redirected redirected -> {
                item.put("phase", "redirected"); item.put("from", redirected.from().toString());
                item.put("to", redirected.to().toString()); item.put("status", redirected.statusCode());
            }
            case NetworkRequestEvent.Loaded loaded -> {
                item.put("phase", "loaded"); item.put("url", loaded.finalUri().toString());
                item.put("status", loaded.statusCode()); item.put("sizeBytes", loaded.sizeBytes());
            }
            case NetworkRequestEvent.Failed failed -> {
                item.put("phase", "failed"); item.put("url", failed.url());
                item.put("error", String.valueOf(failed.cause().getMessage()));
            }
            case NetworkRequestEvent.Cancelled cancelled -> {
                item.put("phase", "cancelled"); item.put("url", cancelled.url());
            }
        }
        return item;
    }

    private static String abbreviate(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length) + "...";
    }

    private static final class DomStats {
        int nodes; int elements; int textNodes;
        final Map<String, Integer> tags = new TreeMap<>();
        void visit(Node node) {
            nodes++;
            if (node instanceof Element element) {
                elements++;
                tags.merge(element.getTagName(), 1, Integer::sum);
            } else if (node.getNodeType() == Node.TEXT_NODE) textNodes++;
            node.getChildren().forEach(this::visit);
        }
    }

    private static final class RenderStats {
        int nodes; int blocks; int inlineBoxes; int textRuns; int lineBreaks;
        void visit(RenderNode node) {
            nodes++;
            switch (node) {
                case RenderBox box -> { blocks++; box.children().forEach(this::visit); }
                case RenderInlineBox box -> { inlineBoxes++; box.children().forEach(this::visit); }
                case RenderTextRun ignored -> textRuns++;
                case RenderLineBreak ignored -> lineBreaks++;
                default -> { }
            }
        }
    }

    private record Options(String url, Path output, boolean pretty) {
        static Options parse(String[] arguments) {
            if (arguments.length == 0 || "--help".equals(arguments[0])) {
                System.out.println("Usage: java -jar browicy-inspect.jar <url> [--output report.json] [--compact]");
                System.exit(arguments.length == 0 ? 2 : 0);
            }
            Path output = null;
            boolean pretty = true;
            for (int index = 1; index < arguments.length; index++) {
                switch (arguments[index]) {
                    case "--output" -> {
                        if (++index >= arguments.length) throw new IllegalArgumentException("--output benötigt einen Pfad");
                        output = Path.of(arguments[index]);
                    }
                    case "--compact" -> pretty = false;
                    default -> throw new IllegalArgumentException("Unbekannte Option: " + arguments[index]);
                }
            }
            return new Options(arguments[0], output, pretty);
        }
    }

    private static final class Json {
        static String write(Object value, boolean pretty, int depth) {
            if (value == null) return "null";
            if (value instanceof String string) return quote(string);
            if (value instanceof Number || value instanceof Boolean) return value.toString();
            if (value instanceof Map<?, ?> map) {
                List<String> entries = new ArrayList<>();
                map.forEach((key, child) -> entries.add(quote(String.valueOf(key)) + ":"
                        + (pretty ? " " : "") + write(child, pretty, depth + 1)));
                return collection("{", "}", entries, pretty, depth);
            }
            if (value instanceof Iterable<?> iterable) {
                List<String> entries = new ArrayList<>();
                iterable.forEach(child -> entries.add(write(child, pretty, depth + 1)));
                return collection("[", "]", entries, pretty, depth);
            }
            return quote(value.toString());
        }
        private static String collection(String open, String close, List<String> entries,
                                         boolean pretty, int depth) {
            if (entries.isEmpty()) return open + close;
            if (!pretty) return open + String.join(",", entries) + close;
            String indent = "  ".repeat(depth + 1);
            return open + "\n" + indent + String.join(",\n" + indent, entries)
                    + "\n" + "  ".repeat(depth) + close;
        }
        private static String quote(String value) {
            StringBuilder result = new StringBuilder("\"");
            for (char character : value.toCharArray()) {
                switch (character) {
                    case '\"' -> result.append("\\\"");
                    case '\\' -> result.append("\\\\");
                    case '\n' -> result.append("\\n");
                    case '\r' -> result.append("\\r");
                    case '\t' -> result.append("\\t");
                    default -> {
                        if (character < 0x20) result.append(String.format("\\u%04x", (int) character));
                        else result.append(character);
                    }
                }
            }
            return result.append('\"').toString();
        }
    }
}

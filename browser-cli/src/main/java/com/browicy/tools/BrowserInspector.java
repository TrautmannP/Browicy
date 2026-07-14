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
import com.browicy.ui.DomViewPanel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
                Map<String, Object> screenshot = options.screenshot() == null
                        ? null : captureScreenshot(session, options);
                report = buildReport(options.url(), started, session,
                        List.copyOf(network), screenshot);
            }
        }
        String json = Json.write(report, options.pretty(), 0) + System.lineSeparator();
        CompatibilityReport.log(castMap(report.get("compatibility")));
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
                                                    List<NetworkRequestEvent> network,
                                                    Map<String, Object> screenshot) {
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
        css.put("backgroundImages", document.getElementsByTagName("*").stream()
                .filter(element -> element.getComputedStyles().containsKey("background-image"))
                .map(element -> Map.of(
                        "tag", element.getTagName(),
                        "id", element.getAttribute("id") == null
                                ? "" : element.getAttribute("id"),
                        "value", element.getComputedStyles().get("background-image")))
                .toList());
        css.put("webFonts", session.fonts().resources().entrySet().stream()
                .map(entry -> Map.of(
                        "family", entry.getKey(),
                        "url", entry.getValue().uri().toString(),
                        "sizeBytes", entry.getValue().sizeBytes()))
                .toList());

        Map<String, Object> renderReport = new LinkedHashMap<>();
        renderReport.put("nodes", render.nodes);
        renderReport.put("blockBoxes", render.blocks);
        renderReport.put("inlineBoxes", render.inlineBoxes);
        renderReport.put("textRuns", render.textRuns);
        renderReport.put("lineBreaks", render.lineBreaks);

        Map<String, Object> javascript = new LinkedHashMap<>();
        javascript.put("console", js.consoleMessages());
        javascript.put("errors", js.errors());

        Map<String, Object> compatibility = CompatibilityReport.build(
                document, session.styleSheets(), js);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 2);
        result.put("generatedAt", Instant.now().toString());
        result.put("durationMs", Duration.between(started, Instant.now()).toMillis());
        result.put("requestedUrl", requestedUrl);
        result.put("page", page);
        result.put("dom", domReport);
        result.put("css", css);
        result.put("renderTree", renderReport);
        result.put("javascript", javascript);
        result.put("compatibility", compatibility);
        result.put("network", network.stream().map(BrowserInspector::networkEvent).toList());
        if (screenshot != null) result.put("screenshot", screenshot);
        result.put("healthy", !document.getUrl().equals("about:error") && js.errors().isEmpty());
        return result;
    }

    private static Map<String, Object> captureScreenshot(PageSession session,
                                                          Options options) throws IOException {
        Path target = options.screenshot().toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);

        BufferedImage image;
        DomViewPanel view = new DomViewPanel(session);
        try {
            image = view.captureScreenshot(
                    options.viewportWidth(), options.viewportHeight(), options.fullPage());
        } finally {
            view.dispose();
        }
        if (!ImageIO.write(image, "png", target.toFile())) {
            throw new IOException("Kein PNG-Encoder verfügbar");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", target.toString());
        result.put("format", "png");
        result.put("width", image.getWidth());
        result.put("height", image.getHeight());
        result.put("viewportWidth", options.viewportWidth());
        result.put("viewportHeight", options.viewportHeight());
        result.put("fullPage", options.fullPage());
        result.put("sizeBytes", Files.size(target));
        result.put("sha256", sha256(target));
        return result;
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 ist nicht verfügbar", impossible);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
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

    static final int DEFAULT_VIEWPORT_WIDTH = 1280;
    static final int DEFAULT_VIEWPORT_HEIGHT = 720;

    record Options(String url,
                   Path output,
                   boolean pretty,
                   Path screenshot,
                   int viewportWidth,
                   int viewportHeight,
                   boolean fullPage) {
        static Options parse(String[] arguments) {
            if (arguments.length == 0 || "--help".equals(arguments[0])) {
                System.out.println("""
                        Usage: java -jar browicy-inspect.jar <url> [options]
                          --output <report.json>   JSON-Bericht schreiben
                          --compact                Kompaktes JSON ausgeben
                          --screenshot <page.png>  Gerenderte Webseite als PNG speichern
                          --viewport <width>x<height>
                                                   Viewport (Standard: 1280x720)
                          --full-page              Gesamte Dokumenthöhe aufnehmen
                        """);
                System.exit(arguments.length == 0 ? 2 : 0);
            }
            Path output = null;
            boolean pretty = true;
            Path screenshot = null;
            int viewportWidth = DEFAULT_VIEWPORT_WIDTH;
            int viewportHeight = DEFAULT_VIEWPORT_HEIGHT;
            boolean fullPage = false;
            for (int index = 1; index < arguments.length; index++) {
                switch (arguments[index]) {
                    case "--output" -> {
                        if (++index >= arguments.length) throw new IllegalArgumentException("--output benötigt einen Pfad");
                        output = Path.of(arguments[index]);
                    }
                    case "--compact" -> pretty = false;
                    case "--screenshot" -> {
                        if (++index >= arguments.length) {
                            throw new IllegalArgumentException("--screenshot benötigt einen PNG-Pfad");
                        }
                        screenshot = Path.of(arguments[index]);
                    }
                    case "--viewport" -> {
                        if (++index >= arguments.length) {
                            throw new IllegalArgumentException("--viewport benötigt WIDTHxHEIGHT");
                        }
                        int[] viewport = parseViewport(arguments[index]);
                        viewportWidth = viewport[0];
                        viewportHeight = viewport[1];
                    }
                    case "--full-page" -> fullPage = true;
                    default -> throw new IllegalArgumentException("Unbekannte Option: " + arguments[index]);
                }
            }
            if (fullPage && screenshot == null) {
                throw new IllegalArgumentException("--full-page erfordert --screenshot");
            }
            if (output != null && screenshot != null
                    && output.toAbsolutePath().normalize()
                    .equals(screenshot.toAbsolutePath().normalize())) {
                throw new IllegalArgumentException(
                        "--output und --screenshot benötigen unterschiedliche Pfade");
            }
            return new Options(arguments[0], output, pretty, screenshot,
                    viewportWidth, viewportHeight, fullPage);
        }

        private static int[] parseViewport(String value) {
            String[] dimensions = value.toLowerCase(java.util.Locale.ROOT).split("x", -1);
            if (dimensions.length != 2) {
                throw invalidViewport(value);
            }
            try {
                int width = Integer.parseInt(dimensions[0]);
                int height = Integer.parseInt(dimensions[1]);
                if (width <= 0 || height <= 0 || width > 16_384 || height > 16_384
                        || (long) width * height > 100_000_000L) {
                    throw invalidViewport(value);
                }
                return new int[]{width, height};
            } catch (NumberFormatException invalidNumber) {
                throw invalidViewport(value);
            }
        }

        private static IllegalArgumentException invalidViewport(String value) {
            return new IllegalArgumentException("Ungültiger Viewport '" + value
                    + "' (erwartet: WIDTHxHEIGHT, maximal 100 Megapixel)");
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

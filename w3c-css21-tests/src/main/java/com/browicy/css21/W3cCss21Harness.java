package com.browicy.css21;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Test-Driven-Development-Harness: rendert jeden Test der W3C CSS2.1-Suite
 * in Chrome (Referenz, „so soll es aussehen") und in Browicy, vergleicht die
 * Screenshots pixelweise und schreibt Berichte plus Diff-Bilder.
 *
 * <p>Steuerung über System-Properties:</p>
 * <ul>
 *   <li>{@code browicy.refreshReferences=true} – Chrome-Referenzen neu
 *       erzeugen/überschreiben (neuer Baseline-/TDD-Zyklus)</li>
 *   <li>{@code browicy.viewport=800x600} – Viewport für beide Browser</li>
 *   <li>{@code browicy.tests=<regex>} – nur Tests mit passendem Pfad laufen
 *       lassen (z. B. {@code abspos/.*} oder ein einzelner Test)</li>
 *   <li>{@code browicy.passRatio=0.0} – maximale Diff-Quote für PASS</li>
 *   <li>{@code browicy.outputDir=target/w3c-css21} – Ausgabeverzeichnis</li>
 * </ul>
 *
 * <p>Artefakte unter {@code outputDir}: {@code references/<test>.png}
 * (Chrome-Sollbilder), {@code comparisons/<test>/{chrome,browicy,diff}.png}
 * sowie {@code latest.json} und {@code latest.html}.</p>
 */
public final class W3cCss21Harness {

    private static final System.Logger LOGGER =
            System.getLogger(W3cCss21Harness.class.getName());

    public enum Status { PASS, DIFF, ERROR, SKIP }

    public record TestResult(String relativePath, Status status, double diffRatio,
                             long differingPixels, long totalPixels,
                             double meanAbsDiff, double maxAbsDiff, String message) {

        /** Für das JUnit-Gate: SKIP ist kein Fehler. */
        public boolean passed() {
            return status == Status.PASS || status == Status.SKIP;
        }
    }

    public record RunResult(List<TestResult> tests, Path outputDirectory,
                            int viewportWidth, int viewportHeight) {

        public long count(Status status) {
            return tests.stream().filter(test -> test.status() == status).count();
        }
    }

    private static final Pattern VIEWPORT = Pattern.compile("(\\d+)x(\\d+)");

    private W3cCss21Harness() {
    }

    public static RunResult run() {
        Config config = Config.fromSystemProperties();
        try {
            return execute(config);
        } catch (RuntimeException setupFailure) {
            // Z. B. Playwright-Browser nicht installiert: als ein einzelner
            // fehlschlagender Test melden statt die JVM abzuschießen.
            TestResult failure = new TestResult("<harness>", Status.ERROR, 0, 0, 0, 0, 0,
                    setupFailure.getMessage());
            return new RunResult(List.of(failure), config.outputDirectory,
                    config.viewportWidth, config.viewportHeight);
        }
    }

    private static RunResult execute(Config config) {
        List<Css21Suite.TestCase> tests = Css21Suite.catalog().stream()
                .filter(test -> config.testsFilter.matcher(test.relativePath()).find())
                .toList();
        if (tests.isEmpty()) {
            throw new IllegalStateException("Kein Test der CSS2.1-Suite passt zum Filter '"
                    + config.testsFilter.pattern() + "' (browicy.tests)");
        }

        Path output = config.outputDirectory.toAbsolutePath().normalize();
        Path references = output.resolve("references");
        Path comparisons = output.resolve("comparisons");
        try {
            Files.createDirectories(references);
            Files.createDirectories(comparisons);
        } catch (IOException failure) {
            throw new IllegalStateException("Ausgabeverzeichnis nicht erstellbar: " + output,
                    failure);
        }

        LOGGER.log(System.Logger.Level.INFO,
                "W3C CSS2.1-Harness: {0} Tests, Viewport {1}x{2}, refreshReferences={3}",
                tests.size(), config.viewportWidth, config.viewportHeight,
                config.refreshReferences);

        List<TestResult> results = new ArrayList<>(tests.size());
        try (Css21TestServer server = Css21TestServer.start();
             ChromeReferenceRenderer chrome = new ChromeReferenceRenderer();
             BrowicyRenderer browicy = new BrowicyRenderer()) {
            for (Css21Suite.TestCase test : tests) {
                results.add(runTest(test, server, chrome, browicy,
                        references, comparisons, config));
            }
        }
        RunResult runResult = new RunResult(List.copyOf(results), output,
                config.viewportWidth, config.viewportHeight);
        writeReports(runResult);
        return runResult;
    }

    private static TestResult runTest(Css21Suite.TestCase test, Css21TestServer server,
                                      ChromeReferenceRenderer chrome,
                                      BrowicyRenderer browicy,
                                      Path references, Path comparisons,
                                      Config config) {
        String path = test.relativePath();
        if (Css21Suite.isSkipped(path)) {
            return new TestResult(path, Status.SKIP, 0, 0, 0, 0, 0, "auf skip.txt");
        }

        Path reference = references.resolve(path.replace('/', '_') + ".png");
        Path artifacts = comparisons.resolve(path.replace('/', '_'));
        try {
            Files.createDirectories(artifacts);
        } catch (IOException failure) {
            return new TestResult(path, Status.ERROR, 0, 0, 0, 0, 0,
                    "Artefaktordner nicht erstellbar: " + failure.getMessage());
        }

        byte[] referenceBytes;
        if (config.refreshReferences || !Files.isRegularFile(reference)) {
            // Fehlende Referenzen werden automatisch erzeugt; refresh=true
            // überschreibt vorhandene (neue Baseline/geänderte Erwartung).
            try {
                referenceBytes = chrome.screenshot(server.url(path),
                        config.viewportWidth, config.viewportHeight);
            } catch (RuntimeException failure) {
                return new TestResult(path, Status.ERROR, 0, 0, 0, 0, 0,
                        "Chrome: " + failure.getMessage());
            }
            try {
                Files.write(reference, referenceBytes);
            } catch (IOException failure) {
                return new TestResult(path, Status.ERROR, 0, 0, 0, 0, 0,
                        "Referenz nicht schreibbar: " + failure.getMessage());
            }
        } else {
            try {
                referenceBytes = Files.readAllBytes(reference);
            } catch (IOException failure) {
                return new TestResult(path, Status.ERROR, 0, 0, 0, 0, 0,
                        "Referenz nicht lesbar: " + failure.getMessage());
            }
        }

        BufferedImage browicyImage;
        try {
            browicyImage = browicy.screenshot(server.url(path),
                    config.viewportWidth, config.viewportHeight);
        } catch (RuntimeException failure) {
            return new TestResult(path, Status.ERROR, 0, 0, 0, 0, 0,
                    "Browicy: " + failure.getMessage());
        }

        BufferedImage referenceImage;
        try {
            referenceImage = ImageIO.read(new java.io.ByteArrayInputStream(referenceBytes));
        } catch (IOException failure) {
            return new TestResult(path, Status.ERROR, 0, 0, 0, 0, 0,
                    "Referenz-PNG nicht dekodierbar: " + failure.getMessage());
        }
        if (referenceImage == null) {
            return new TestResult(path, Status.ERROR, 0, 0, 0, 0, 0,
                    "Referenz-PNG leer");
        }

        PixelComparator.Metrics metrics;
        try {
            metrics = PixelComparator.compare(referenceImage, browicyImage);
        } catch (IllegalArgumentException mismatch) {
            return new TestResult(path, Status.ERROR, 0, 0, 0, 0, 0,
                    mismatch.getMessage());
        }

        writeImages(artifacts, referenceBytes, browicyImage, referenceImage);

        Status status = metrics.diffRatio() <= config.passRatio ? Status.PASS : Status.DIFF;
        String message = status == Status.PASS
                ? "pixelidentisch (diffRatio=" + formatRatio(metrics.diffRatio()) + ")"
                : "diffRatio=" + formatRatio(metrics.diffRatio())
                        + " (mean=" + format(metrics.meanAbsDiff())
                        + ", max=" + format(metrics.maxAbsDiff()) + ")";
        return new TestResult(path, status, metrics.diffRatio(), metrics.differingPixels(),
                metrics.totalPixels(), metrics.meanAbsDiff(), metrics.maxAbsDiff(), message);
    }

    private static void writeImages(Path artifacts, byte[] referenceBytes,
                                    BufferedImage browicyImage,
                                    BufferedImage referenceImage) {
        try {
            Files.write(artifacts.resolve("chrome.png"), referenceBytes);
            ImageIO.write(browicyImage, "png", artifacts.resolve("browicy.png").toFile());
            ImageIO.write(PixelComparator.diffImage(referenceImage, browicyImage), "png",
                    artifacts.resolve("diff.png").toFile());
        } catch (IOException failure) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Diff-Artefakte nicht schreibbar: " + failure.getMessage());
        }
    }

    private static void writeReports(RunResult runResult) {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"summary\": {");
        json.append("\"tests\": ").append(runResult.tests().size());
        for (Status status : Status.values()) {
            json.append(", \"").append(status.name()).append("\": ")
                    .append(runResult.count(status));
        }
        json.append("},\n  \"tests\": [");
        boolean first = true;
        for (TestResult test : runResult.tests()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("\n    {\"path\": \"").append(jsonEscape(test.relativePath()))
                    .append("\", \"status\": \"").append(test.status())
                    .append("\", \"diffRatio\": ").append(test.diffRatio())
                    .append(", \"differingPixels\": ").append(test.differingPixels())
                    .append(", \"totalPixels\": ").append(test.totalPixels())
                    .append(", \"meanAbsDiff\": ").append(test.meanAbsDiff())
                    .append(", \"maxAbsDiff\": ").append(test.maxAbsDiff())
                    .append(", \"message\": \"").append(jsonEscape(test.message()))
                    .append("\"}");
        }
        json.append("\n  ]\n}\n");
        try {
            Files.writeString(runResult.outputDirectory().resolve("latest.json"),
                    json.toString(), StandardCharsets.UTF_8);
            Files.writeString(runResult.outputDirectory().resolve("latest.html"),
                    htmlReport(runResult), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Berichte nicht schreibbar", failure);
        }
    }

    private static String htmlReport(RunResult runResult) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"de\">\n<head>\n<meta charset=\"utf-8\">\n")
                .append("<title>W3C CSS2.1 – Chrome vs. Browicy</title>\n")
                .append("<style>")
                .append("body{font-family:sans-serif;margin:1em}td,th{padding:2px 8px;")
                .append("text-align:left;vertical-align:top;font-size:13px}")
                .append("img{max-width:200px;max-height:150px;border:1px solid #ccc}")
                .append(".PASS{color:green;font-weight:bold}.DIFF{color:#b06a00;font-weight:bold}")
                .append(".ERROR,.NO_REFERENCE{color:red;font-weight:bold}")
                .append(".SKIP{color:#888}table{border-collapse:collapse}")
                .append("th{border-bottom:2px solid #333}")
                .append("</style>\n</head>\n<body>\n");
        html.append("<h1>W3C CSS2.1 – Chrome vs. Browicy</h1>\n");
        html.append("<p>Viewport ").append(runResult.viewportWidth()).append("x")
                .append(runResult.viewportHeight()).append(" · ")
                .append(runResult.tests().size()).append(" Tests · ");
        for (Status status : Status.values()) {
            html.append("<span class=\"").append(status).append("\">")
                    .append(status).append(": ").append(runResult.count(status))
                    .append("</span> · ");
        }
        html.append("</p>\n");
        html.append("<table>\n<tr><th>Status</th><th>Test</th><th>Diff</th>")
                .append("<th>Chrome</th><th>Browicy</th><th>Diff-Bild</th><th>Meldung</th></tr>\n");
        for (TestResult test : runResult.tests()) {
            String base = "comparisons/" + test.relativePath().replace('/', '_');
            html.append("<tr><td class=\"").append(test.status()).append("\">")
                    .append(test.status()).append("</td>")
                    .append("<td>").append(htmlEscape(test.relativePath())).append("</td>")
                    .append("<td>").append(formatRatio(test.diffRatio())).append("</td>")
                    .append("<td><a href=\"").append(base).append("/chrome.png\">")
                    .append("<img src=\"").append(base).append("/chrome.png\" alt=\"chrome\">")
                    .append("</a></td>")
                    .append("<td><a href=\"").append(base).append("/browicy.png\">")
                    .append("<img src=\"").append(base).append("/browicy.png\" alt=\"browicy\">")
                    .append("</a></td>")
                    .append("<td><a href=\"").append(base).append("/diff.png\">")
                    .append("<img src=\"").append(base).append("/diff.png\" alt=\"diff\">")
                    .append("</a></td>")
                    .append("<td>").append(htmlEscape(test.message())).append("</td></tr>\n");
        }
        html.append("</table>\n</body>\n</html>\n");
        return html.toString();
    }

    private static String formatRatio(double ratio) {
        return String.format(java.util.Locale.ROOT, "%.4f%%", ratio * 100);
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String htmlEscape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private record Config(boolean refreshReferences, int viewportWidth, int viewportHeight,
                          Pattern testsFilter, double passRatio, Path outputDirectory) {

        private static Config fromSystemProperties() {
            boolean refresh = Boolean.getBoolean("browicy.refreshReferences");
            int[] viewport = parseViewport(System.getProperty("browicy.viewport", "800x600"));
            Pattern filter = Pattern.compile(System.getProperty("browicy.tests", ".*"));
            double passRatio = Double.parseDouble(System.getProperty("browicy.passRatio", "0.0"));
            Path output = Path.of(System.getProperty("browicy.outputDir", "target/w3c-css21"));
            return new Config(refresh, viewport[0], viewport[1], filter, passRatio, output);
        }

        private static int[] parseViewport(String value) {
            var matcher = VIEWPORT.matcher(value.strip());
            if (!matcher.matches()) {
                throw new IllegalArgumentException(
                        "Ungültiger Viewport '" + value + "' (erwartet WIDTHxHEIGHT)");
            }
            return new int[]{Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2))};
        }
    }
}

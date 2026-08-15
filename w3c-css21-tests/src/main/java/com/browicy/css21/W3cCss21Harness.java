package com.browicy.css21;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

public final class W3cCss21Harness {

    private static final System.Logger LOGGER =
            System.getLogger(W3cCss21Harness.class.getName());

    public enum Status { PASS, DIFF, ERROR, SKIP }

    public record TestResult(String relativePath, Status status, double diffRatio,
                             long differingPixels, long totalPixels,
                             double meanAbsDiff, double maxAbsDiff,
                             int layoutMismatches, double maxLayoutPositionDelta,
                             String message) {
        public boolean passed() {
            return status == Status.PASS || status == Status.SKIP;
        }
    }

    public record RunResult(List<TestResult> tests, Path outputDirectory,
                            int viewportWidth, int viewportHeight) {
    }

    private static final Pattern VIEWPORT = Pattern.compile("(\\d+)x(\\d+)");

    private W3cCss21Harness() {
    }

    public static RunResult run() {
        return execute(Config.fromSystemProperties());
    }

    private static RunResult execute(Config config) {
        Path references = config.outputDirectory().resolve("references");
        Path comparisons = config.outputDirectory().resolve("comparisons");
        try {
            Files.createDirectories(references);
            Files.createDirectories(comparisons);
        } catch (IOException e) {
            throw new IllegalStateException("Ausgabeverzeichnisse anlegen fehlgeschlagen: " + e.getMessage(), e);
        }

        List<Css21Suite.TestCase> tests = new ArrayList<>();
        for (Css21Suite.TestCase test : Css21Suite.catalog()) {
            if (config.testsFilter().matcher(test.relativePath()).matches()) {
                tests.add(test);
            }
        }

        List<TestResult> results = new ArrayList<>();
        try (Css21TestServer server = Css21TestServer.start();
             ChromeReferenceRenderer chrome = new ChromeReferenceRenderer();
             BrowicyRenderer browicy = new BrowicyRenderer()) {

            int done = 0;
            for (Css21Suite.TestCase test : tests) {
                TestResult result = runTest(test, server, chrome, browicy,
                        references, comparisons, config);
                results.add(result);
                done++;
                if (done % 10 == 0 || done == tests.size()) {
                    LOGGER.log(System.Logger.Level.INFO,
                            "Harness-Fortschritt: {0}/{1} ({2}%)",
                            done, tests.size(), Math.round(100.0 * done / tests.size()));
                }
            }
        }

        RunResult runResult = new RunResult(results, config.outputDirectory(),
                config.viewportWidth(), config.viewportHeight());
        writeReports(runResult);
        return runResult;
    }

    private static TestResult runTest(Css21Suite.TestCase test, Css21TestServer server,
                                      ChromeReferenceRenderer chrome,
                                      BrowicyRenderer browicy,
                                      Path references, Path comparisons,
                                      Config config) {
        int width = config.viewportWidth();
        int height = config.viewportHeight();
        Path reference = references.resolve(test.relativePath().replace('/', '_') + ".png");
        Path artifacts = comparisons.resolve(test.relativePath().replace('/', '_'));

        try {
            if (Css21Suite.isSkipped(test.relativePath())) {
                return new TestResult(test.relativePath(), Status.SKIP, 0, 0, 0,
                        0, 0, 0, 0, "Übersprungen (skip.txt)");
            }

            String testUrl = server.url(test.relativePath());
            byte[] referenceBytes;
            List<ElementGeometrySnapshot> chromeGeometry;
            if (config.refreshReferences() || !Files.exists(reference)) {
                referenceBytes = chrome.screenshot(testUrl, width, height);
                chromeGeometry = chrome.extractGeometry(testUrl, width, height);
                Files.createDirectories(artifacts);
                Files.write(reference, referenceBytes);
            } else {
                referenceBytes = Files.readAllBytes(reference);
                chromeGeometry = chrome.extractGeometry(testUrl, width, height);
            }

            BufferedImage referenceImage = ImageIO.read(new java.io.ByteArrayInputStream(referenceBytes));
            BufferedImage browicyImage = browicy.screenshot(testUrl, width, height);
            List<ElementGeometrySnapshot> browicyGeometry =
                    browicy.extractGeometry(testUrl, width, height);

            PixelComparator.Metrics metrics = PixelComparator.compare(referenceImage, browicyImage);
            LayoutTreeComparator.ComparisonResult layoutResult =
                    LayoutTreeComparator.compare(chromeGeometry, browicyGeometry);

            Files.createDirectories(artifacts);
            writeImages(artifacts, referenceBytes, browicyImage, referenceImage);
            Files.writeString(artifacts.resolve("layout-diff.txt"),
                    LayoutTreeComparator.formatTable(layoutResult), StandardCharsets.UTF_8);
            Files.writeString(artifacts.resolve("layout-diff.json"),
                    LayoutTreeComparator.toJson(layoutResult), StandardCharsets.UTF_8);

            double diffRatio = metrics.diffRatio();
            Status status = diffRatio <= config.passRatio() ? Status.PASS : Status.DIFF;
            String message = String.format(java.util.Locale.ROOT,
                    "diffRatio=%.4f%% (mean=%.1f, max=%.1f, layoutDiffs=%d, maxΔPos=%.1fpx)",
                    diffRatio * 100, metrics.meanAbsDiff(), metrics.maxAbsDiff(),
                    layoutResult.mismatchedElements() + layoutResult.missingInActual()
                            + layoutResult.extraInActual(),
                    layoutResult.maxPositionDelta());

            return new TestResult(test.relativePath(), status, diffRatio,
                    metrics.differingPixels(), metrics.totalPixels(),
                    metrics.meanAbsDiff(), metrics.maxAbsDiff(),
                    layoutResult.mismatchedElements() + layoutResult.missingInActual()
                            + layoutResult.extraInActual(),
                    layoutResult.maxPositionDelta(),
                    message);
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Test fehlgeschlagen (ERROR): " + test.relativePath() + " – " + e, e);
            return new TestResult(test.relativePath(), Status.ERROR, 0, 0, 0,
                    0, 0, 0, 0, "Fehler: " + e.getMessage());
        }
    }

    private static void writeImages(Path artifacts, byte[] referenceBytes,
                                    BufferedImage browicyImage,
                                    BufferedImage referenceImage) {
        try {
            Files.write(artifacts.resolve("chrome.png"), referenceBytes);
            ImageIO.write(browicyImage, "png", artifacts.resolve("browicy.png").toFile());
            BufferedImage diffImage = PixelComparator.diffImage(referenceImage, browicyImage);
            ImageIO.write(diffImage, "png", artifacts.resolve("diff.png").toFile());
        } catch (IOException e) {
            throw new IllegalStateException("Bilder schreiben fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private static void writeReports(RunResult runResult) {
        try {
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"viewport\": \"").append(runResult.viewportWidth())
                    .append('x').append(runResult.viewportHeight()).append("\",\n");
            json.append("  \"tests\": [\n");
            for (int i = 0; i < runResult.tests().size(); i++) {
                TestResult result = runResult.tests().get(i);
                json.append("    {\n");
                json.append("      \"path\": \"").append(jsonEscape(result.relativePath())).append("\",\n");
                json.append("      \"status\": \"").append(result.status()).append("\",\n");
                json.append("      \"diffRatio\": ").append(result.diffRatio()).append(",\n");
                json.append("      \"differingPixels\": ").append(result.differingPixels()).append(",\n");
                json.append("      \"totalPixels\": ").append(result.totalPixels()).append(",\n");
                json.append("      \"meanAbsDiff\": ").append(result.meanAbsDiff()).append(",\n");
                json.append("      \"maxAbsDiff\": ").append(result.maxAbsDiff()).append(",\n");
                json.append("      \"layoutMismatches\": ").append(result.layoutMismatches()).append(",\n");
                json.append("      \"maxLayoutPositionDelta\": ").append(result.maxLayoutPositionDelta()).append(",\n");
                json.append("      \"message\": \"").append(jsonEscape(result.message())).append("\"\n");
                json.append("    }").append(i < runResult.tests().size() - 1 ? ",\n" : "\n");
            }
            json.append("  ]\n");
            json.append("}\n");
            Files.writeString(runResult.outputDirectory().resolve("latest.json"),
                    json.toString(), StandardCharsets.UTF_8);
            Files.writeString(runResult.outputDirectory().resolve("latest.html"),
                    htmlReport(runResult), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Berichte schreiben fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private static String htmlReport(RunResult runResult) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"de\">\n<head>\n")
                .append("<meta charset=\"utf-8\">\n")
                .append("<title>W3C CSS2.1 Harness – Chrome vs. Browicy</title>\n")
                .append("<style>\n")
                .append("body{font-family:sans-serif;margin:1rem}\n")
                .append("table{border-collapse:collapse;width:100%}\n")
                .append("th,td{border:1px solid #ccc;padding:4px 8px;text-align:left}\n")
                .append(".PASS{background:#dfd}.DIFF{background:#fdd}.ERROR{background:#fcc}.SKIP{background:#eee}\n")
                .append("img{max-width:220px;border:1px solid #999}\n")
                .append("</style>\n</head>\n<body>\n")
                .append("<h1>W3C CSS2.1 Harness – Chrome vs. Browicy</h1>\n")
                .append("<p>Viewport: ").append(runResult.viewportWidth()).append('x')
                .append(runResult.viewportHeight()).append("</p>\n")
                .append("<table>\n<tr><th>Status</th><th>Test</th><th>Diff-Ratio</th>")
                .append("<th>Layout</th><th>Chrome</th><th>Browicy</th><th>Diff</th></tr>\n");

        for (TestResult result : runResult.tests()) {
            String key = result.relativePath().replace('/', '_');
            String artifactDir = "comparisons/" + key;
            String layoutClass = result.layoutMismatches() == 0 ? "PASS" : "DIFF";
            html.append("<tr class=\"").append(result.status()).append("\">\n")
                    .append("<td>").append(result.status()).append("</td>\n")
                    .append("<td>").append(htmlEscape(result.relativePath())).append("</td>\n")
                    .append("<td>").append(formatRatio(result.diffRatio()))
                    .append("<br><small>mean ").append(format(result.meanAbsDiff()))
                    .append(", max ").append(format(result.maxAbsDiff())).append("</small></td>\n")
                    .append("<td class=\"").append(layoutClass).append("\">")
                    .append(result.layoutMismatches()).append(" Abweichung(en), max ΔPos ")
                    .append(format(result.maxLayoutPositionDelta())).append("px<br>")
                    .append("<a href=\"").append(artifactDir).append("/layout-diff.txt\">Layout-Diff (TXT)</a> · ")
                    .append("<a href=\"").append(artifactDir).append("/layout-diff.json\">JSON</a></td>\n")
                    .append("<td><a href=\"").append(artifactDir).append("/chrome.png\"><img src=\"")
                    .append(artifactDir).append("/chrome.png\" alt=\"Chrome\"></a></td>\n")
                    .append("<td><a href=\"").append(artifactDir).append("/browicy.png\"><img src=\"")
                    .append(artifactDir).append("/browicy.png\" alt=\"Browicy\"></a></td>\n")
                    .append("<td><a href=\"").append(artifactDir).append("/diff.png\"><img src=\"")
                    .append(artifactDir).append("/diff.png\" alt=\"Diff\"></a></td>\n")
                    .append("</tr>\n");
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

        static Config fromSystemProperties() {
            boolean refresh = Boolean.parseBoolean(
                    System.getProperty("browicy.refreshReferences", "false"));
            int width = 800;
            int height = 600;
            String viewport = System.getProperty("browicy.viewport", "800x600");
            var viewportMatcher = VIEWPORT.matcher(viewport);
            if (viewportMatcher.matches()) {
                width = Integer.parseInt(viewportMatcher.group(1));
                height = Integer.parseInt(viewportMatcher.group(2));
            }
            String filter = System.getProperty("browicy.tests", ".*");
            Pattern testsFilter = Pattern.compile(filter);
            double passRatio = Double.parseDouble(
                    System.getProperty("browicy.passRatio", "0.0"));
            Path output = Path.of(System.getProperty("browicy.outputDir",
                    "target/w3c-css21"));
            return new Config(refresh, width, height, testsFilter, passRatio, output);
        }
    }
}

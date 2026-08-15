package com.browicy.css21;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Katalog der W3C CSS2.1-Testsuite.
 *
 * <p>Die Suite ist nicht eingecheckt (55 MB): Sie wird beim ersten Lauf von
 * der gepinnten Upstream-Revision ({@link #UPSTREAM_SHA}) als Zip geladen und
 * nach {@code ~/.browicy/w3c-css21-suite/<sha>/} extrahiert. Danach läuft der
 * Harness offline. Testdateien sind {@code .xht}/{@code .html}/{@code .htm}
 * unterhalb von {@code css21/} (außerhalb von {@code support/}-Verzeichnissen);
 * Namen mit {@code -ref} sind Referenzdateien und keine Tests.</p>
 */
public final class Css21Suite {

    /** Gepinnte Upstream-Revision (w3c/csswg-test, Branch master, Archiv). */
    public static final String UPSTREAM_SHA = "8eced53cb246ba1ab8b9450e36d2d57dc74a1f4a";

    private static final String ARCHIVE_URL =
            "https://github.com/w3c/csswg-test/archive/" + UPSTREAM_SHA + ".zip";
    private static final String ZIP_PREFIX = "csswg-test-" + UPSTREAM_SHA + "/css21/";

    private static final Pattern TEST_FILE = Pattern.compile("(?i).*\\.(xht|html|htm)");
    private static final Pattern REFERENCE_FILE = Pattern.compile(
            "(?i).*-ref(?:-[a-z0-9]+)?\\.(xht|html|htm)");

    private static volatile Path suiteRoot;
    private static volatile List<Pattern> skipPatterns;

    private Css21Suite() {
    }

    public record TestCase(String relativePath) {

        /** Pfad mit Schrägstrichen, dient als stabiler Testname. */
        public String name() {
            return relativePath;
        }
    }

    /** Alle Testdateien der Suite, lexikographisch sortiert nach relativem Pfad. */
    public static List<TestCase> catalog() {
        Path root = suiteRoot();
        List<TestCase> tests = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> TEST_FILE.matcher(path.getFileName().toString()).matches())
                    .filter(path -> !REFERENCE_FILE.matcher(path.getFileName().toString()).matches())
                    .filter(path -> !relativePath(root, path).contains("/support/"))
                    .forEach(path -> tests.add(new TestCase(relativePath(root, path))));
        } catch (IOException failure) {
            throw new IllegalStateException("CSS2.1-Testsuite unter " + root + " nicht lesbar", failure);
        }
        tests.sort(Comparator.comparing(TestCase::relativePath));
        return List.copyOf(tests);
    }

    /** Liegt der Test auf der Skip-Liste ({@code /w3c-css21/skip.txt})? */
    public static boolean isSkipped(String relativePath) {
        List<Pattern> patterns = skipPatterns();
        if (patterns.isEmpty()) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> pattern.matcher(relativePath).find());
    }

    /**
     * Wurzelverzeichnis der Suite; lädt und extrahiert sie bei Bedarf
     * (einmalig, Netzwerk für den ersten Lauf erforderlich).
     */
    public static Path suiteRoot() {
        Path cached = suiteRoot;
        if (cached == null) {
            synchronized (Css21Suite.class) {
                cached = suiteRoot;
                if (cached == null) {
                    cached = loadSuite();
                    suiteRoot = cached;
                }
            }
        }
        return cached;
    }

    private static Path loadSuite() {
        Path cache = Path.of(System.getProperty("user.home"), ".browicy",
                "w3c-css21-suite", UPSTREAM_SHA);
        Path suite = cache.resolve("css21");
        Path marker = cache.resolve(".extracted");
        if (Files.isDirectory(suite) && Files.isRegularFile(marker)) {
            return suite;
        }
        Path zip = cache.resolve("suite.zip");
        try {
            Files.createDirectories(cache);
            if (!Files.isRegularFile(zip) || Files.size(zip) == 0) {
                downloadArchive(zip);
            }
            extractSuite(zip, cache);
            Files.writeString(marker, UPSTREAM_SHA, StandardCharsets.UTF_8);
            return suite;
        } catch (IOException | InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CSS2.1-Testsuite konnte nicht geladen werden ("
                    + failure.getMessage() + "). Der erste Lauf benötigt Netzwerk: lädt "
                    + ARCHIVE_URL + " nach " + cache
                    + ". Bei wiederholten Fehlern den Cache-Ordner löschen.", failure);
        }
    }

    private static void downloadArchive(Path target) throws IOException, InterruptedException {
        System.Logger logger = System.getLogger(Css21Suite.class.getName());
        logger.log(System.Logger.Level.INFO,
                "Lade CSS2.1-Testsuite (gepinnt auf {0}) – einmalig, Cache: {1}",
                UPSTREAM_SHA, target.getParent());
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(ARCHIVE_URL))
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();
        HttpResponse<Path> response = client.send(request,
                HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() != 200) {
            Files.deleteIfExists(target);
            throw new IOException("HTTP " + response.statusCode() + " für " + ARCHIVE_URL);
        }
    }

    private static void extractSuite(Path zip, Path cache) throws IOException {
        Path suite = cache.resolve("css21");
        if (Files.exists(suite)) {
            deleteRecursively(suite);
        }
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.getName().startsWith(ZIP_PREFIX)) {
                    continue;
                }
                String relative = entry.getName().substring(ZIP_PREFIX.length());
                if (relative.isEmpty()) {
                    continue;
                }
                Path target = cache.resolve("css21").resolve(relative).normalize();
                if (!target.startsWith(cache)) {
                    throw new IOException("Unerwarteter Zip-Eintrag: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(input, target,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException failure) {
            // Beschädigter/abgebrochener Download: Cache zurücksetzen.
            deleteRecursively(suite);
            Files.deleteIfExists(zip);
            throw failure;
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Einzelne Dateien können hängen bleiben; der Marker
                    // verhindert, dass der Cache als vollständig gilt.
                }
            });
        }
    }

    private static List<Pattern> skipPatterns() {
        List<Pattern> cached = skipPatterns;
        if (cached != null) {
            return cached;
        }
        List<Pattern> patterns = new ArrayList<>();
        try (InputStream input = Css21Suite.class.getResourceAsStream("/w3c-css21/skip.txt")) {
            if (input != null) {
                for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8)
                        .split("\\R")) {
                    String entry = line.strip();
                    if (entry.isEmpty() || entry.startsWith("#")) {
                        continue;
                    }
                    patterns.add(Pattern.compile(entry));
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("skip.txt nicht lesbar", failure);
        }
        skipPatterns = List.copyOf(patterns);
        return skipPatterns;
    }

    private static String relativePath(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}

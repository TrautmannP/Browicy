package com.browicy.engine;

import com.browicy.engine.dom.Document;
import com.browicy.engine.net.LocalTestServer;
import com.browicy.engine.net.PageLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Performance-Regressionstests für Netzwerkstack und Parser. Die Messwerte
 * werden ausgegeben, damit sich Entwicklungen über die Zeit vergleichen
 * lassen; die Grenzwerte sind bewusst großzügig gewählt, damit der Test nur
 * bei echten Regressionen (Größenordnungen) fehlschlägt, nicht bei
 * Lastschwankungen der Maschine.
 */
public class EnginePerformanceTest {

    private static final int WARMUP_ROUNDS = 5;
    private static final int MEASURED_ROUNDS = 30;

    /** ~100 KB HTML mit vielen Blöcken, ähnlich einer echten Textseite. */
    private static final String LARGE_HTML = buildLargeHtml(1500);

    private LocalTestServer server;

    @Before
    public void startServer() throws IOException {
        server = new LocalTestServer();
        server.serveHtml("/seite", LARGE_HTML);
    }

    @After
    public void stopServer() {
        server.close();
    }

    @Test
    public void loadsPageOverLocalNetworkWithinBudget() throws IOException {
        PageLoader loader = new PageLoader();
        String url = server.url("/seite");

        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            loader.load(url);
        }

        long[] durationsMicros = new long[MEASURED_ROUNDS];
        for (int i = 0; i < MEASURED_ROUNDS; i++) {
            long start = System.nanoTime();
            PageLoader.Page page = loader.load(url);
            durationsMicros[i] = (System.nanoTime() - start) / 1_000;
            assertEquals(200, page.statusCode());
        }

        report("Netzwerk-Laden (lokal, " + LARGE_HTML.length() / 1024 + " KB)", durationsMicros);
        assertTrue("Durchschnittliche Ladezeit über 250 ms — Netzwerk-Regression?",
                average(durationsMicros) < 250_000);
    }

    @Test
    public void loadsAndParsesFullPipelineWithinBudget() {
        BrowicyEngine engine = new BrowicyEngine();
        String url = server.url("/seite");

        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            engine.loadPage(url);
        }

        long[] durationsMicros = new long[MEASURED_ROUNDS];
        for (int i = 0; i < MEASURED_ROUNDS; i++) {
            long start = System.nanoTime();
            Document document = engine.loadPage(url);
            durationsMicros[i] = (System.nanoTime() - start) / 1_000;
            assertEquals("Performance-Seite", document.getTitle());
        }

        report("Pipeline Laden+Parsen (lokal)", durationsMicros);
        assertTrue("Durchschnittliche Lade-und-Parse-Zeit über 400 ms — Regression?",
                average(durationsMicros) < 400_000);
    }

    @Test
    public void parsesLargeDocumentWithinBudget() {
        BrowicyEngine engine = new BrowicyEngine();

        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            engine.parseHtml(LARGE_HTML, "about:perf");
        }

        long[] durationsMicros = new long[MEASURED_ROUNDS];
        for (int i = 0; i < MEASURED_ROUNDS; i++) {
            long start = System.nanoTime();
            Document document = engine.parseHtml(LARGE_HTML, "about:perf");
            durationsMicros[i] = (System.nanoTime() - start) / 1_000;
            assertEquals("Performance-Seite", document.getTitle());
        }

        report("Parser (" + LARGE_HTML.length() / 1024 + " KB HTML)", durationsMicros);
        assertTrue("Durchschnittliche Parse-Zeit über 200 ms — Parser-Regression?",
                average(durationsMicros) < 200_000);
    }

    private static String buildLargeHtml(int paragraphs) {
        StringBuilder html = new StringBuilder("""
                <!DOCTYPE html>
                <html>
                <head><title>Performance-Seite</title></head>
                <body>
                <h1>Messseite f&uuml;r den Browicy-Netzwerkstack</h1>
                """);
        for (int i = 0; i < paragraphs; i++) {
            html.append("<p>Absatz ").append(i)
                    .append(": Lorem ipsum dolor sit amet, <b>consetetur</b> sadipscing elitr &ndash; ")
                    .append("sed diam nonumy eirmod tempor invidunt ut labore.</p>\n");
        }
        return html.append("</body></html>").toString();
    }

    private static void report(String label, long[] durationsMicros) {
        long[] sorted = durationsMicros.clone();
        Arrays.sort(sorted);
        System.out.printf("[Perf] %-45s avg=%6.2f ms  median=%6.2f ms  max=%6.2f ms  (n=%d)%n",
                label,
                average(durationsMicros) / 1000.0,
                sorted[sorted.length / 2] / 1000.0,
                sorted[sorted.length - 1] / 1000.0,
                durationsMicros.length);
    }

    private static double average(long[] values) {
        return Arrays.stream(values).average().orElse(0);
    }
}

package com.browicy.engine;

import com.browicy.engine.js.JavaScriptSource;
import com.browicy.engine.net.LocalTestServer;
import com.browicy.engine.net.NetworkRequestEvent;
import com.browicy.engine.net.NetworkResourceType;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExternalStyleSheetEventLoopTest {

    private static final int GIANT_CSS_RULES = 250_000;

    private LocalTestServer server;
    private final BrowicyEngine engine = new BrowicyEngine();
    private final List<NetworkRequestEvent> events = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() throws IOException {
        server = new LocalTestServer();
        engine.addRequestObserver(events::add);
    }

    @After
    public void tearDown() {
        engine.close();
        server.close();
    }

    @Test
    public void eventLoopStaysResponsiveWhileAGiantStylesheetIsProcessed() throws Exception {
        String css = giantCss(GIANT_CSS_RULES);
        server.serveText("/giant.css", "text/css; charset=utf-8", css);
        server.serveHtml("/", """
                <html><head><title>Stylesheets</title></head><body>
                  <link rel="stylesheet" href="/giant.css">
                </body></html>
                """);

        try (PageSession session = engine.loadPageSession(
                server.url("/"), PageUpdateListener.NO_OP)) {
            assertTrue("Giant-Stylesheet wurde nicht geladen",
                    awaitStylesheetLoaded(5, TimeUnit.SECONDS));

            long firstProbe = probeEventLoop(session);
            assertTrue("Event-Loop blockierte während der Stylesheet-Verarbeitung ("
                    + firstProbe + " ns)", firstProbe < 500_000_000L);

            Thread.sleep(150);
            long secondProbe = probeEventLoop(session);
            assertTrue("Event-Loop blockierte während der Stylesheet-Verarbeitung ("
                    + secondProbe + " ns)", secondProbe < 500_000_000L);

            session.awaitResources();
            assertTrue("Erwartete mindestens " + GIANT_CSS_RULES + " akzeptierte Regeln, "
                            + "erhalten: " + session.styleSheets().rules().size(),
                    session.styleSheets().rules().size() >= GIANT_CSS_RULES);
        }
    }

    private static long probeEventLoop(PageSession session) {
        long start = System.nanoTime();
        var result = session.runtime().execute(
                new JavaScriptSource("1 + 1", null, "event-loop-probe.js"));
        long elapsed = System.nanoTime() - start;
        assertFalse("Probe-Skript schlug fehl: " + result.errors(), result.hasErrors());
        return elapsed;
    }

    private boolean awaitStylesheetLoaded(long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            for (NetworkRequestEvent event : events) {
                if (event instanceof NetworkRequestEvent.Loaded loaded
                        && loaded.resourceType() == NetworkResourceType.STYLESHEET
                        && loaded.finalUri().getPath().endsWith("/giant.css")) {
                    return true;
                }
            }
            Thread.sleep(5);
        }
        return false;
    }

    private static String giantCss(int ruleCount) {
        StringBuilder css = new StringBuilder(ruleCount * 48);
        for (int index = 0; index < ruleCount; index++) {
            css.append(".cls").append(index % 1_000).append(" { color:#")
                    .append(Integer.toHexString(0x100000 + (index % 0xFFFFFF)))
                    .append("; background-image:url(../images/bg").append(index)
                    .append(".png); margin:4px; }\n");
        }
        return css.toString();
    }
}

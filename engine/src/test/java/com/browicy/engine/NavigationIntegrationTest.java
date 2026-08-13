package com.browicy.engine;

import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Event;
import com.browicy.engine.net.LocalTestServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * P1: JavaScript-Navigation (location.replace/assign/href, window.open) lädt
 * die Ziel-URL in derselben Session nach und führt deren Skripte aus.
 */
public class NavigationIntegrationTest {

    private LocalTestServer server;
    private final BrowicyEngine engine = new BrowicyEngine();

    @Before
    public void setUp() throws IOException {
        server = new LocalTestServer();
    }

    @After
    public void tearDown() {
        engine.close();
        server.close();
    }

    @Test
    public void locationReplaceNavigatesSessionAndRunsTargetScripts() throws Exception {
        server.serveHtml("/start", """
                <html><body><p id="status">start</p>
                  <script>location.replace('/ziel');</script>
                </body></html>
                """);
        server.serveHtml("/ziel", """
                <html><head><title>Vor Skript</title></head><body>
                  <script>document.title = 'Ziel nach Skript';</script>
                </body></html>
                """);

        try (PageSession session = engine.loadPageSession(
                server.url("/start"), PageUpdateListener.NO_OP)) {
            session.awaitResources();

            assertEquals(server.url("/ziel"), session.document().getUrl());
            assertEquals("Ziel nach Skript", session.document().getTitle());
        }
    }

    @Test
    public void locationAssignAndHrefSetterNavigateToResolvedUrls() throws Exception {
        server.serveHtml("/a", """
                <html><body><script>location.assign('/b');</script></body></html>
                """);
        server.serveHtml("/b", """
                <html><body><script>location.href = '/c';</script></body></html>
                """);
        server.serveHtml("/c", """
                <html><head><title>Ende</title></head><body>c</body></html>
                """);

        try (PageSession session = engine.loadPageSession(
                server.url("/a"), PageUpdateListener.NO_OP)) {
            session.awaitResources();

            assertEquals(server.url("/c"), session.document().getUrl());
            assertEquals("Ende", session.document().getTitle());
        }
    }

    @Test
    public void clickOnConsentButtonNavigatesLikeTheGolemFlow() throws Exception {
        server.serveHtml("/wahl", """
                <html><body>
                  <button id="zustimmen">Zustimmen</button>
                  <script>
                    document.getElementById('zustimmen').addEventListener('click', function() {
                      location.replace('/frei');
                    });
                  </script>
                </body></html>
                """);
        server.serveHtml("/frei", """
                <html><head><title>Freigeschaltet</title></head><body>frei</body></html>
                """);

        try (PageSession session = engine.loadPageSession(
                server.url("/wahl"), PageUpdateListener.NO_OP)) {
            session.awaitResources();
            long generation = session.navigationGeneration();
            Element button = session.document().querySelector("#zustimmen");
            session.runtime().submitEvent(button, new Event("click", true, true)).join();
            assertTrue("Navigation nach Klick erwartet",
                    session.awaitNavigation(generation, 5_000));

            assertEquals(server.url("/frei"), session.document().getUrl());
            assertEquals("Freigeschaltet", session.document().getTitle());
        }
    }
}

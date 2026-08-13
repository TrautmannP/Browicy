package com.browicy.engine.net;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * P3: Ein hängender fetch/XHR im SubResourceLoader-Pfad scheitert innerhalb
 * des konfigurierten Zeitlimits statt offen zu bleiben.
 */
public class SubResourceLoaderTimeoutTest {

    private LocalTestServer server;
    private SubResourceLoader loader;

    @Before
    public void setUp() throws IOException {
        server = new LocalTestServer();
        loader = new SubResourceLoader();
    }

    @After
    public void tearDown() {
        loader.close();
        server.close();
    }

    @Test
    public void hungFetchFailsWithinConfiguredTimeout() throws Exception {
        server.on("/haengt", exchange -> {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        loader.setFetchTimeoutMillisForTesting(300);

        FetchResourceLoad load = loader.fetchAsync(URI.create(server.url("/haengt")));
        try {
            load.future().get(5, TimeUnit.SECONDS);
            fail("Hängender Fetch hätte fehlschlagen müssen");
        } catch (ExecutionException expected) {
            assertTrue(String.valueOf(expected.getCause()),
                    expected.getCause() instanceof IOException);
        }
    }

    @Test
    public void fastResponsesAreUnaffectedBySmallTimeout() throws Exception {
        server.serveText("/schnell.txt", "text/plain; charset=utf-8", "ok");
        loader.setFetchTimeoutMillisForTesting(300);

        FetchResource resource = loader.fetchAsync(
                URI.create(server.url("/schnell.txt"))).await();

        assertEquals("ok", resource.bodyText());
    }
}

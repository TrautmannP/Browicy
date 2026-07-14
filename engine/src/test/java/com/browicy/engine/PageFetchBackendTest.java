package com.browicy.engine;

import com.browicy.engine.js.JsCookieStore;
import com.browicy.engine.js.JsFetchResponse;
import com.browicy.engine.net.LocalTestServer;
import com.browicy.engine.net.SubResourceLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PageFetchBackendTest {

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

    private String serverOriginUrl(String path) {
        return server.url(path);
    }

    @Test
    public void normalizesOrigins() {
        assertEquals("https://beispiel.de", PageFetchBackend.originOf("https://Beispiel.DE/pfad?x=1"));
        assertEquals("https://beispiel.de", PageFetchBackend.originOf("https://beispiel.de:443/"));
        assertEquals("http://beispiel.de", PageFetchBackend.originOf("http://beispiel.de:80/"));
        assertEquals("http://beispiel.de:8080", PageFetchBackend.originOf("http://beispiel.de:8080/"));
        assertNull(PageFetchBackend.originOf("about:blank"));
        assertNull(PageFetchBackend.originOf("file:///tmp/x.html"));
        assertNull(PageFetchBackend.originOf(null));
        assertNull(PageFetchBackend.originOf("kein url"));
    }

    @Test
    public void sameOriginRequestsNeedNoCorsHeader() throws Exception {
        server.serveText("/daten.txt", "text/plain; charset=utf-8", "lokal");
        PageFetchBackend backend = new PageFetchBackend(loader, serverOriginUrl("/index.html"));

        JsFetchResponse response = backend.fetch(
                URI.create(serverOriginUrl("/daten.txt"))).get(5, TimeUnit.SECONDS);

        assertEquals(200, response.status());
        assertEquals("lokal", response.bodyText());
    }

    @Test
    public void crossOriginWithoutAllowHeaderIsBlocked() {
        server.serveText("/geheim.txt", "text/plain", "intern");
        PageFetchBackend backend = new PageFetchBackend(loader, "https://andere.example/seite.html");

        CompletionException failure = assertThrows(CompletionException.class,
                () -> backend.fetch(URI.create(serverOriginUrl("/geheim.txt"))).join());

        assertTrue(String.valueOf(failure.getCause()),
                failure.getCause() instanceof PageFetchBackend.CorsDeniedException);
    }

    @Test
    public void crossOriginWithWildcardAllowHeaderPasses() throws Exception {
        server.on("/offen.txt", exchange -> {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            LocalTestServer.respond(exchange, 200, "text/plain",
                    "öffentlich".getBytes(StandardCharsets.UTF_8));
        });
        PageFetchBackend backend = new PageFetchBackend(loader, "https://andere.example/seite.html");

        JsFetchResponse response = backend.fetch(
                URI.create(serverOriginUrl("/offen.txt"))).get(5, TimeUnit.SECONDS);

        assertEquals("öffentlich", response.bodyText());
    }

    @Test
    public void crossOriginWithMatchingAllowHeaderPasses() throws Exception {
        server.on("/exakt.txt", exchange -> {
            exchange.getResponseHeaders().set(
                    "Access-Control-Allow-Origin", "https://andere.example");
            LocalTestServer.respond(exchange, 200, "text/plain",
                    "erlaubt".getBytes(StandardCharsets.UTF_8));
        });
        PageFetchBackend backend = new PageFetchBackend(loader, "https://andere.example/seite.html");

        JsFetchResponse response = backend.fetch(
                URI.create(serverOriginUrl("/exakt.txt"))).get(5, TimeUnit.SECONDS);

        assertEquals("erlaubt", response.bodyText());
    }

    @Test
    public void crossOriginWithForeignAllowHeaderIsBlocked() {
        server.on("/fremd.txt", exchange -> {
            exchange.getResponseHeaders().set(
                    "Access-Control-Allow-Origin", "https://dritte.example");
            LocalTestServer.respond(exchange, 200, "text/plain", new byte[0]);
        });
        PageFetchBackend backend = new PageFetchBackend(loader, "https://andere.example/seite.html");

        CompletionException failure = assertThrows(CompletionException.class,
                () -> backend.fetch(URI.create(serverOriginUrl("/fremd.txt"))).join());

        assertTrue(failure.getCause() instanceof PageFetchBackend.CorsDeniedException);
    }

    @Test
    public void setCookieHeadersAreNotExposedToScripts() throws Exception {
        server.on("/cookies.txt", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "sitzung=geheim");
            exchange.getResponseHeaders().add("X-Sichtbar", "ja");
            LocalTestServer.respond(exchange, 200, "text/plain", new byte[0]);
        });
        PageFetchBackend backend = new PageFetchBackend(loader, serverOriginUrl("/index.html"));

        JsFetchResponse response = backend.fetch(
                URI.create(serverOriginUrl("/cookies.txt"))).get(5, TimeUnit.SECONDS);

        assertTrue(response.headers().stream()
                .anyMatch(header -> header.name().equals("x-sichtbar")));
        assertFalse(response.headers().stream()
                .anyMatch(header -> header.name().contains("cookie")));
    }

    @Test
    public void setCookieHeadersLandInTheCookieStore() throws Exception {
        server.on("/setzt-cookies", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "sitzung=abc; Path=/");
            exchange.getResponseHeaders().add("Set-Cookie", "geheim=intern; Path=/; HttpOnly");
            LocalTestServer.respond(exchange, 200, "text/plain", new byte[0]);
        });
        JsCookieStore cookies = new JsCookieStore();
        PageFetchBackend backend = new PageFetchBackend(
                loader, serverOriginUrl("/index.html"), cookies);

        JsFetchResponse response = backend.fetch(
                URI.create(serverOriginUrl("/setzt-cookies"))).get(5, TimeUnit.SECONDS);

        assertFalse(response.headers().stream()
                .anyMatch(header -> header.name().contains("cookie")));
        assertEquals("sitzung=abc",
                cookies.cookiesForScript(URI.create(serverOriginUrl("/index.html"))));
    }

    @Test
    public void deniedCrossOriginResponsesStoreNoCookies() {
        server.on("/fremde-cookies", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "fremd=wert; Path=/");
            LocalTestServer.respond(exchange, 200, "text/plain", new byte[0]);
        });
        JsCookieStore cookies = new JsCookieStore();
        PageFetchBackend backend = new PageFetchBackend(
                loader, "https://andere.example/seite.html", cookies);

        assertThrows(CompletionException.class,
                () -> backend.fetch(URI.create(serverOriginUrl("/fremde-cookies"))).join());

        assertEquals("", cookies.cookiesForScript(
                URI.create(serverOriginUrl("/index.html"))));
    }

    @Test
    public void cancelRejectsNewRequestsAndCancelsOutstandingOnes() throws Exception {
        CountDownLatch requestArrived = new CountDownLatch(1);
        server.on("/haengt", exchange -> {
            requestArrived.countDown();
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            LocalTestServer.respond(exchange, 200, "text/plain", new byte[0]);
        });
        PageFetchBackend backend = new PageFetchBackend(loader, serverOriginUrl("/index.html"));

        CompletableFuture<JsFetchResponse> outstanding =
                backend.fetch(URI.create(serverOriginUrl("/haengt")));
        assertTrue(requestArrived.await(5, TimeUnit.SECONDS));

        assertTrue(backend.cancel());
        assertTrue(outstanding.isCancelled() || outstanding.isCompletedExceptionally());

        CompletableFuture<JsFetchResponse> afterClose =
                backend.fetch(URI.create(serverOriginUrl("/haengt")));
        assertTrue(afterClose.isCompletedExceptionally());
    }
}

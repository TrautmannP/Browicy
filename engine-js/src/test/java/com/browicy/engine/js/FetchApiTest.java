package com.browicy.engine.js;

import com.browicy.engine.dom.Document;
import com.browicy.engine.html.HtmlParser;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FetchApiTest {

    private static final String DOCUMENT_URL = "https://app.example/pfad/seite.html";

    private final HtmlParser parser = new HtmlParser();
    private final JavaScriptEngine engine = new JavaScriptEngine();

    @Test
    public void fetchResolvesWithBodyTextOnTheEventLoop() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.completing(uri ->
                response(uri, 200, "OK", "Hallo Fetch"));

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            JsExecutionResult result = runtime.execute(new JavaScriptSource("""
                    fetch('https://daten.example/gruss.txt')
                      .then(r => r.text())
                      .then(text => {
                        document.getElementById('out').textContent = text;
                      });
                    """, null, "fetch-text.js"));
            assertFalse(String.valueOf(result.errors()), result.hasErrors());
            runtime.awaitIdle();
        }

        assertEquals("Hallo Fetch", output(document));
    }

    @Test
    public void responseExposesStatusUrlAndJson() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.completing(uri ->
                response(uri, 200, "OK", "{\"wert\":42}"));

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            runtime.execute(new JavaScriptSource("""
                    fetch('https://daten.example/info.json').then(async r => {
                      const daten = await r.json();
                      document.getElementById('out').textContent = [
                        r.ok, r.status, r.statusText, r.url, r.redirected, daten.wert
                      ].join('|');
                    });
                    """, null, "fetch-json.js"));
            runtime.awaitIdle();
        }

        assertEquals("true|200|OK|https://daten.example/info.json|false|42", output(document));
    }

    @Test
    public void nonSuccessfulResponsesResolveWithOkFalse() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.completing(uri ->
                response(uri, 404, "Not Found", "weg"));

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            runtime.execute(new JavaScriptSource("""
                    fetch('https://daten.example/fehlt')
                      .then(r => r.text().then(text => {
                        document.getElementById('out').textContent =
                            r.ok + '|' + r.status + '|' + text;
                      }));
                    """, null, "fetch-404.js"));
            runtime.awaitIdle();
        }

        assertEquals("false|404|weg", output(document));
    }

    @Test
    public void headersAreCaseInsensitiveAndCombineDuplicates() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.completing(uri ->
                new JsFetchResponse(uri.toString(), 200, "OK", List.of(
                        new JsFetchResponse.Header("Content-Type", "text/plain"),
                        new JsFetchResponse.Header("x-mehrfach", "eins"),
                        new JsFetchResponse.Header("X-Mehrfach", "zwei")), ""));

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            runtime.execute(new JavaScriptSource("""
                    fetch('https://daten.example/h').then(r => {
                      document.getElementById('out').textContent = [
                        r.headers.get('CONTENT-TYPE'),
                        r.headers.has('x-mehrfach'),
                        r.headers.get('X-MEHRFACH'),
                        String(r.headers.get('fehlt'))
                      ].join('|');
                    });
                    """, null, "fetch-headers.js"));
            runtime.awaitIdle();
        }

        assertEquals("text/plain|true|eins, zwei|null", output(document));
    }

    @Test
    public void networkFailureRejectsWithTypeError() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.failing(
                new IOException("Verbindung fehlgeschlagen"));

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            JsExecutionResult result = runtime.execute(new JavaScriptSource("""
                    fetch('https://kaputt.example/')
                      .then(() => { document.getElementById('out').textContent = 'aufgelöst'; })
                      .catch(error => {
                        document.getElementById('out').textContent =
                            (error instanceof TypeError) + '|' + error.message;
                      });
                    """, null, "fetch-fehler.js"));
            assertFalse(String.valueOf(result.errors()), result.hasErrors());
            runtime.awaitIdle();
        }

        assertEquals("true|Verbindung fehlgeschlagen", output(document));
    }

    @Test
    public void relativeUrlsResolveAgainstTheDocumentUrl() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.completing(uri ->
                response(uri, 200, "OK", ""));

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            runtime.execute(new JavaScriptSource("""
                    fetch('daten/info.json');
                    fetch('/wurzel.json');
                    fetch('https://andere.example/extern');
                    """, null, "fetch-relativ.js"));
            runtime.awaitIdle();
        }

        assertEquals(List.of(
                URI.create("https://app.example/pfad/daten/info.json"),
                URI.create("https://app.example/wurzel.json"),
                URI.create("https://andere.example/extern")), backend.requests);
    }

    @Test
    public void unsupportedSchemesRejectWithoutReachingTheBackend() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.completing(uri ->
                response(uri, 200, "OK", ""));

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            runtime.execute(new JavaScriptSource("""
                    fetch('ftp://server.example/datei').catch(error => {
                      document.getElementById('out').textContent =
                          (error instanceof TypeError) + '|' + error.message;
                    });
                    """, null, "fetch-schema.js"));
            runtime.awaitIdle();
        }

        assertTrue(output(document), output(document).startsWith("true|fetch: "));
        assertTrue(backend.requests.isEmpty());
    }

    @Test
    public void postForwardsMethodHeadersAndUtf8Body() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.completingRequest(request ->
                response(request.uri(), 201, "Created", "gespeichert"));

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            runtime.execute(new JavaScriptSource("""
                    fetch('/api/eintrag', {
                      method: 'post',
                      headers: {
                        'Content-Type': 'application/json; charset=utf-8',
                        'X-Anfrage': 'ja',
                        'Content-Length': '999'
                      },
                      body: '{"name":"Grüße"}'
                    }).then(r => r.text()).then(text => {
                      document.getElementById('out').textContent = text;
                    });
                    """, null, "fetch-post.js"));
            runtime.awaitIdle();
        }

        assertEquals("gespeichert", output(document));
        JsFetchRequest request = backend.requestDetails.getFirst();
        assertEquals("POST", request.method());
        assertEquals(URI.create("https://app.example/api/eintrag"), request.uri());
        assertEquals("{\"name\":\"Grüße\"}",
                new String(request.body(), StandardCharsets.UTF_8));
        assertTrue(request.headers().stream().anyMatch(header ->
                header.name().equals("content-type")
                        && header.value().equals("application/json; charset=utf-8")));
        assertTrue(request.headers().stream().anyMatch(header ->
                header.name().equals("x-anfrage") && header.value().equals("ja")));
        assertFalse(request.headers().stream().anyMatch(header ->
                header.name().equals("content-length")));
    }

    @Test
    public void stringBodyGetsDefaultContentType() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.completingRequest(request ->
                response(request.uri(), 200, "OK", ""));

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            runtime.execute(new JavaScriptSource("""
                    fetch('/api/text', { method: 'PUT', body: 'nutzlast' });
                    """, null, "fetch-standard-content-type.js"));
            runtime.awaitIdle();
        }

        JsFetchRequest request = backend.requestDetails.getFirst();
        assertEquals("PUT", request.method());
        assertTrue(request.headers().stream().anyMatch(header ->
                header.name().equals("content-type")
                        && header.value().equals("text/plain;charset=UTF-8")));
    }

    @Test
    public void getBodiesAndForbiddenMethodsRejectBeforeTheBackend() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.completing(uri ->
                response(uri, 200, "OK", ""));

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            runtime.execute(new JavaScriptSource("""
                    Promise.allSettled([
                      fetch('https://daten.example/', { body: 'nutzlast' }),
                      fetch('https://daten.example/', { method: 'TRACE' })
                    ]).then(results => {
                      document.getElementById('out').textContent = results.map(result =>
                          result.status + ':' + (result.reason instanceof TypeError)).join('|');
                    });
                    """, null, "fetch-validierung.js"));
            runtime.awaitIdle();
        }

        assertEquals("rejected:true|rejected:true", output(document));
        assertTrue(backend.requests.isEmpty());
    }

    @Test
    public void fetchIsUndefinedWithoutBackend() {
        Document document = parse();

        try (PageRuntime runtime = engine.createPageRuntime(document)) {
            runtime.execute(new JavaScriptSource("""
                    document.getElementById('out').textContent = typeof fetch;
                    """, null, "fetch-undef.js"));
            runtime.awaitIdle();
        }

        assertEquals("undefined", output(document));
    }

    @Test
    public void secondBodyReadRejectsAndMarksBodyUsed() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.completing(uri ->
                response(uri, 200, "OK", "einmal"));

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            runtime.execute(new JavaScriptSource("""
                    fetch('https://daten.example/').then(async r => {
                      const erste = await r.text();
                      let zweite = 'kein-fehler';
                      try { await r.text(); }
                      catch (error) {
                        zweite = error instanceof TypeError ? 'typeerror' : 'anderer';
                      }
                      document.getElementById('out').textContent =
                          erste + '|' + zweite + '|' + r.bodyUsed;
                    });
                    """, null, "fetch-bodyused.js"));
            runtime.awaitIdle();
        }

        assertEquals("einmal|typeerror|true", output(document));
    }

    @Test
    public void perPageRequestLimitRejectsExcessRequests() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.completing(uri ->
                response(uri, 200, "OK", ""));
        int attempts = GraalPageRuntime.MAX_FETCH_REQUESTS_PER_PAGE + 4;

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            runtime.execute(new JavaScriptSource("""
                    const versuche = %d;
                    const anfragen = [];
                    for (let i = 0; i < versuche; i++) anfragen.push(fetch('/eintrag-' + i));
                    Promise.allSettled(anfragen).then(results => {
                      const abgelehnt = results.filter(r => r.status === 'rejected');
                      document.getElementById('out').textContent =
                          (results.length - abgelehnt.length) + '|' + abgelehnt.length + '|'
                          + (abgelehnt.length > 0
                              && String(abgelehnt[0].reason).indexOf('Limit') >= 0);
                    });
                    """.formatted(attempts), null, "fetch-limit.js"));
            runtime.awaitIdle();
        }

        assertEquals(GraalPageRuntime.MAX_FETCH_REQUESTS_PER_PAGE + "|4|true", output(document));
        assertEquals(GraalPageRuntime.MAX_FETCH_REQUESTS_PER_PAGE, backend.requests.size());
    }

    @Test
    public void asynchronouslyCompletingBackendResolvesOnTheEventLoop() throws Exception {
        Document document = parse();
        CompletableFuture<JsFetchResponse> pending = new CompletableFuture<>();
        RecordingBackend backend = new RecordingBackend(uri -> pending);
        CountDownLatch changed = new CountDownLatch(1);
        document.addMutationListener(mutation -> changed.countDown());

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            runtime.execute(new JavaScriptSource("""
                    fetch('https://daten.example/spaet').then(r => r.text()).then(text => {
                      document.getElementById('out').textContent = text;
                    });
                    """, null, "fetch-asynchron.js"));
            assertEquals("", output(document));

            pending.complete(response(
                    URI.create("https://daten.example/spaet"), 200, "OK", "Endlich da"));
            assertTrue("Fetch-Auflösung hat das DOM nicht aktualisiert",
                    changed.await(2, TimeUnit.SECONDS));
            runtime.awaitIdle();
        }

        assertEquals("Endlich da", output(document));
    }

    private Document parse() {
        return parser.parse("""
                <html><body><output id="out"></output></body></html>
                """, DOCUMENT_URL);
    }

    private static String output(Document document) {
        return document.getElementById("out").getTextContent();
    }

    private static JsFetchResponse response(URI uri, int status, String statusText, String body) {
        return new JsFetchResponse(uri.toString(), status, statusText,
                List.of(new JsFetchResponse.Header("content-type", "text/plain")), body);
    }

    private static final class RecordingBackend implements JsFetchBackend {

        private final List<URI> requests = new CopyOnWriteArrayList<>();
        private final List<JsFetchRequest> requestDetails = new CopyOnWriteArrayList<>();
        private final Function<JsFetchRequest, CompletableFuture<JsFetchResponse>> responder;

        private RecordingBackend(
                Function<JsFetchRequest, CompletableFuture<JsFetchResponse>> responder) {
            this.responder = responder;
        }

        static RecordingBackend completing(Function<URI, JsFetchResponse> responder) {
            return completingRequest(request -> responder.apply(request.uri()));
        }

        static RecordingBackend completingRequest(
                Function<JsFetchRequest, JsFetchResponse> responder) {
            return new RecordingBackend(request ->
                    CompletableFuture.completedFuture(responder.apply(request)));
        }

        static RecordingBackend failing(Exception failure) {
            return new RecordingBackend(request -> CompletableFuture.failedFuture(failure));
        }

        @Override
        public CompletableFuture<JsFetchResponse> fetch(JsFetchRequest request) {
            requests.add(request.uri());
            requestDetails.add(request);
            return responder.apply(request);
        }
    }
}

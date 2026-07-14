package com.browicy.engine.js;

import com.browicy.engine.dom.Document;
import com.browicy.engine.html.HtmlParser;
import java.io.IOException;
import java.net.URI;
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

public class XmlHttpRequestTest {

    private static final String DOCUMENT_URL = "https://app.example/pfad/seite.html";

    private final HtmlParser parser = new HtmlParser();
    private final JavaScriptEngine engine = new JavaScriptEngine();

    @Test
    public void asyncGetDeliversResponseAndReadyStates() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.completing(uri ->
                response(uri, 200, "OK", "Hallo XHR"));

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            JsExecutionResult result = runtime.execute(new JavaScriptSource("""
                    const zustaende = [];
                    const xhr = new XMLHttpRequest();
                    xhr.onreadystatechange = () => zustaende.push(xhr.readyState);
                    xhr.onload = () => {
                      document.getElementById('out').textContent = [
                        zustaende.join(','), xhr.status, xhr.statusText,
                        xhr.responseText, xhr.responseURL
                      ].join('|');
                    };
                    xhr.open('GET', 'https://daten.example/gruss.txt');
                    xhr.send();
                    """, null, "xhr-async.js"));
            assertFalse(String.valueOf(result.errors()), result.hasErrors());
            runtime.awaitIdle();
        }

        assertEquals("1,2,3,4|200|OK|Hallo XHR|https://daten.example/gruss.txt",
                output(document));
        assertEquals(List.of(URI.create("https://daten.example/gruss.txt")), backend.requests);
    }

    @Test
    public void relativeUrlsResolveAgainstTheDocumentUrl() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.completing(uri ->
                response(uri, 200, "OK", ""));

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            runtime.execute(new JavaScriptSource("""
                    const xhr = new XMLHttpRequest();
                    xhr.open('GET', 'daten/info.json');
                    xhr.send();
                    """, null, "xhr-relativ.js"));
            runtime.awaitIdle();
        }

        assertEquals(List.of(
                URI.create("https://app.example/pfad/daten/info.json")), backend.requests);
    }

    @Test
    public void responseHeadersAreAccessible() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.completing(uri ->
                new JsFetchResponse(uri.toString(), 200, "OK", List.of(
                        new JsFetchResponse.Header("Content-Type", "text/plain"),
                        new JsFetchResponse.Header("x-mehrfach", "eins"),
                        new JsFetchResponse.Header("X-Mehrfach", "zwei")), ""));

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            runtime.execute(new JavaScriptSource("""
                    const xhr = new XMLHttpRequest();
                    xhr.onload = () => {
                      document.getElementById('out').textContent = [
                        xhr.getResponseHeader('CONTENT-TYPE'),
                        xhr.getResponseHeader('x-mehrfach'),
                        String(xhr.getResponseHeader('fehlt')),
                        JSON.stringify(xhr.getAllResponseHeaders())
                      ].join('|');
                    };
                    xhr.open('GET', 'https://daten.example/h');
                    xhr.send();
                    """, null, "xhr-headers.js"));
            runtime.awaitIdle();
        }

        assertEquals("text/plain|eins, zwei|null|"
                        + "\"content-type: text/plain\\r\\nx-mehrfach: eins, zwei\\r\\n\"",
                output(document));
    }

    @Test
    public void networkFailureFiresErrorEvent() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.failing(
                new IOException("Verbindung fehlgeschlagen"));

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            runtime.execute(new JavaScriptSource("""
                    const xhr = new XMLHttpRequest();
                    const meldungen = [];
                    xhr.onload = () => meldungen.push('load');
                    xhr.onerror = () => meldungen.push('error:' + xhr.status);
                    xhr.onloadend = () => {
                      meldungen.push('loadend:' + xhr.readyState);
                      document.getElementById('out').textContent = meldungen.join('|');
                    };
                    xhr.open('GET', 'https://kaputt.example/');
                    xhr.send();
                    """, null, "xhr-fehler.js"));
            runtime.awaitIdle();
        }

        assertEquals("error:0|loadend:4", output(document));
    }

    @Test
    public void abortIgnoresLateResponses() throws Exception {
        Document document = parse();
        CompletableFuture<JsFetchResponse> pending = new CompletableFuture<>();
        RecordingBackend backend = new RecordingBackend(uri -> pending);
        CountDownLatch changed = new CountDownLatch(1);
        document.addMutationListener(mutation -> changed.countDown());

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            runtime.execute(new JavaScriptSource("""
                    const xhr = new XMLHttpRequest();
                    const meldungen = [];
                    xhr.onload = () => meldungen.push('load');
                    xhr.onabort = () => meldungen.push('abort');
                    xhr.open('GET', 'https://daten.example/spaet');
                    xhr.send();
                    xhr.abort();
                    document.getElementById('out').textContent =
                        meldungen.join('|') + '|' + xhr.readyState;
                    """, null, "xhr-abort.js"));
            assertTrue(changed.await(2, TimeUnit.SECONDS));
            pending.complete(response(
                    URI.create("https://daten.example/spaet"), 200, "OK", "zu spaet"));
            runtime.awaitIdle();
        }

        assertEquals("abort|0", output(document));
    }

    @Test
    public void synchronousRequestsBlockUntilTheResponse() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.completing(uri ->
                response(uri, 200, "OK", "synchron da"));

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            JsExecutionResult result = runtime.execute(new JavaScriptSource("""
                    const xhr = new XMLHttpRequest();
                    xhr.open('GET', 'https://daten.example/direkt', false);
                    xhr.send();
                    document.getElementById('out').textContent =
                        xhr.readyState + '|' + xhr.status + '|' + xhr.responseText;
                    """, null, "xhr-sync.js"));
            assertFalse(String.valueOf(result.errors()), result.hasErrors());
        }

        assertEquals("4|200|synchron da", output(document));
    }

    @Test
    public void synchronousFailuresThrowNetworkError() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.failing(
                new IOException("Verbindung fehlgeschlagen"));

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            runtime.execute(new JavaScriptSource("""
                    const xhr = new XMLHttpRequest();
                    xhr.open('GET', 'https://kaputt.example/', false);
                    try {
                      xhr.send();
                      document.getElementById('out').textContent = 'kein-fehler';
                    } catch (error) {
                      document.getElementById('out').textContent =
                          error.name + '|' + (error instanceof DOMException);
                    }
                    """, null, "xhr-sync-fehler.js"));
        }

        assertEquals("NetworkError|true", output(document));
    }

    @Test
    public void invalidStatesAndUnsupportedMethodsThrow() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.completing(uri ->
                response(uri, 200, "OK", ""));

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            runtime.execute(new JavaScriptSource("""
                    const meldungen = [];
                    const ohneOpen = new XMLHttpRequest();
                    try { ohneOpen.send(); } catch (error) { meldungen.push(error.name); }
                    const post = new XMLHttpRequest();
                    post.open('POST', 'https://daten.example/');
                    try { post.send('daten'); } catch (error) { meldungen.push(error.name); }
                    try { new XMLHttpRequest().open('TRACE', '/x'); }
                    catch (error) { meldungen.push(error.name); }
                    document.getElementById('out').textContent = meldungen.join('|');
                    """, null, "xhr-validierung.js"));
            runtime.awaitIdle();
        }

        assertEquals("InvalidStateError|NotSupportedError|SecurityError", output(document));
        assertTrue(backend.requests.isEmpty());
    }

    @Test
    public void responseTypeJsonParsesTheBody() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.completing(uri ->
                response(uri, 200, "OK", "{\"wert\":42}"));

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            runtime.execute(new JavaScriptSource("""
                    const xhr = new XMLHttpRequest();
                    xhr.responseType = 'json';
                    xhr.onload = () => {
                      document.getElementById('out').textContent = xhr.response.wert;
                    };
                    xhr.open('GET', 'https://daten.example/info.json');
                    xhr.send();
                    """, null, "xhr-json.js"));
            runtime.awaitIdle();
        }

        assertEquals("42", output(document));
    }

    @Test
    public void timeoutFiresTimeoutEvent() throws Exception {
        Document document = parse();
        CompletableFuture<JsFetchResponse> pending = new CompletableFuture<>();
        RecordingBackend backend = new RecordingBackend(uri -> pending);
        CountDownLatch changed = new CountDownLatch(1);
        document.addMutationListener(mutation -> changed.countDown());

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            runtime.execute(new JavaScriptSource("""
                    const xhr = new XMLHttpRequest();
                    xhr.timeout = 30;
                    xhr.ontimeout = () => {
                      document.getElementById('out').textContent =
                          'timeout|' + xhr.readyState + '|' + xhr.status;
                    };
                    xhr.open('GET', 'https://daten.example/haengt');
                    xhr.send();
                    """, null, "xhr-timeout.js"));
            assertTrue("Timeout-Ereignis wurde nicht ausgeliefert",
                    changed.await(2, TimeUnit.SECONDS));
            runtime.awaitIdle();
        }

        assertEquals("timeout|4|0", output(document));
    }

    @Test
    public void listenersRegisteredViaAddEventListenerFire() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.completing(uri ->
                response(uri, 200, "OK", "inhalt"));

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            runtime.execute(new JavaScriptSource("""
                    const xhr = new XMLHttpRequest();
                    xhr.addEventListener('load', function(event) {
                      document.getElementById('out').textContent =
                          event.type + '|' + (event.target === xhr) + '|' + this.responseText;
                    });
                    xhr.open('GET', 'https://daten.example/');
                    xhr.send();
                    """, null, "xhr-listener.js"));
            runtime.awaitIdle();
        }

        assertEquals("load|true|inhalt", output(document));
    }

    @Test
    public void constantsAreExposed() {
        Document document = parse();
        RecordingBackend backend = RecordingBackend.completing(uri ->
                response(uri, 200, "OK", ""));

        try (PageRuntime runtime = engine.createPageRuntime(
                document, PageRuntimeObserver.NO_OP, backend)) {
            runtime.execute(new JavaScriptSource("""
                    const xhr = new XMLHttpRequest();
                    document.getElementById('out').textContent = [
                      XMLHttpRequest.UNSENT, XMLHttpRequest.OPENED,
                      XMLHttpRequest.HEADERS_RECEIVED, XMLHttpRequest.LOADING,
                      XMLHttpRequest.DONE, xhr.DONE, xhr.readyState
                    ].join('|');
                    """, null, "xhr-konstanten.js"));
            runtime.awaitIdle();
        }

        assertEquals("0|1|2|3|4|4|0", output(document));
    }

    @Test
    public void xhrIsUndefinedWithoutBackend() {
        Document document = parse();

        try (PageRuntime runtime = engine.createPageRuntime(document)) {
            runtime.execute(new JavaScriptSource("""
                    document.getElementById('out').textContent = typeof XMLHttpRequest;
                    """, null, "xhr-undef.js"));
            runtime.awaitIdle();
        }

        assertEquals("undefined", output(document));
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
        private final Function<URI, CompletableFuture<JsFetchResponse>> responder;

        private RecordingBackend(Function<URI, CompletableFuture<JsFetchResponse>> responder) {
            this.responder = responder;
        }

        static RecordingBackend completing(Function<URI, JsFetchResponse> responder) {
            return new RecordingBackend(uri ->
                    CompletableFuture.completedFuture(responder.apply(uri)));
        }

        static RecordingBackend failing(Exception failure) {
            return new RecordingBackend(uri -> CompletableFuture.failedFuture(failure));
        }

        @Override
        public CompletableFuture<JsFetchResponse> fetch(URI uri) {
            requests.add(uri);
            return responder.apply(uri);
        }
    }
}

package com.browicy.engine.js;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Event;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * JavaScript-Unterstützung der Browicy-Engine auf Basis von GraalJS
 * (GraalVM Polyglot API). Statt eine eigene JS-Engine zu bauen, wird die
 * vollständige ECMAScript-Implementierung von GraalVM eingebettet; Browicy
 * steuert nur bei, was ein Browser darüber hinaus braucht: die Anbindung
 * des DOM ({@code document}), der DOM-Events und der Konsole ({@code console}).
 *
 * <p><b>Sandbox:</b> Skripte laufen ohne Host-Zugriff (kein Java-Zugriff,
 * kein Dateisystem, keine Prozesse/Threads) und mit einem Statement-Limit
 * gegen Endlosschleifen. Das DOM ist ausschließlich über
 * {@link org.graalvm.polyglot.proxy.Proxy}-Objekte erreichbar.</p>
 *
 * <p><b>Prototyp-Grenzen:</b> Nur Inline-Skripte werden ausgeführt
 * (externe {@code <script src=…>} werden übersprungen), Timer werden als
 * einfache FIFO-Microtask-Näherung abgearbeitet und pro Seite wird noch ein
 * frischer, nach der Ausführung geschlossener Kontext verwendet.</p>
 */
public final class JavaScriptEngine {

    /** Großzügiges Limit; echte Seiten-Skripte bleiben weit darunter. */
    public static final long DEFAULT_STATEMENT_LIMIT = 10_000_000;

    private static final String BROWSER_BOOTSTRAP = """
            globalThis.window = globalThis;
            globalThis.Node = function Node() { throw new TypeError('Illegal constructor'); };
            Node.ELEMENT_NODE = 1;
            Node.TEXT_NODE = 3;
            Node.COMMENT_NODE = 8;
            Node.DOCUMENT_NODE = 9;
            Node.DOCUMENT_TYPE_NODE = 10;
            Node.DOCUMENT_FRAGMENT_NODE = 11;
            Node.DOCUMENT_POSITION_DISCONNECTED = 0x01;
            Node.DOCUMENT_POSITION_PRECEDING = 0x02;
            Node.DOCUMENT_POSITION_FOLLOWING = 0x04;
            Node.DOCUMENT_POSITION_CONTAINS = 0x08;
            Node.DOCUMENT_POSITION_CONTAINED_BY = 0x10;
            Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC = 0x20;
            globalThis.Range = function Range() { return document.createRange(); };
            Range.START_TO_START = 0;
            Range.START_TO_END = 1;
            Range.END_TO_END = 2;
            Range.END_TO_START = 3;
            globalThis.DOMException = class DOMException extends Error {
              constructor(message = '', name = 'Error') {
                super(String(message));
                this.name = String(name);
                this.code = DOMException[name] || 0;
              }
            };
            Object.assign(DOMException, {
              INDEX_SIZE_ERR:1, DOMSTRING_SIZE_ERR:2, HIERARCHY_REQUEST_ERR:3,
              WRONG_DOCUMENT_ERR:4, INVALID_CHARACTER_ERR:5, NO_DATA_ALLOWED_ERR:6,
              NO_MODIFICATION_ALLOWED_ERR:7, NOT_FOUND_ERR:8, NOT_SUPPORTED_ERR:9,
              INUSE_ATTRIBUTE_ERR:10, INVALID_STATE_ERR:11, SYNTAX_ERR:12,
              INVALID_MODIFICATION_ERR:13, NAMESPACE_ERR:14, INVALID_ACCESS_ERR:15,
              VALIDATION_ERR:16, TYPE_MISMATCH_ERR:17, SECURITY_ERR:18,
              NETWORK_ERR:19, ABORT_ERR:20, URL_MISMATCH_ERR:21, QUOTA_EXCEEDED_ERR:22,
              TIMEOUT_ERR:23, INVALID_NODE_TYPE_ERR:24, DATA_CLONE_ERR:25,
               HierarchyRequestError:3, WrongDocumentError:4, InvalidCharacterError:5,
               NotFoundError:8, InvalidStateError:11, NamespaceError:14, InvalidNodeTypeError:24
            });
            Object.assign(DOMException.prototype, DOMException);
            globalThis.NodeFilter = Object.freeze({
              FILTER_ACCEPT:1,FILTER_REJECT:2,FILTER_SKIP:3,
              SHOW_ALL:0xFFFFFFFF,SHOW_ELEMENT:1,SHOW_ATTRIBUTE:2,SHOW_TEXT:4,
              SHOW_CDATA_SECTION:8,SHOW_ENTITY_REFERENCE:16,SHOW_ENTITY:32,
              SHOW_PROCESSING_INSTRUCTION:64,SHOW_COMMENT:128,SHOW_DOCUMENT:256,
              SHOW_DOCUMENT_TYPE:512,SHOW_DOCUMENT_FRAGMENT:1024,SHOW_NOTATION:2048
            });
            globalThis.Event = function Event(type, init) {
              init = init || {};
              const event = document.createEvent('Event');
              event.initEvent(String(type), Boolean(init.bubbles), Boolean(init.cancelable));
              return event;
            };
            Event.NONE = 0;
            Event.CAPTURING_PHASE = 1;
            Event.AT_TARGET = 2;
            Event.BUBBLING_PHASE = 3;
            globalThis.UIEvent = function UIEvent(type, init) {
              init = init || {};
              const event = document.createEvent('UIEvent');
              event.initUIEvent(String(type), Boolean(init.bubbles), Boolean(init.cancelable),
                                init.view == null ? null : init.view, Number(init.detail) || 0);
              return event;
            };
            UIEvent.NONE = Event.NONE;
            UIEvent.CAPTURING_PHASE = Event.CAPTURING_PHASE;
            UIEvent.AT_TARGET = Event.AT_TARGET;
            UIEvent.BUBBLING_PHASE = Event.BUBBLING_PHASE;
            """;

    private static final String EVENT_LISTENER_INVOKER = """
            (listener, currentTarget, event) => {
              if (typeof listener === 'function') {
                return listener.call(currentTarget, event);
              }
              return listener.handleEvent.call(listener, event);
            }
            """;

    private final long statementLimit;

    public JavaScriptEngine() {
        this(DEFAULT_STATEMENT_LIMIT);
    }

    /** Für Tests: Engine mit eigenem Statement-Limit gegen Endlosschleifen. */
    public JavaScriptEngine(long statementLimit) {
        this.statementLimit = statementLimit;
    }

    /**
     * Führt alle Inline-Skripte des Dokuments in Dokumentreihenfolge aus.
     * Die Skripte teilen sich wie im Browser einen globalen Zustand; ein
     * Fehler in einem Skript bricht die folgenden nicht ab. DOM-Änderungen
     * wirken direkt auf das übergebene Dokument.
     */
    public JsExecutionResult runScripts(Document document) {
        List<Script> scripts = new ArrayList<>();
        for (Element script : document.getElementsByTagName("script")) {
            if (script.hasAttribute("src")) {
                continue; // Externe Skripte lädt der Prototyp noch nicht
            }
            String code = script.getTextContent();
            if (!code.isBlank()) {
                scripts.add(new Script(code, script));
            }
        }
        Element body = document.getBody();
        boolean hasInlineLoadHandler = body != null
                && body.hasAttribute("onload")
                && !body.getAttribute("onload").isBlank();
        if (scripts.isEmpty() && !hasInlineLoadHandler) {
            return JsExecutionResult.EMPTY;
        }
        return execute(document, scripts);
    }

    /**
     * Führt ein einzelnes Skript gegen das Dokument aus (z.B. für eine
     * spätere Entwickler-Konsole).
     */
    public JsExecutionResult execute(Document document, String script) {
        return execute(document, List.of(new Script(script, null)));
    }

    private JsExecutionResult execute(Document document, List<Script> scripts) {
        JsConsole console = new JsConsole();
        List<String> errors = new ArrayList<>();
        try (Context context = newSandboxedContext()) {
            Value bindings = context.getBindings("js");
            JsDocument jsDocument = new JsDocument(document, errors::add);
            try {
                bindings.putMember("document", jsDocument);
                bindings.putMember("console", console);
                context.eval("js", BROWSER_BOOTSTRAP);
                jsDocument.setDomOperationWrapper(context.eval("js", """
                        operation => (...args) => {
                          try { return operation(...args); }
                          catch (error) {
                            const message = String(error && error.message || error);
                            const marker = 'DOM_EXCEPTION|';
                            const start = message.indexOf(marker);
                            if (start < 0) throw error;
                            const fields = message.substring(start + marker.length).split('|');
                            throw new DOMException(fields.slice(2).join('|'), fields[0]);
                          }
                        }
                        """));
                jsDocument.setEventListenerInvoker(context.eval("js", EVENT_LISTENER_INVOKER));

                Deque<Value> timers = new ArrayDeque<>();
                bindings.putMember("setTimeout", (ProxyExecutable) args -> {
                    if (args.length > 0 && args[0].canExecute()) {
                        timers.addLast(args[0]);
                    }
                    return 0;
                });

                boolean contextUsable = true;
                int index = 0;
                for (Script script : scripts) {
                    index++;
                    try {
                        jsDocument.setCurrentScript(script.element());
                        context.eval(Source.newBuilder("js", script.code(), "inline-script-" + index + ".js")
                                .buildLiteral());
                    } catch (PolyglotException exception) {
                        errors.add(message(exception));
                        if (exception.isCancelled() || exception.isResourceExhausted()) {
                            contextUsable = false;
                            break; // Kontext ist nach Limit-Überschreitung nicht mehr nutzbar
                        }
                    }
                }
                jsDocument.setCurrentScript(null);

                if (contextUsable) {
                    dispatchLoadEvent(context, jsDocument, document, errors);
                    runTimers(timers, errors);
                }
            } finally {
                // GraalJS-Values sind nach Context.close() ungültig. Listener dieses
                // kurzlebigen Ausführungskontexts werden deshalb sauber aus dem DOM gelöst.
                jsDocument.clearEventListeners();
            }
        } catch (RuntimeException exception) {
            errors.add(message(exception));
        }
        return new JsExecutionResult(List.copyOf(console.getMessages()), List.copyOf(errors));
    }

    private static void dispatchLoadEvent(Context context, JsDocument jsDocument,
                                          Document document, List<String> errors) {
        Element body = document.getBody();
        if (body == null) {
            return;
        }

        JsEventListener inlineListener = null;
        if (body.hasAttribute("onload") && !body.getAttribute("onload").isBlank()) {
            try {
                Value callback = context.eval(Source.newBuilder("js",
                                "(function(event) {\n" + body.getAttribute("onload") + "\n})",
                                "body-onload.js")
                        .buildLiteral());
                inlineListener = new JsEventListener(callback, jsDocument);
                body.addEventListener("load", inlineListener, false);
            } catch (PolyglotException exception) {
                errors.add(message(exception));
            }
        }

        try {
            body.dispatchEvent(new Event("load", false, false));
        } finally {
            if (inlineListener != null) {
                body.removeEventListener("load", inlineListener, false);
            }
        }
    }

    private static void runTimers(Deque<Value> timers, List<String> errors) {
        while (!timers.isEmpty()) {
            try {
                timers.removeFirst().executeVoid();
            } catch (PolyglotException exception) {
                errors.add(message(exception));
                if (exception.isCancelled() || exception.isResourceExhausted()) {
                    return;
                }
            }
        }
    }

    private static String message(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private record Script(String code, Element element) {
    }

    private Context newSandboxedContext() {
        return Context.newBuilder("js")
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup(className -> false)
                .allowIO(IOAccess.NONE)
                .allowCreateProcess(false)
                .allowCreateThread(false)
                .resourceLimits(ResourceLimits.newBuilder()
                        .statementLimit(statementLimit, null)
                        .build())
                // Kein Hinweis-Spam, wenn ohne Graal-JIT (z.B. auf normalem JDK) gelaufen wird
                .option("engine.WarnInterpreterOnly", "false")
                .out(OutputStream.nullOutputStream())
                .err(OutputStream.nullOutputStream())
                .build();
    }
}

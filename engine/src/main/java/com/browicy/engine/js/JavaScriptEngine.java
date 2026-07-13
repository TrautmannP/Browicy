package com.browicy.engine.js;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * JavaScript-Unterstützung der Browicy-Engine auf Basis von GraalJS
 * (GraalVM Polyglot API). Statt eine eigene JS-Engine zu bauen, wird die
 * vollständige ECMAScript-Implementierung von GraalVM eingebettet; Browicy
 * steuert nur bei, was ein Browser darüber hinaus braucht: die Anbindung
 * des DOM ({@code document}) und der Konsole ({@code console}).
 *
 * <p><b>Sandbox:</b> Skripte laufen ohne Host-Zugriff (kein Java-Zugriff,
 * kein Dateisystem, keine Prozesse/Threads) und mit einem Statement-Limit
 * gegen Endlosschleifen. Das DOM ist ausschließlich über
 * {@link org.graalvm.polyglot.proxy.Proxy}-Objekte erreichbar.</p>
 *
 * <p><b>Prototyp-Grenzen:</b> Nur Inline-Skripte werden ausgeführt
 * (externe {@code <script src=…>} werden übersprungen), es gibt keine
 * Events, Timer oder dynamisches Nachladen. Pro Seite wird ein frischer,
 * nach der Ausführung geschlossener Kontext verwendet.</p>
 */
public final class JavaScriptEngine {

    /** Großzügiges Limit; echte Seiten-Skripte bleiben weit darunter. */
    public static final long DEFAULT_STATEMENT_LIMIT = 10_000_000;

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
        if (scripts.isEmpty()) {
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
            JsDocument jsDocument = new JsDocument(document);
            bindings.putMember("document", jsDocument);
            bindings.putMember("console", console);
            context.eval("js", "globalThis.NodeFilter = Object.freeze({" +
                    "FILTER_ACCEPT:1,FILTER_REJECT:2,FILTER_SKIP:3," +
                    "SHOW_ALL:0xFFFFFFFF,SHOW_ELEMENT:1,SHOW_ATTRIBUTE:2,SHOW_TEXT:4," +
                    "SHOW_CDATA_SECTION:8,SHOW_ENTITY_REFERENCE:16,SHOW_ENTITY:32," +
                    "SHOW_PROCESSING_INSTRUCTION:64,SHOW_COMMENT:128,SHOW_DOCUMENT:256," +
                    "SHOW_DOCUMENT_TYPE:512,SHOW_DOCUMENT_FRAGMENT:1024,SHOW_NOTATION:2048});");
            Deque<Value> timers = new ArrayDeque<>();
            bindings.putMember("setTimeout", (org.graalvm.polyglot.proxy.ProxyExecutable) args -> {
                if (args.length > 0 && args[0].canExecute()) {
                    timers.addLast(args[0]);
                }
                return 0;
            });
            int index = 0;
            for (Script script : scripts) {
                index++;
                try {
                    jsDocument.setCurrentScript(script.element());
                    context.eval(Source.newBuilder("js", script.code(), "inline-script-" + index + ".js")
                            .buildLiteral());
                } catch (PolyglotException e) {
                    errors.add(e.getMessage() == null ? e.toString() : e.getMessage());
                    if (e.isCancelled() || e.isResourceExhausted()) {
                        break; // Kontext ist nach Limit-Überschreitung nicht mehr nutzbar
                    }
                }
            }
            jsDocument.setCurrentScript(null);
            Element body = document.getBody();
            if (body != null && body.hasAttribute("onload")) {
                context.eval(Source.newBuilder("js", body.getAttribute("onload"), "body-onload.js")
                        .buildLiteral());
                while (!timers.isEmpty()) {
                    timers.removeFirst().executeVoid();
                }
            }
        } catch (RuntimeException e) {
            errors.add(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        return new JsExecutionResult(List.copyOf(console.getMessages()), List.copyOf(errors));
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

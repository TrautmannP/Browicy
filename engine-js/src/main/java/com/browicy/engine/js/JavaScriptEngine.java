package com.browicy.engine.js;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.DocumentReadyState;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Event;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Factory and compatibility facade for document-bound JavaScript runtimes. */
public final class JavaScriptEngine {

    public static final long DEFAULT_STATEMENT_LIMIT = 10_000_000;

    static final String BROWSER_BOOTSTRAP = """
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
               NotFoundError:8, InvalidStateError:11, SyntaxError:12, NamespaceError:14, InvalidNodeTypeError:24
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

    static final String EVENT_LISTENER_INVOKER = """
            (listener, currentTarget, event) => {
              if (typeof listener === 'function') {
                return listener.call(currentTarget, event);
              }
              return listener.handleEvent.call(listener, event);
            }
            """;

    static final String DOM_OPERATION_WRAPPER = """
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
            """;

    private final long statementLimit;

    public JavaScriptEngine() {
        this(DEFAULT_STATEMENT_LIMIT);
    }

    public JavaScriptEngine(long statementLimit) {
        if (statementLimit <= 0) {
            throw new IllegalArgumentException("Statement-Limit muss positiv sein");
        }
        this.statementLimit = statementLimit;
    }

    public PageRuntime createPageRuntime(Document document) {
        return createPageRuntime(document, PageRuntimeObserver.NO_OP);
    }

    public PageRuntime createPageRuntime(Document document, PageRuntimeObserver observer) {
        return new GraalPageRuntime(document, statementLimit, observer);
    }

    public JsExecutionResult runScripts(Document document) {
        Objects.requireNonNull(document, "document");
        List<JavaScriptSource> scripts = new ArrayList<>();
        for (Element script : document.getElementsByTagName("script")) {
            if (script.hasAttribute("src")) {
                continue;
            }
            String code = script.getTextContent();
            if (!code.isBlank()) {
                scripts.add(new JavaScriptSource(
                        code, script, "inline-script-" + (scripts.size() + 1) + ".js"));
            }
        }
        return executeSequence(document, scripts);
    }

    public JsExecutionResult runScripts(Document document, List<JavaScriptSource> scripts) {
        Objects.requireNonNull(scripts, "scripts");
        return executeSequence(document, List.copyOf(scripts));
    }

    public JsExecutionResult runScriptSequence(
            Document document, Iterable<JavaScriptSource> scripts) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(scripts, "scripts");
        return executeSequence(document, scripts);
    }

    public JsExecutionResult execute(Document document, String script) {
        return executeSequence(document, List.of(new JavaScriptSource(script, null, "script.js")));
    }

    private JsExecutionResult executeSequence(
            Document document, Iterable<JavaScriptSource> scripts) {
        Objects.requireNonNull(document, "document");
        try (PageRuntime runtime = createPageRuntime(document)) {
            for (JavaScriptSource source : scripts) {
                runtime.execute(source);
            }
            completeLifecycle(document, runtime);
            runtime.awaitIdle();
            return ((GraalPageRuntime) runtime).snapshotResult();
        }
    }

    private static void completeLifecycle(Document document, PageRuntime runtime) {
        runtime.enqueueTask(() -> document.transitionTo(DocumentReadyState.INTERACTIVE));
        runtime.submitEvent(document, new Event("DOMContentLoaded", true, false)).join();
        runtime.enqueueTask(() -> document.transitionTo(DocumentReadyState.COMPLETE));
        Element body = document.getBody();
        if (body != null) {
            runtime.submitEvent(body, new Event("load", false, false)).join();
        }
    }
}

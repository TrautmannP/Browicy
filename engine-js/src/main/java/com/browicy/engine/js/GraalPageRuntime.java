package com.browicy.engine.js;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Event;
import com.browicy.engine.css.CssParser;
import com.browicy.engine.css.StyleSheetRegistry;
import com.browicy.engine.selectors.SelectorParseException;
import com.browicy.engine.selectors.SelectorParser;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;

final class GraalPageRuntime implements PageRuntime {

    private static final System.Logger LOGGER = System.getLogger(GraalPageRuntime.class.getName());
    private static final AtomicLong NEXT_RUNTIME_ID = new AtomicLong();
    static final int MAX_FETCH_REQUESTS_PER_PAGE = 256;
    static final int MAX_REQUEST_HEADERS = 200;

    static final long SYNC_FETCH_TIMEOUT_MILLIS = 30_000;

    static final long TASK_TIME_BUDGET_MILLIS =
            Long.getLong("browicy.js.taskBudgetMillis", 10_000);

    private static final long INTERRUPT_GRACE_MILLIS = 5_000;

    private final Document document;
    private final long statementLimit;
    private final PageRuntimeObserver observer;
    private final JsFetchBackend fetchBackend;
    private final JsCookieStore cookieStore;
    private final StyleSheetRegistry styleSheets;
    private final Runnable styleSheetMutationCallback;
    private final LinkedBlockingDeque<Envelope<?>> tasks = new LinkedBlockingDeque<>();
    private final Deque<Envelope<?>> microtasks = new ArrayDeque<>();
    private final AtomicBoolean acceptingTasks = new AtomicBoolean(true);
    private final AtomicBoolean executingTask = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CompletableFuture<Void> initialized = new CompletableFuture<>();
    private final ScheduledThreadPoolExecutor scheduler;
    private final Thread eventLoopThread;
    private final AtomicLong nextTimerId = new AtomicLong();
    private final Map<Long, TimerRegistration> timers = new HashMap<>();

    private volatile Context context;
    private volatile long taskTimeBudgetMillis = TASK_TIME_BUDGET_MILLIS;
    private volatile String currentTaskDescription;
    private volatile long currentTaskStartNanos;
    private volatile long taskSequence;
    private JsDocument jsDocument;
    private JsMutationObserverRegistry mutationObservers;
    private JsConsole console;
    private final List<String> errors = new ArrayList<>();
    private volatile boolean contextUsable = true;
    private volatile boolean shutdownRequested;
    private boolean inlineLoadHandlerInstalled;
    private boolean globalLoadHandlerFired;
    private Value windowLoadInvoker;
    private Value mutationObserverDeliveryInvoker;
    private int startedFetchRequests;

    GraalPageRuntime(Document document, long statementLimit, PageRuntimeObserver observer) {
        this(document, statementLimit, observer, null, null,
                new StyleSheetRegistry(), () -> { });
    }

    GraalPageRuntime(Document document, long statementLimit, PageRuntimeObserver observer,
                     JsFetchBackend fetchBackend) {
        this(document, statementLimit, observer, fetchBackend, null,
                new StyleSheetRegistry(), () -> { });
    }

    GraalPageRuntime(Document document, long statementLimit, PageRuntimeObserver observer,
                     JsFetchBackend fetchBackend, JsCookieStore cookieStore) {
        this(document, statementLimit, observer, fetchBackend, cookieStore,
                new StyleSheetRegistry(), () -> { });
    }

    GraalPageRuntime(Document document, long statementLimit, PageRuntimeObserver observer,
                     JsFetchBackend fetchBackend, JsCookieStore cookieStore,
                     StyleSheetRegistry styleSheets, Runnable styleSheetMutationCallback) {
        this.document = Objects.requireNonNull(document, "document");
        this.statementLimit = statementLimit;
        this.observer = Objects.requireNonNull(observer, "observer");
        this.fetchBackend = fetchBackend;
        this.cookieStore = cookieStore == null ? new JsCookieStore() : cookieStore;
        this.styleSheets = Objects.requireNonNull(styleSheets, "styleSheets");
        this.styleSheetMutationCallback = Objects.requireNonNull(
                styleSheetMutationCallback, "styleSheetMutationCallback");
        long runtimeId = NEXT_RUNTIME_ID.incrementAndGet();
        ThreadFactory schedulerThreads = runnable -> {
            Thread thread = new Thread(runnable, "browicy-page-timer-" + runtimeId);
            thread.setDaemon(true);
            return thread;
        };
        scheduler = new ScheduledThreadPoolExecutor(1, schedulerThreads);
        scheduler.setRemoveOnCancelPolicy(true);
        eventLoopThread = Thread.ofPlatform()
                .daemon(true)
                .name("browicy-page-runtime-" + runtimeId)
                .start(this::runEventLoop);
        initialized.join();
    }

    @Override
    public JsExecutionResult execute(JavaScriptSource source) {
        Objects.requireNonNull(source, "source");
        return join(submit(new PageTask.Script(source), false));
    }

    @Override
    public void dispatchEvent(com.browicy.engine.dom.Node target, Event event) {
        submitEvent(target, event).exceptionally(failure -> {
            logTaskFailure("DOM-Event konnte nicht verarbeitet werden", failure);
            return false;
        });
    }

    @Override
    public CompletableFuture<Boolean> submitEvent(com.browicy.engine.dom.Node target, Event event) {
        return submit(new PageTask.DomEvent(target, event), false);
    }

    @Override
    public CompletableFuture<Void> submitTask(Runnable task) {
        return submit(new PageTask.Callback(task), false);
    }

    @Override
    public void enqueue(PageTask task) {
        submit(task, false).exceptionally(failure -> {
            logTaskFailure("Page-Task konnte nicht verarbeitet werden", failure);
            return null;
        });
    }

    @Override
    public void enqueueMicrotask(PageTask task) {
        Objects.requireNonNull(task, "task");
        if (Thread.currentThread() == eventLoopThread) {
            microtasks.addLast(new Envelope<>(task, new CompletableFuture<>(), false));
            return;
        }
        submitTask(() -> microtasks.addLast(
                new Envelope<>(task, new CompletableFuture<>(), false)));
    }

    @Override
    public void awaitIdle() {
        if (Thread.currentThread() == eventLoopThread) {
            return;
        }
        join(submit(new PageTask.Callback(() -> { }), false));
    }

    @Override
    public boolean isClosed() {
        return !acceptingTasks.get() || closed.get();
    }

    @Override
    public void close() {
        if (!acceptingTasks.compareAndSet(true, false)) {
            joinEventLoop();
            return;
        }
        if (Thread.currentThread() == eventLoopThread) {
            shutdownRequested = true;
            return;
        }
        CompletableFuture<Void> completion = new CompletableFuture<>();
        tasks.offerFirst(new Envelope<>(new PageTask.Callback(() -> { }), completion, true));
        cancelRunningGuestCode();
        join(completion);
        joinEventLoop();
    }

    JsExecutionResult snapshotResult() {
        return join(callOnRuntime(() -> new JsExecutionResult(
                List.copyOf(console.getMessages()), List.copyOf(errors))));
    }

    @Override
    public JsExecutionResult snapshot() {
        return snapshotResult();
    }

    private void runEventLoop() {
        try {
            initializeContext();
            initialized.complete(null);
            while (!shutdownRequested) {
                Envelope<?> envelope = tasks.take();
                if (envelope.shutdown()) {
                    envelope.complete(null);
                    shutdownRequested = true;
                    break;
                }
                processTurn(envelope);
            }
        } catch (Throwable failure) {
            initialized.completeExceptionally(failure);
            failPending(failure);
        } finally {
            acceptingTasks.set(false);
            cleanup();
            closed.set(true);
        }
    }

    private void initializeContext() {
        context = newSandboxedContext();
        console = new JsConsole();
        jsDocument = new JsDocument(
                document, errors::add, styleSheets, styleSheetMutationCallback);
        jsDocument.setCookieStore(cookieStore);
        Value bindings = context.getBindings("js");
        bindings.putMember("document", jsDocument);
        bindings.putMember("console", console);
        CssParser cssParser = new CssParser();
        SelectorParser selectorParser = new SelectorParser();
        bindings.putMember("__browicyCssSupports", (ProxyExecutable) args -> {
            if (args.length >= 2) {
                return cssParser.supports(asText(args[0]), asText(args[1]));
            }
            String condition = args.length == 0 ? "" : asText(args[0]).strip();
            if (condition.startsWith("selector(") && condition.endsWith(")")) {
                try {
                    selectorParser.parse(condition.substring(9, condition.length() - 1));
                    return true;
                } catch (SelectorParseException ignored) {
                    return false;
                }
            }
            if (condition.startsWith("(") && condition.endsWith(")")) {
                condition = condition.substring(1, condition.length() - 1);
            }
            int separator = condition.indexOf(':');
            return separator > 0 && cssParser.supports(
                    condition.substring(0, separator), condition.substring(separator + 1));
        });
        bindings.putMember("__browicyGetComputedStyle", (ProxyExecutable) args -> {
            if (args.length == 0 || !args[0].isProxyObject()
                    || !(args[0].asProxyObject() instanceof JsElement element)) {
                throw new IllegalArgumentException(
                        "getComputedStyle: argument 1 must be an Element");
            }
            return jsDocument.computedStyle(element);
        });
        context.eval("js", JavaScriptEngine.BROWSER_BOOTSTRAP);
        windowLoadInvoker = context.eval("js", "(callback, event) => callback.call(window, event)");
        mutationObserverDeliveryInvoker = bindings.getMember("__browicyDeliverMutationObserver");
        jsDocument.setDomOperationWrapper(context.eval("js", JavaScriptEngine.DOM_OPERATION_WRAPPER));
        jsDocument.setEventListenerInvoker(context.eval("js", JavaScriptEngine.EVENT_LISTENER_INVOKER));
        mutationObservers = new JsMutationObserverRegistry(
                document, jsDocument, this::scheduleMutationObserverDelivery);
        bindings.putMember("__browicyMutationObserve",
                (ProxyExecutable) mutationObservers::observe);
        bindings.putMember("__browicyMutationDisconnect",
                (ProxyExecutable) mutationObservers::disconnect);
        bindings.putMember("__browicyMutationTakeRecords",
                (ProxyExecutable) mutationObservers::takeRecords);
        bindings.putMember("setTimeout", (ProxyExecutable) args -> registerTimer(args, false));
        bindings.putMember("clearTimeout", (ProxyExecutable) args -> { clearTimer(args); return null; });
        bindings.putMember("setInterval", (ProxyExecutable) args -> registerTimer(args, true));
        bindings.putMember("clearInterval", (ProxyExecutable) args -> { clearTimer(args); return null; });
        bindings.putMember("queueMicrotask", (ProxyExecutable) args -> {
            Value callback = requireCallback(args, 0, "queueMicrotask");
            enqueueMicrotask(new PageTask.Callback(() -> executeCallback(callback, new Object[0])));
            return null;
        });
        if (fetchBackend != null) {
            bindings.putMember("__browicyFetch", (ProxyExecutable) this::startFetch);
            bindings.putMember("__browicyFetchSync", (ProxyExecutable) this::fetchSync);
            context.eval("js", JavaScriptEngine.FETCH_BOOTSTRAP);
            context.eval("js", JavaScriptEngine.XHR_BOOTSTRAP);
        }
    }

    private Object startFetch(Value[] args) {
        Value resolve = requireCallback(args, 4, "fetch");
        Value reject = requireCallback(args, 5, "fetch");
        JsFetchRequest request;
        try {
            request = createFetchRequest(args);
        } catch (IllegalArgumentException invalidRequest) {
            executeCallback(reject, new Object[]{"fetch: " + invalidRequest.getMessage()});
            return null;
        }
        if (startedFetchRequests >= MAX_FETCH_REQUESTS_PER_PAGE) {
            executeCallback(reject, new Object[]{
                    "fetch: Limit von " + MAX_FETCH_REQUESTS_PER_PAGE
                            + " Netzwerkanfragen pro Seite erreicht"});
            return null;
        }
        startedFetchRequests++;
        CompletableFuture<JsFetchResponse> pending;
        try {
            pending = Objects.requireNonNull(
                    fetchBackend.fetch(request), "fetchBackend lieferte null");
        } catch (RuntimeException backendFailure) {
            pending = CompletableFuture.failedFuture(backendFailure);
        }
        pending.whenComplete((response, failure) ->
                completeFetchOnEventLoop(resolve, reject, response, failure));
        return null;
    }

    private Object fetchSync(Value[] args) {
        JsFetchRequest request;
        try {
            request = createFetchRequest(args);
        } catch (IllegalArgumentException invalidRequest) {
            return ProxyArray.fromArray(false,
                    "XMLHttpRequest: " + invalidRequest.getMessage());
        }
        if (startedFetchRequests >= MAX_FETCH_REQUESTS_PER_PAGE) {
            return ProxyArray.fromArray(false,
                    "XMLHttpRequest: Limit von " + MAX_FETCH_REQUESTS_PER_PAGE
                            + " Netzwerkanfragen pro Seite erreicht");
        }
        startedFetchRequests++;
        JsFetchResponse response;
        try {
            response = Objects.requireNonNull(
                            fetchBackend.fetch(request), "fetchBackend lieferte null")
                    .get(SYNC_FETCH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return ProxyArray.fromArray(false, "XMLHttpRequest: Anfrage wurde unterbrochen");
        } catch (java.util.concurrent.TimeoutException timeout) {
            return ProxyArray.fromArray(false, "XMLHttpRequest: Zeitüberschreitung nach "
                    + SYNC_FETCH_TIMEOUT_MILLIS + " ms");
        } catch (Exception failure) {
            return ProxyArray.fromArray(false, failureMessage(failure));
        }
        Object[] headerPairs = new Object[response.headers().size() * 2];
        int index = 0;
        for (JsFetchResponse.Header header : response.headers()) {
            headerPairs[index++] = header.name();
            headerPairs[index++] = header.value();
        }
        return ProxyArray.fromArray(true, response.url(), response.status(),
                response.statusText(), ProxyArray.fromArray(headerPairs), response.bodyText());
    }

    private JsFetchRequest createFetchRequest(Value[] args) {
        String url = args.length == 0 ? "" : asText(args[0]);
        String method = args.length <= 1 ? "GET" : asText(args[1]);
        List<JsFetchRequest.Header> headers = args.length <= 2
                ? List.of() : requestHeaders(args[2]);
        byte[] body = null;
        if (args.length > 3 && !args[3].isNull()) {
            body = asText(args[3]).getBytes(StandardCharsets.UTF_8);
        }
        URI target = resolveFetchUri(url);
        JsFetchRequest request = new JsFetchRequest(target, method, headers, body);
        if (request.hasBody()
                && (request.method().equals("GET") || request.method().equals("HEAD"))) {
            throw new IllegalArgumentException(
                    "Request-Body ist für " + request.method() + " nicht erlaubt");
        }
        return request;
    }

    private static List<JsFetchRequest.Header> requestHeaders(Value headerPairs) {
        if (headerPairs == null || headerPairs.isNull()) {
            return List.of();
        }
        if (!headerPairs.hasArrayElements()) {
            throw new IllegalArgumentException("Ungültige Request-Header");
        }
        long size = headerPairs.getArraySize();
        if ((size & 1) != 0) {
            throw new IllegalArgumentException("Unvollständige Request-Header");
        }
        if (size / 2 > MAX_REQUEST_HEADERS) {
            throw new IllegalArgumentException("Zu viele Request-Header");
        }
        List<JsFetchRequest.Header> headers = new ArrayList<>((int) (size / 2));
        for (long index = 0; index < size; index += 2) {
            headers.add(new JsFetchRequest.Header(
                    asText(headerPairs.getArrayElement(index)),
                    asText(headerPairs.getArrayElement(index + 1))));
        }
        return List.copyOf(headers);
    }

    private URI resolveFetchUri(String url) {
        URI resolved;
        try {
            String documentUrl = document.getUrl();
            URI base = documentUrl == null || documentUrl.isBlank()
                    ? null : new URI(documentUrl);
            resolved = base == null ? new URI(url) : base.resolve(url);
        } catch (URISyntaxException | IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Ungültige URL: " + url);
        }
        String scheme = resolved.getScheme();
        if (scheme == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Nicht unterstützte URL: " + resolved);
        }
        if (resolved.getHost() == null) {
            throw new IllegalArgumentException("URL ohne Host: " + resolved);
        }
        return resolved;
    }

    private void completeFetchOnEventLoop(Value resolve, Value reject,
                                          JsFetchResponse response, Throwable failure) {
        submit(new PageTask.Callback(() -> {
            if (!contextUsable) {
                return;
            }
            if (failure != null) {
                executeCallback(reject, new Object[]{failureMessage(failure)});
                return;
            }
            Object[] headerPairs = new Object[response.headers().size() * 2];
            int index = 0;
            for (JsFetchResponse.Header header : response.headers()) {
                headerPairs[index++] = header.name();
                headerPairs[index++] = header.value();
            }
            executeCallback(resolve, new Object[]{
                    response.url(), response.status(), response.statusText(),
                    ProxyArray.fromArray(headerPairs), response.bodyText()});
        }), false).exceptionally(taskFailure -> {
            logTaskFailure("Fetch-Ergebnis konnte nicht zugestellt werden", taskFailure);
            return null;
        });
    }

    private static String failureMessage(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private void installInlineLoadHandler() {
        if (inlineLoadHandlerInstalled) {
            return;
        }
        inlineLoadHandlerInstalled = true;
        Element body = document.getBody();
        if (body == null || !body.hasAttribute("onload") || body.getAttribute("onload").isBlank()) {
            return;
        }
        try {
            Value callback = context.eval(Source.newBuilder("js",
                            "(function(event) {\n" + body.getAttribute("onload") + "\n})",
                            "body-onload.js")
                    .buildLiteral());
            jsDocument.addEventListener(body, "load", callback, false);
        } catch (PolyglotException exception) {
            recordPolyglotFailure(exception);
        }
    }

    private void processTurn(Envelope<?> envelope) {
        Object result = null;
        Throwable taskFailure = null;
        long sequence = ++taskSequence;
        currentTaskDescription = describe(envelope.task());
        currentTaskStartNanos = System.nanoTime();
        executingTask.set(true);
        ScheduledFuture<?> watchdog = scheduleTaskWatchdog(sequence, currentTaskDescription);
        try {
            result = process(envelope.task());
            drainMicrotasks();
        } catch (Throwable failure) {
            markContextIfFatal(failure);
            taskFailure = failure;
        } finally {
            if (watchdog != null) {
                watchdog.cancel(false);
            }
            executingTask.set(false);
            currentTaskDescription = null;
            notifyObserver(envelope.task());
        }
        if (taskFailure == null) {
            envelope.complete(result);
        } else {
            envelope.fail(taskFailure);
        }
    }

    void taskTimeBudgetMillisForTesting(long budgetMillis) {
        this.taskTimeBudgetMillis = budgetMillis;
    }

    private ScheduledFuture<?> scheduleTaskWatchdog(long sequence, String description) {
        long budgetMillis = taskTimeBudgetMillis;
        if (budgetMillis <= 0 || scheduler.isShutdown()) {
            return null;
        }
        try {
            return scheduler.schedule(() -> interruptOverdueTask(sequence, description),
                    budgetMillis, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException shuttingDown) {
            return null;
        }
    }

    private void interruptOverdueTask(long sequence, String description) {
        if (taskSequence != sequence || !executingTask.get()) {
            return;
        }
        Context activeContext = context;
        if (activeContext == null) {
            return;
        }
        LOGGER.log(System.Logger.Level.WARNING,
                "JavaScript-Task überschreitet das Zeitbudget von " + taskTimeBudgetMillis
                        + " ms und wird unterbrochen: " + description);
        try {
            activeContext.interrupt(java.time.Duration.ofMillis(INTERRUPT_GRACE_MILLIS));
        } catch (java.util.concurrent.TimeoutException stuck) {
            LOGGER.log(System.Logger.Level.ERROR,
                    "Unterbrechung griff nicht innerhalb von " + INTERRUPT_GRACE_MILLIS
                            + " ms – GraalJS-Kontext wird hart abgebrochen: " + description);
            activeContext.close(true);
        } catch (RuntimeException alreadyClosing) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Watchdog konnte den Kontext nicht unterbrechen", alreadyClosing);
        }
    }

    private static String describe(PageTask task) {
        return switch (task) {
            case PageTask.Script script -> "Skript " + script.source().sourceName();
            case PageTask.DomEvent domEvent -> "DOM-Event '" + domEvent.event().getType() + "'";
            case PageTask.Timer timer -> "Timer-Callback #" + timer.timerId();
            case PageTask.Callback ignored -> "interner Callback";
        };
    }

    @Override
    public PageRuntimeDiagnostics diagnostics() {
        String task = currentTaskDescription;
        long startNanos = currentTaskStartNanos;
        long runningMillis = task == null ? 0
                : Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
        return new PageRuntimeDiagnostics(isClosed(), contextUsable, tasks.size(),
                task, runningMillis);
    }

    private void drainMicrotasks() {
        while (!microtasks.isEmpty()) {
            Envelope<?> microtask = microtasks.removeFirst();
            try {
                Object result = process(microtask.task());
                microtask.complete(result);
            } catch (Throwable failure) {
                markContextIfFatal(failure);
                microtask.fail(failure);
            }
        }
    }

    private void scheduleMutationObserverDelivery(long observerId) {
        enqueueMicrotask(new PageTask.Callback(() -> {
            List<Object> records = mutationObservers.drain(observerId);
            if (!records.isEmpty()) {
                executeCallback(mutationObserverDeliveryInvoker,
                        new Object[]{observerId, ProxyArray.fromList(records)});
            }
        }));
    }

    private Object process(PageTask task) {
        synchronized (document) {
            return switch (task) {
                case PageTask.Script script -> evaluate(script.source());
                case PageTask.DomEvent domEvent -> {
                    if ("load".equals(domEvent.event().getType())
                            && domEvent.target() == document.getBody()) {
                        installInlineLoadHandler();
                    }
                    boolean allowed = domEvent.target().dispatchEvent(domEvent.event());
                    if ("load".equals(domEvent.event().getType())
                            && domEvent.target() == document.getBody()) {
                        invokeGlobalLoadHandler(domEvent.event());
                    }
                    yield allowed;
                }
                case PageTask.Callback callback -> {
                    callback.callback().run();
                    yield null;
                }
                case PageTask.Timer timer -> {
                    timer.callback().run();
                    yield null;
                }
            };
        }
    }

    private void invokeGlobalLoadHandler(Event event) {
        if (globalLoadHandlerFired || !contextUsable) return;
        globalLoadHandlerFired = true;
        Value handler = context.getBindings("js").getMember("onload");
        if (handler != null && handler.canExecute()) {
            try {
                windowLoadInvoker.executeVoid(handler, jsDocument.wrap(event));
            } catch (PolyglotException exception) {
                recordPolyglotFailure(exception);
            }
        }
        try {
            context.getBindings("js").getMember("__browicyDispatchWindowEvent")
                    .executeVoid("load", jsDocument.wrap(event));
        } catch (PolyglotException exception) {
            recordPolyglotFailure(exception);
        }
    }

    private static String asText(Value value) {
        return value.isString() ? value.asString() : value.toString();
    }

    private JsExecutionResult evaluate(JavaScriptSource source) {
        int consoleStart = console.getMessages().size();
        int errorStart = errors.size();
        if (!contextUsable) {
            errors.add("Der JavaScript-Kontext ist nach einem fatalen Fehler nicht mehr verwendbar");
            return delta(consoleStart, errorStart);
        }
        try {
            jsDocument.setCurrentScript(source.element());
            context.eval(Source.newBuilder("js", source.code(), source.sourceName()).buildLiteral());
        } catch (PolyglotException exception) {
            recordPolyglotFailure(exception);
        } finally {
            jsDocument.setCurrentScript(null);
        }
        return delta(consoleStart, errorStart);
    }

    private JsExecutionResult delta(int consoleStart, int errorStart) {
        return new JsExecutionResult(
                List.copyOf(console.getMessages().subList(consoleStart, console.getMessages().size())),
                List.copyOf(errors.subList(errorStart, errors.size())));
    }

    private Object registerTimer(Value[] args, boolean repeating) {
        Value callback = requireCallback(args, 0, repeating ? "setInterval" : "setTimeout");
        long delayMillis = normalizeDelay(args);
        Object[] callbackArguments = Arrays.copyOfRange(args, Math.min(2, args.length), args.length);
        long timerId = nextTimerId.incrementAndGet();
        TimerRegistration registration = new TimerRegistration(
                timerId, callback, callbackArguments, delayMillis, repeating);
        timers.put(timerId, registration);
        schedule(registration);
        return timerId;
    }

    private void schedule(TimerRegistration registration) {
        if (!timers.containsKey(registration.id())) {
            return;
        }
        Runnable enqueue = () -> enqueue(new PageTask.Timer(
                registration.id(), () -> fireTimer(registration.id())));
        if (registration.delayMillis() == 0) {
            enqueue.run();
        } else {
            ScheduledFuture<?> future = scheduler.schedule(
                    enqueue, registration.delayMillis(), TimeUnit.MILLISECONDS);
            registration.future(future);
        }
    }

    private void fireTimer(long timerId) {
        TimerRegistration registration = timers.get(timerId);
        if (registration == null || !contextUsable) {
            return;
        }
        if (!registration.repeating()) {
            timers.remove(timerId);
        }
        executeCallback(registration.callback(), registration.arguments());
        if (registration.repeating() && timers.containsKey(timerId) && acceptingTasks.get()) {
            schedule(registration);
        }
    }

    private void executeCallback(Value callback, Object[] arguments) {
        try {
            callback.executeVoid(arguments);
        } catch (PolyglotException exception) {
            recordPolyglotFailure(exception);
            if (exception.isCancelled() || exception.isResourceExhausted()) {
                throw exception;
            }
        }
    }

    private void clearTimer(Value[] args) {
        if (args.length == 0 || args[0].isNull()) {
            return;
        }
        long timerId = args[0].fitsInLong() ? args[0].asLong() : (long) args[0].asDouble();
        TimerRegistration registration = timers.remove(timerId);
        if (registration != null && registration.future() != null) {
            registration.future().cancel(false);
        }
    }

    private static Value requireCallback(Value[] args, int index, String functionName) {
        if (index >= args.length || !args[index].canExecute()) {
            throw new IllegalArgumentException(functionName + " erwartet eine aufrufbare Funktion");
        }
        return args[index];
    }

    private static long normalizeDelay(Value[] args) {
        if (args.length < 2 || args[1].isNull()) {
            return 0;
        }
        double delay = args[1].fitsInDouble() ? args[1].asDouble() : 0;
        if (!Double.isFinite(delay) || delay <= 0) {
            return 0;
        }
        return Math.min(Integer.MAX_VALUE, (long) delay);
    }

    private void recordPolyglotFailure(PolyglotException exception) {
        String detail = message(exception);
        if (exception.getSourceLocation() != null) {
            var location = exception.getSourceLocation();
            detail += " (" + location.getSource().getName() + ":"
                    + location.getStartLine() + ":" + location.getStartColumn() + ")";
        }
        if (exception.isInterrupted()) {
            detail = "Skript wurde nach Überschreitung des Zeitbudgets von "
                    + taskTimeBudgetMillis + " ms unterbrochen (mögliche Endlosschleife): "
                    + detail;
        }
        errors.add(detail);
        if (exception.isCancelled() || exception.isResourceExhausted()) {
            contextUsable = false;
        }
    }

    private void markContextIfFatal(Throwable failure) {
        if (failure instanceof PolyglotException exception
                && (exception.isCancelled() || exception.isResourceExhausted())) {
            contextUsable = false;
        }
    }

    private void notifyObserver(PageTask task) {
        try {
            observer.afterTask(task);
        } catch (RuntimeException failure) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Beobachter eines Page-Runtime-Tasks warf eine Exception", failure);
        }
    }

    private <T> CompletableFuture<T> submit(PageTask task, boolean microtask) {
        Objects.requireNonNull(task, "task");
        if (!acceptingTasks.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("PageRuntime ist geschlossen"));
        }
        CompletableFuture<T> completion = new CompletableFuture<>();
        Envelope<T> envelope = new Envelope<>(task, completion, false);
        if (Thread.currentThread() == eventLoopThread && microtask) {
            microtasks.addLast(envelope);
        } else {
            tasks.offerLast(envelope);
        }
        return completion;
    }

    private <T> CompletableFuture<T> callOnRuntime(Callable<T> callable) {
        if (Thread.currentThread() == eventLoopThread) {
            try {
                return CompletableFuture.completedFuture(callable.call());
            } catch (Exception failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        submitTask(() -> {
            try {
                result.complete(callable.call());
            } catch (Exception failure) {
                result.completeExceptionally(failure);
            }
        }).exceptionally(failure -> {
            result.completeExceptionally(failure);
            return null;
        });
        return result;
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
                .option("engine.WarnInterpreterOnly", "false")
                .out(OutputStream.nullOutputStream())
                .err(OutputStream.nullOutputStream())
                .build();
    }

    private void cleanup() {
        scheduler.shutdownNow();
        for (TimerRegistration registration : timers.values()) {
            if (registration.future() != null) {
                registration.future().cancel(false);
            }
        }
        timers.clear();
        if (jsDocument != null) {
            jsDocument.clearEventListeners();
        }
        if (mutationObservers != null) {
            mutationObservers.close();
        }
        if (context != null) {
            try {
                context.close(true);
            } catch (RuntimeException failure) {
                LOGGER.log(System.Logger.Level.DEBUG, "GraalJS-Kontext konnte nicht sauber schließen", failure);
            }
        }
        failPending(new IllegalStateException("PageRuntime wurde geschlossen"));
    }

    private void failPending(Throwable failure) {
        Envelope<?> envelope;
        while ((envelope = tasks.poll()) != null) {
            envelope.fail(failure);
        }
        while (!microtasks.isEmpty()) {
            microtasks.removeFirst().fail(failure);
        }
    }

    private static String message(RuntimeException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName() : exception.getMessage();
    }


    private void cancelRunningGuestCode() {
        Context activeContext = context;
        if (!executingTask.get() || activeContext == null) {
            return;
        }
        try {
            activeContext.close(true);
        } catch (RuntimeException failure) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Laufende GraalJS-Ausführung konnte nicht aktiv abgebrochen werden", failure);
        }
    }

    private void joinEventLoop() {
        if (Thread.currentThread() == eventLoopThread) {
            return;
        }
        boolean interrupted = false;
        while (eventLoopThread.isAlive()) {
            try {
                eventLoopThread.join();
            } catch (InterruptedException failure) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static <T> T join(CompletableFuture<T> future) {
        return future.join();
    }

    private static void logTaskFailure(String message, Throwable failure) {
        LOGGER.log(System.Logger.Level.DEBUG, message, failure);
    }

    private record Envelope<T>(PageTask task,
                               CompletableFuture<T> completion,
                               boolean shutdown) {
        private void complete(Object result) {
            @SuppressWarnings("unchecked")
            T typed = (T) result;
            completion.complete(typed);
        }

        private void fail(Throwable failure) {
            completion.completeExceptionally(failure);
        }
    }

    private static final class TimerRegistration {
        private final long id;
        private final Value callback;
        private final Object[] arguments;
        private final long delayMillis;
        private final boolean repeating;
        private ScheduledFuture<?> future;

        private TimerRegistration(long id, Value callback, Object[] arguments,
                                  long delayMillis, boolean repeating) {
            this.id = id;
            this.callback = callback;
            this.arguments = arguments;
            this.delayMillis = delayMillis;
            this.repeating = repeating;
        }

        long id() { return id; }
        Value callback() { return callback; }
        Object[] arguments() { return arguments; }
        long delayMillis() { return delayMillis; }
        boolean repeating() { return repeating; }
        ScheduledFuture<?> future() { return future; }
        void future(ScheduledFuture<?> future) { this.future = future; }
    }
}

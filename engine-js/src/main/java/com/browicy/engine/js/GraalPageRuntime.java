package com.browicy.engine.js;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Event;
import java.io.OutputStream;
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
import org.graalvm.polyglot.proxy.ProxyExecutable;

final class GraalPageRuntime implements PageRuntime {

    private static final System.Logger LOGGER = System.getLogger(GraalPageRuntime.class.getName());
    private static final AtomicLong NEXT_RUNTIME_ID = new AtomicLong();

    private final Document document;
    private final long statementLimit;
    private final PageRuntimeObserver observer;
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
    private JsDocument jsDocument;
    private JsConsole console;
    private final List<String> errors = new ArrayList<>();
    private volatile boolean contextUsable = true;
    private volatile boolean shutdownRequested;
    private boolean inlineLoadHandlerInstalled;

    GraalPageRuntime(Document document, long statementLimit, PageRuntimeObserver observer) {
        this.document = Objects.requireNonNull(document, "document");
        this.statementLimit = statementLimit;
        this.observer = Objects.requireNonNull(observer, "observer");
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
        jsDocument = new JsDocument(document, errors::add);
        Value bindings = context.getBindings("js");
        bindings.putMember("document", jsDocument);
        bindings.putMember("console", console);
        context.eval("js", JavaScriptEngine.BROWSER_BOOTSTRAP);
        jsDocument.setDomOperationWrapper(context.eval("js", JavaScriptEngine.DOM_OPERATION_WRAPPER));
        jsDocument.setEventListenerInvoker(context.eval("js", JavaScriptEngine.EVENT_LISTENER_INVOKER));
        bindings.putMember("setTimeout", (ProxyExecutable) args -> registerTimer(args, false));
        bindings.putMember("clearTimeout", (ProxyExecutable) args -> { clearTimer(args); return null; });
        bindings.putMember("setInterval", (ProxyExecutable) args -> registerTimer(args, true));
        bindings.putMember("clearInterval", (ProxyExecutable) args -> { clearTimer(args); return null; });
        bindings.putMember("queueMicrotask", (ProxyExecutable) args -> {
            Value callback = requireCallback(args, 0, "queueMicrotask");
            enqueueMicrotask(new PageTask.Callback(() -> executeCallback(callback, new Object[0])));
            return null;
        });
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
        executingTask.set(true);
        try {
            result = process(envelope.task());
            drainMicrotasks();
        } catch (Throwable failure) {
            markContextIfFatal(failure);
            taskFailure = failure;
        } finally {
            executingTask.set(false);
            notifyObserver(envelope.task());
        }
        if (taskFailure == null) {
            envelope.complete(result);
        } else {
            envelope.fail(taskFailure);
        }
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

    private Object process(PageTask task) {
        synchronized (document) {
            return switch (task) {
                case PageTask.Script script -> evaluate(script.source());
                case PageTask.DomEvent domEvent -> {
                    if ("load".equals(domEvent.event().getType())
                            && domEvent.target() == document.getBody()) {
                        installInlineLoadHandler();
                    }
                    yield domEvent.target().dispatchEvent(domEvent.event());
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
        errors.add(message(exception));
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

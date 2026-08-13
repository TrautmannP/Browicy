package com.browicy.engine.js;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.DocumentMutationListener;
import com.browicy.engine.dom.DomMutation;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;

/**
 * Java-seitige Custom-Element-Verwaltung: hält die registrierten Konstruktoren,
 * erkennt Elemente über {@code document.createElement} und stößt die
 * Lebenszyklus-Callbacks (connected/disconnected/attributeChanged) auf Basis der
 * DOM-Mutations-Meldungen an.
 *
 * <p>Die Zustellung erfolgt als Mikrotask auf dem Event-Loop, damit der GraalJS-Kontext
 * nicht re-entrant aus dem Mutations-Pfad heraus betreten wird. Attribut-Werte
 * (oldValue/newValue) werden dabei zum Mutationszeitpunkt erfasst und sind daher
 * unabhängig von späteren Änderungen.
 */
final class JsCustomElementRegistry implements AutoCloseable {

    private static final int MAX_CALLBACK_FAILURES = 5;

    private final Document document;
    private final JsDocument jsDocument;
    private final Consumer<String> errorSink;
    private final Runnable enqueueDelivery;
    private final Value invoker;
    private final Map<String, Value> constructors = new HashMap<>();
    private final Map<String, List<String>> observedAttributes = new HashMap<>();
    private final Set<Node> upgraded = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<String, Integer> failures = new HashMap<>();
    private final Set<String> reportedErrors = new HashSet<>();
    private final Deque<Runnable> pending = new ArrayDeque<>();
    private boolean deliveryScheduled;
    private final DocumentMutationListener listener = this::record;

    JsCustomElementRegistry(Document document, JsDocument jsDocument,
                            Consumer<String> errorSink, Runnable enqueueDelivery,
                            Value invoker) {
        this.document = Objects.requireNonNull(document, "document");
        this.jsDocument = Objects.requireNonNull(jsDocument, "jsDocument");
        this.errorSink = Objects.requireNonNull(errorSink, "errorSink");
        this.enqueueDelivery = Objects.requireNonNull(enqueueDelivery, "enqueueDelivery");
        this.invoker = Objects.requireNonNull(invoker, "invoker");
        document.addMutationListener(listener);
    }

    void define(String name, Value ctor) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(ctor, "ctor");
        String normalized = name.toLowerCase(Locale.ROOT);
        constructors.put(normalized, ctor);
        observedAttributes.put(normalized, observedAttributesOf(ctor));
        for (Element element : document.getElementsByTagName(normalized)) {
            upgrade(element, constructors.get(normalized));
            queueConnected(element);
        }
    }

    void elementCreated(Element element) {
        Value ctor = constructors.get(tagName(element));
        if (ctor != null) {
            upgraded.add(element);
        }
    }

    void upgrade(Node root) {
        walk(root, (element, ctor) -> {
            upgrade(element, ctor);
            if (connected(element)) {
                queueConnected(element);
            }
        });
    }

    void deliver() {
        while (!pending.isEmpty()) {
            pending.removeFirst().run();
        }
        deliveryScheduled = false;
    }

    private void record(DomMutation mutation) {
        switch (mutation) {
            case DomMutation.AttributeChanged attribute -> {
                if (!upgraded.contains(attribute.target())) {
                    return;
                }
                String name = tagName(attribute.target());
                Value ctor = constructors.get(name);
                if (ctor == null) {
                    return;
                }
                List<String> observed = observedAttributes.get(name);
                if (observed == null || !observed.contains(attribute.attributeName())) {
                    return;
                }
                queue(() -> invoke(ctor, attribute.target(), "attributeChangedCallback",
                        new Object[]{attribute.attributeName(),
                                attribute.oldValue(), attribute.newValue()}));
            }
            case DomMutation.ChildListChanged childList -> {
                for (Node added : childList.addedNodes()) {
                    walk(added, (element, ctor) -> {
                        upgrade(element, ctor);
                        queueConnected(element);
                    });
                }
                for (Node removed : childList.removedNodes()) {
                    walk(removed, (element, ctor) -> queue(() -> invoke(
                            ctor, element, "disconnectedCallback", new Object[0])));
                }
            }
            default -> {
                // CharacterData- und UI-Zustandsänderungen sind für Custom Elements irrelevant.
            }
        }
    }

    private void upgrade(Element element, Value ctor) {
        if (!upgraded.add(element)) {
            return;
        }
        String name = tagName(element);
        List<String> observed = observedAttributes.get(name);
        if (observed == null) {
            return;
        }
        for (String attribute : observed) {
            String value = element.getAttribute(attribute);
            if (value != null) {
                queue(() -> invoke(ctor, element, "attributeChangedCallback",
                        new Object[]{attribute, null, value}));
            }
        }
    }

    private void queueConnected(Element element) {
        Value ctor = constructors.get(tagName(element));
        if (ctor != null) {
            queue(() -> invoke(ctor, element, "connectedCallback", new Object[0]));
        }
    }

    private void walk(Node node, BiConsumer<Element, Value> visitor) {
        if (node == null) {
            return;
        }
        if (node instanceof Element element) {
            Value ctor = constructors.get(tagName(element));
            if (ctor != null) {
                visitor.accept(element, ctor);
            }
        }
        for (Node child : node.getChildren()) {
            walk(child, visitor);
        }
    }

    private void invoke(Value ctor, Element element, String method, Object[] arguments) {
        String key = tagName(element) + "#" + method;
        int failureCount = failures.getOrDefault(key, 0);
        if (failureCount >= MAX_CALLBACK_FAILURES) {
            // Circuit Breaker: wiederholt werfende Callbacks nicht endlos erneut
            // ausführen (Fehlersturm auf Seiten mit inkompatiblen Komponenten).
            return;
        }
        try {
            invoker.executeVoid(ctor, jsDocument.wrap(element), method,
                    ProxyArray.fromArray(arguments));
            failures.remove(key);
        } catch (PolyglotException failure) {
            if (failure.isCancelled() || failure.isResourceExhausted()) {
                throw failure;
            }
            failures.put(key, failureCount + 1);
            reportError(key, failure);
        }
    }

    private void reportError(String key, PolyglotException failure) {
        String detail = failure.getMessage() == null
                ? failure.getClass().getSimpleName() : failure.getMessage();
        if (failure.getSourceLocation() != null) {
            var location = failure.getSourceLocation();
            detail += " (" + location.getSource().getName() + ":"
                    + location.getStartLine() + ":" + location.getStartColumn() + ")";
        }
        String message = "Custom-Element-Callback '" + key + "' warf: " + detail;
        if (reportedErrors.add(message)) {
            errorSink.accept(message);
        }
    }

    private void queue(Runnable event) {
        pending.addLast(event);
        if (!deliveryScheduled) {
            deliveryScheduled = true;
            enqueueDelivery.run();
        }
    }

    private static String tagName(Element element) {
        return element.getNodeName().toLowerCase(Locale.ROOT);
    }

    private static boolean connected(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof Document) {
                return true;
            }
        }
        return false;
    }

    private static List<String> observedAttributesOf(Value ctor) {
        try {
            Value observed = ctor.getMember("observedAttributes");
            if (observed == null || observed.isNull() || !observed.hasArrayElements()) {
                return List.of();
            }
            List<String> attributes = new ArrayList<>();
            for (long index = 0; index < observed.getArraySize(); index++) {
                Value attribute = observed.getArrayElement(index);
                attributes.add(attribute.isString() ? attribute.asString()
                        : attribute.toString());
            }
            return List.copyOf(attributes);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    @Override
    public void close() {
        document.removeMutationListener(listener);
        pending.clear();
        deliveryScheduled = false;
    }
}

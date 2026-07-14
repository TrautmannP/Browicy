package com.browicy.engine.js;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.DocumentMutationListener;
import com.browicy.engine.dom.DomMutation;
import com.browicy.engine.dom.Node;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongConsumer;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

final class JsMutationObserverRegistry implements AutoCloseable {

    private final Document document;
    private final JsDocument jsDocument;
    private final LongConsumer scheduleDelivery;
    private final Map<Long, ObserverState> observers = new HashMap<>();
    private final DocumentMutationListener listener = this::record;

    JsMutationObserverRegistry(Document document, JsDocument jsDocument,
                               LongConsumer scheduleDelivery) {
        this.document = Objects.requireNonNull(document, "document");
        this.jsDocument = Objects.requireNonNull(jsDocument, "jsDocument");
        this.scheduleDelivery = Objects.requireNonNull(scheduleDelivery, "scheduleDelivery");
        document.addMutationListener(listener);
    }

    synchronized Object observe(Value[] arguments) {
        if (arguments.length < 9 || !arguments[0].fitsInLong()) {
            throw new IllegalArgumentException("MutationObserver.observe: ungültige Argumente");
        }
        long observerId = arguments[0].asLong();
        Node target = node(arguments[1]);
        ObserverOptions options = new ObserverOptions(
                arguments[2].asBoolean(), arguments[3].asBoolean(),
                arguments[4].asBoolean(), arguments[5].asBoolean(),
                arguments[6].asBoolean(), arguments[7].asBoolean(),
                strings(arguments[8]));
        ObserverState state = observers.computeIfAbsent(observerId, ignored -> new ObserverState());
        state.registrations.removeIf(registration -> registration.target() == target);
        state.registrations.add(new Registration(target, options));
        return null;
    }

    synchronized Object disconnect(Value[] arguments) {
        ObserverState state = observers.remove(id(arguments));
        if (state != null) {
            state.records.clear();
        }
        return null;
    }

    synchronized Object takeRecords(Value[] arguments) {
        return ProxyArray.fromList(drain(id(arguments)));
    }

    synchronized List<Object> drain(long observerId) {
        ObserverState state = observers.get(observerId);
        if (state == null || state.records.isEmpty()) {
            return List.of();
        }
        List<Object> result = state.records.stream().map(this::toRecord).toList();
        state.records.clear();
        state.deliveryScheduled = false;
        return result;
    }

    private synchronized void record(DomMutation mutation) {
        for (Map.Entry<Long, ObserverState> entry : observers.entrySet()) {
            ObserverState state = entry.getValue();
            boolean matched = false;
            boolean includeOldValue = false;
            for (Registration registration : state.registrations) {
                if (registration.matches(mutation)) {
                    matched = true;
                    includeOldValue |= registration.options().requestsOldValue(mutation);
                }
            }
            if (!matched) {
                continue;
            }
            state.records.add(new PendingRecord(mutation, includeOldValue));
            if (!state.deliveryScheduled) {
                state.deliveryScheduled = true;
                scheduleDelivery.accept(entry.getKey());
            }
        }
    }

    private Object toRecord(PendingRecord pending) {
        DomMutation mutation = pending.mutation();
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("target", jsDocument.wrap(mutation.target()));
        record.put("addedNodes", ProxyArray.fromArray());
        record.put("removedNodes", ProxyArray.fromArray());
        record.put("previousSibling", null);
        record.put("nextSibling", null);
        record.put("attributeName", null);
        record.put("attributeNamespace", null);
        record.put("oldValue", null);
        switch (mutation) {
            case DomMutation.ChildListChanged childList -> {
                record.put("type", "childList");
                record.put("addedNodes", ProxyArray.fromList(
                        childList.addedNodes().stream().map(jsDocument::wrap).toList()));
                record.put("removedNodes", ProxyArray.fromList(
                        childList.removedNodes().stream().map(jsDocument::wrap).toList()));
                record.put("previousSibling", jsDocument.wrap(childList.previousSibling()));
                record.put("nextSibling", jsDocument.wrap(childList.nextSibling()));
            }
            case DomMutation.AttributeChanged attribute -> {
                record.put("type", "attributes");
                record.put("attributeName", attribute.attributeName());
                if (pending.includeOldValue()) {
                    record.put("oldValue", attribute.oldValue());
                }
            }
            case DomMutation.CharacterDataChanged characterData -> {
                record.put("type", "characterData");
                if (pending.includeOldValue()) {
                    record.put("oldValue", characterData.oldValue());
                }
            }
            case DomMutation.StateChanged ignored ->
                    throw new IllegalStateException("UI-Zustände sind keine DOM-Mutationen");
        }
        return ProxyObject.fromMap(record);
    }

    @Override
    public synchronized void close() {
        document.removeMutationListener(listener);
        observers.clear();
    }

    private static long id(Value[] arguments) {
        if (arguments.length == 0 || !arguments[0].fitsInLong()) {
            throw new IllegalArgumentException("MutationObserver: ungültige Observer-ID");
        }
        return arguments[0].asLong();
    }

    private static Node node(Value value) {
        if (!value.isProxyObject() || !(value.asProxyObject() instanceof JsNodeLike wrapper)) {
            throw new IllegalArgumentException("MutationObserver.observe: target muss ein Node sein");
        }
        return wrapper.unwrapNode();
    }

    private static List<String> strings(Value value) {
        if (!value.hasArrayElements()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (long index = 0; index < value.getArraySize(); index++) {
            Value item = value.getArrayElement(index);
            result.add(item.isString() ? item.asString() : item.toString());
        }
        return List.copyOf(result);
    }

    private static final class ObserverState {
        private final List<Registration> registrations = new ArrayList<>();
        private final List<PendingRecord> records = new ArrayList<>();
        private boolean deliveryScheduled;
    }

    private record PendingRecord(DomMutation mutation, boolean includeOldValue) { }

    private record Registration(Node target, ObserverOptions options) {
        boolean matches(DomMutation mutation) {
            if (mutation.target() != target
                    && (!options.subtree() || !target.contains(mutation.target()))) {
                return false;
            }
            return switch (mutation) {
                case DomMutation.ChildListChanged ignored -> options.childList();
                case DomMutation.CharacterDataChanged ignored -> options.characterData();
                case DomMutation.AttributeChanged attribute -> options.attributes()
                        && (options.attributeFilter().isEmpty()
                        || options.attributeFilter().contains(attribute.attributeName()));
                case DomMutation.StateChanged ignored -> false;
            };
        }
    }

    private record ObserverOptions(boolean childList, boolean attributes,
                                   boolean characterData, boolean subtree,
                                   boolean attributeOldValue, boolean characterDataOldValue,
                                   List<String> attributeFilter) {
        boolean requestsOldValue(DomMutation mutation) {
            return mutation instanceof DomMutation.AttributeChanged && attributeOldValue
                    || mutation instanceof DomMutation.CharacterDataChanged && characterDataOldValue;
        }
    }
}

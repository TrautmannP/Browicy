package com.browicy.engine;

import com.browicy.engine.css.StyleApplicator;
import com.browicy.engine.css.StyleSheetRegistry;
import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.DocumentMutationListener;
import com.browicy.engine.dom.DomMutation;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Batches DOM/style invalidations and flushes at most once per event-loop turn. */
public final class DocumentUpdateCoordinator implements AutoCloseable {

    private static final System.Logger LOGGER =
            System.getLogger(DocumentUpdateCoordinator.class.getName());

    private final Document document;
    private final StyleSheetRegistry styleSheets;
    private final StyleApplicator styleApplicator;
    private final PageUpdateListener listener;
    private final DocumentMutationListener mutationListener = this::record;
    private final List<DomMutation> mutations = new ArrayList<>();
    private final Set<URI> changedStyleSheets = new LinkedHashSet<>();

    private InvalidationType invalidation;
    private boolean notificationsEnabled;
    private volatile boolean closed;

    public DocumentUpdateCoordinator(Document document,
                                     StyleSheetRegistry styleSheets,
                                     StyleApplicator styleApplicator,
                                     PageUpdateListener listener) {
        this.document = Objects.requireNonNull(document, "document");
        this.styleSheets = Objects.requireNonNull(styleSheets, "styleSheets");
        this.styleApplicator = Objects.requireNonNull(styleApplicator, "styleApplicator");
        this.listener = Objects.requireNonNull(listener, "listener");
        document.addMutationListener(mutationListener);
    }

    public synchronized void record(DomMutation mutation) {
        if (closed) {
            return;
        }
        mutations.add(Objects.requireNonNull(mutation, "mutation"));
        invalidation = merge(invalidation, invalidationFor(mutation));
    }

    public synchronized void stylesheetChanged(URI uri) {
        if (closed) {
            return;
        }
        changedStyleSheets.add(Objects.requireNonNull(uri, "uri"));
        invalidation = merge(invalidation, InvalidationType.STYLE);
    }

    public synchronized void invalidate(InvalidationType type) {
        if (!closed) {
            invalidation = merge(invalidation, Objects.requireNonNull(type, "type"));
        }
    }

    public synchronized void enableNotifications() {
        notificationsEnabled = true;
    }

    public void flush() {
        PendingUpdate pending;
        synchronized (this) {
            if (closed || invalidation == null) {
                return;
            }
            pending = new PendingUpdate(
                    invalidation, List.copyOf(mutations), List.copyOf(changedStyleSheets),
                    notificationsEnabled);
            invalidation = null;
            mutations.clear();
            changedStyleSheets.clear();
        }

        if (closed) {
            return;
        }
        if (pending.invalidation().requires(InvalidationType.STYLE)) {
            synchronized (document) {
                styleApplicator.apply(document, styleSheets);
            }
        }
        if (!pending.publish() || closed) {
            return;
        }

        PageUpdate update = pending.stylesheets().isEmpty()
                ? new PageUpdate.DocumentChanged(
                        document, pending.invalidation(), pending.mutations())
                : new PageUpdate.StylesChanged(
                        document, pending.invalidation(), pending.stylesheets(), pending.mutations());
        notifySafely(update);
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        mutations.clear();
        changedStyleSheets.clear();
        invalidation = null;
        document.removeMutationListener(mutationListener);
    }

    private void notifySafely(PageUpdate update) {
        if (closed) {
            return;
        }
        try {
            listener.onUpdate(update);
        } catch (RuntimeException failure) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Listener einer Seitenaktualisierung warf eine Exception", failure);
        }
    }

    private static InvalidationType invalidationFor(DomMutation mutation) {
        return switch (mutation) {
            case DomMutation.ChildListChanged ignored -> InvalidationType.STYLE;
            case DomMutation.AttributeChanged ignored -> InvalidationType.STYLE;
            case DomMutation.CharacterDataChanged ignored -> InvalidationType.RENDER_TREE;
        };
    }

    private static InvalidationType merge(InvalidationType current, InvalidationType next) {
        return current == null ? next : current.merge(next);
    }

    private record PendingUpdate(InvalidationType invalidation,
                                 List<DomMutation> mutations,
                                 List<URI> stylesheets,
                                 boolean publish) {
    }
}

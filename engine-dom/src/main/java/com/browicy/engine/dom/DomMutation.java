package com.browicy.engine.dom;

import java.util.List;
import java.util.Objects;

/**
 * Internal, document-scoped description of a completed DOM mutation.
 *
 * <p>The records intentionally contain immutable snapshots so listeners may batch them without
 * observing later changes to the participating nodes.</p>
 */
public sealed interface DomMutation
        permits DomMutation.ChildListChanged, DomMutation.AttributeChanged,
                DomMutation.CharacterDataChanged {

    Node target();

    record ChildListChanged(Node target, List<Node> addedNodes, List<Node> removedNodes)
            implements DomMutation {
        public ChildListChanged {
            Objects.requireNonNull(target, "target");
            addedNodes = List.copyOf(addedNodes);
            removedNodes = List.copyOf(removedNodes);
        }
    }

    record AttributeChanged(Element target,
                            String attributeName,
                            String oldValue,
                            String newValue) implements DomMutation {
        public AttributeChanged {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(attributeName, "attributeName");
        }
    }

    record CharacterDataChanged(Node target, String oldValue, String newValue)
            implements DomMutation {
        public CharacterDataChanged {
            Objects.requireNonNull(target, "target");
            oldValue = oldValue == null ? "" : oldValue;
            newValue = newValue == null ? "" : newValue;
        }
    }
}

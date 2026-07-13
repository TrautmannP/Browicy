package com.browicy.engine.dom;

import java.util.List;
import java.util.Objects;

public sealed interface DomMutation
        permits DomMutation.ChildListChanged, DomMutation.AttributeChanged,
                DomMutation.CharacterDataChanged {

    Node target();

    record ChildListChanged(Node target, List<Node> addedNodes, List<Node> removedNodes,
                            Node previousSibling, Node nextSibling)
            implements DomMutation {
        public ChildListChanged(Node target, List<Node> addedNodes, List<Node> removedNodes) {
            this(target, addedNodes, removedNodes, null, null);
        }

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

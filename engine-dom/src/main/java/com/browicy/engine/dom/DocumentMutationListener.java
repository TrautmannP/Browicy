package com.browicy.engine.dom;

/** Receives completed mutations for nodes currently connected to a document. */
@FunctionalInterface
public interface DocumentMutationListener {

    void onMutation(DomMutation mutation);
}

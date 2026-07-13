package com.browicy.engine.dom;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DocumentMutationTest {

    @Test
    public void reportsSuccessfulConnectedMutationsWithOldAndNewValues() {
        Document document = document();
        Element body = document.getBody();
        TextNode text = document.createTextNode("vorher");
        body.appendChild(text);
        List<DomMutation> mutations = new ArrayList<>();
        document.addMutationListener(mutations::add);

        body.setAttribute("class", "open");
        body.setAttribute("class", "open");
        text.setData("nachher");
        Element child = document.createElement("span");
        body.appendChild(child);
        body.removeChild(child);

        assertEquals(4, mutations.size());
        DomMutation.AttributeChanged attribute =
                (DomMutation.AttributeChanged) mutations.get(0);
        assertSame(body, attribute.target());
        assertEquals("class", attribute.attributeName());
        assertEquals(null, attribute.oldValue());
        assertEquals("open", attribute.newValue());

        DomMutation.CharacterDataChanged characterData =
                (DomMutation.CharacterDataChanged) mutations.get(1);
        assertSame(text, characterData.target());
        assertEquals("vorher", characterData.oldValue());
        assertEquals("nachher", characterData.newValue());

        DomMutation.ChildListChanged added =
                (DomMutation.ChildListChanged) mutations.get(2);
        assertEquals(List.of(child), added.addedNodes());
        assertTrue(added.removedNodes().isEmpty());

        DomMutation.ChildListChanged removed =
                (DomMutation.ChildListChanged) mutations.get(3);
        assertTrue(removed.addedNodes().isEmpty());
        assertEquals(List.of(child), removed.removedNodes());
    }

    @Test
    public void failedDomOperationDoesNotEmitMutation() {
        Document document = document();
        Element body = document.getBody();
        List<DomMutation> mutations = new ArrayList<>();
        document.addMutationListener(mutations::add);

        assertThrows(DomException.class, () -> body.appendChild(body));

        assertTrue(mutations.isEmpty());
    }

    @Test
    public void failingMutationListenerCannotUndoSuccessfulDomOperation() {
        Document document = document();
        Element body = document.getBody();
        document.addMutationListener(mutation -> {
            throw new IllegalStateException("kaputter Beobachter");
        });

        body.setAttribute("data-state", "ready");

        assertEquals("ready", body.getAttribute("data-state"));
    }

    @Test
    public void readyStateOnlyAdvances() {
        Document document = document();

        assertEquals(DocumentReadyState.LOADING, document.getReadyState());
        document.transitionTo(DocumentReadyState.INTERACTIVE);
        document.transitionTo(DocumentReadyState.COMPLETE);

        assertEquals(DocumentReadyState.COMPLETE, document.getReadyState());
        assertThrows(IllegalStateException.class,
                () -> document.transitionTo(DocumentReadyState.LOADING));
    }

    private static Document document() {
        Document document = new Document("about:test");
        Element html = document.createElement("html");
        Element body = document.createElement("body");
        document.appendChild(html);
        html.appendChild(body);
        return document;
    }
}

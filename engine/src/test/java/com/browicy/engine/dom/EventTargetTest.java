package com.browicy.engine.dom;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class EventTargetTest {

    @Test
    public void dispatchUsesCaptureTargetAndBubblePhases() {
        Document document = new Document("about:test");
        Element body = new Element("body");
        Element parent = new Element("div");
        Element target = new Element("button");
        document.appendChild(body);
        body.appendChild(parent);
        parent.appendChild(target);

        List<String> calls = new ArrayList<>();
        addRecordingListener(document, "document-capture", true, calls, target, Event.CAPTURING_PHASE);
        addRecordingListener(body, "body-capture", true, calls, target, Event.CAPTURING_PHASE);
        addRecordingListener(parent, "parent-capture", true, calls, target, Event.CAPTURING_PHASE);
        addRecordingListener(target, "target-capture", true, calls, target, Event.AT_TARGET);
        addRecordingListener(target, "target-bubble", false, calls, target, Event.AT_TARGET);
        addRecordingListener(parent, "parent-bubble", false, calls, target, Event.BUBBLING_PHASE);
        addRecordingListener(body, "body-bubble", false, calls, target, Event.BUBBLING_PHASE);
        addRecordingListener(document, "document-bubble", false, calls, target, Event.BUBBLING_PHASE);

        Event event = new Event("activate", true, true);
        assertTrue(target.dispatchEvent(event));

        assertEquals(List.of(
                "document-capture", "body-capture", "parent-capture",
                "target-capture", "target-bubble",
                "parent-bubble", "body-bubble", "document-bubble"), calls);
        assertSame(target, event.getTarget());
        assertNull(event.getCurrentTarget());
        assertEquals(Event.NONE, event.getEventPhase());
    }

    @Test
    public void stopPropagationFinishesCurrentTargetButStopsThePath() {
        Document document = new Document("about:test");
        Element parent = new Element("div");
        Element target = new Element("button");
        document.appendChild(parent);
        parent.appendChild(target);

        List<String> calls = new ArrayList<>();
        parent.addEventListener("click", event -> {
            calls.add("first");
            event.stopPropagation();
        }, true);
        parent.addEventListener("click", event -> calls.add("second"), true);
        target.addEventListener("click", event -> calls.add("target"), false);

        target.dispatchEvent(new Event("click", true, true));

        assertEquals(List.of("first", "second"), calls);
    }

    @Test
    public void preventDefaultControlsDispatchReturnValueOnlyForCancelableEvents() {
        Element target = new Element("button");
        target.addEventListener("submit", Event::preventDefault, false);

        Event cancelable = new Event("submit", true, true);
        assertFalse(target.dispatchEvent(cancelable));
        assertTrue(cancelable.isDefaultPrevented());

        Event fixed = new Event("submit", true, false);
        assertTrue(target.dispatchEvent(fixed));
        assertFalse(fixed.isDefaultPrevented());
    }

    @Test
    public void listenerChangesDuringDispatchFollowDomRules() {
        Element target = new Element("button");
        List<String> calls = new ArrayList<>();
        EventListener late = event -> calls.add("late");
        EventListener removed = event -> calls.add("removed");

        target.addEventListener("test", event -> {
            calls.add("first");
            target.removeEventListener("test", removed, false);
            target.addEventListener("test", late, false);
        }, false);
        target.addEventListener("test", removed, false);

        target.dispatchEvent(new Event("test", false, false));
        assertEquals(List.of("first"), calls);

        calls.clear();
        target.dispatchEvent(new Event("test", false, false));
        assertEquals(List.of("first", "late"), calls);
    }

    private static void addRecordingListener(Node node, String name, boolean capture,
                                             List<String> calls, Node expectedTarget,
                                             short expectedPhase) {
        node.addEventListener("activate", event -> {
            assertSame(expectedTarget, event.getTarget());
            assertSame(node, event.getCurrentTarget());
            assertEquals(expectedPhase, event.getEventPhase());
            calls.add(name);
        }, capture);
    }
}

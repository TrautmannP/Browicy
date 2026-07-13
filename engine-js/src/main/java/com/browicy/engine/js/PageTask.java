package com.browicy.engine.js;

import com.browicy.engine.dom.Event;
import com.browicy.engine.dom.Node;
import java.util.Objects;

/** A unit of work that must execute on a page's single JavaScript thread. */
public sealed interface PageTask
        permits PageTask.Script, PageTask.DomEvent, PageTask.Callback, PageTask.Timer {

    record Script(JavaScriptSource source) implements PageTask {
        public Script {
            Objects.requireNonNull(source, "source");
        }
    }

    record DomEvent(Node target, Event event) implements PageTask {
        public DomEvent {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(event, "event");
        }
    }

    record Callback(Runnable callback) implements PageTask {
        public Callback {
            Objects.requireNonNull(callback, "callback");
        }
    }

    record Timer(long timerId, Runnable callback) implements PageTask {
        public Timer {
            if (timerId <= 0) {
                throw new IllegalArgumentException("Timer-ID muss positiv sein");
            }
            Objects.requireNonNull(callback, "callback");
        }
    }
}

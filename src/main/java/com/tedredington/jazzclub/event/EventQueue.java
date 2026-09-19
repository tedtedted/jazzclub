package com.tedredington.jazzclub.event;

import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/** The single hand-over point between producer threads and the main loop. */
public final class EventQueue {

    private final BlockingDeque<Event> events = new LinkedBlockingDeque<>();

    public void publish(Event event) {
        events.addLast(event);
    }

    /** Blocks until an event is available. */
    public Event take() throws InterruptedException {
        return events.takeFirst();
    }

    /** Puts events back at the head, keeping their order; used by prompts for events they set aside. */
    public void putBack(List<? extends Event> deferred) {
        for (Event event : deferred.reversed()) {
            events.addFirst(event);
        }
    }
}

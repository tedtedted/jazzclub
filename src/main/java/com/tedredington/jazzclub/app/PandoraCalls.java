package com.tedredington.jazzclub.app;

import java.util.function.Supplier;

import com.tedredington.jazzclub.app.event.EventResult;
import com.tedredington.jazzclub.app.event.EventType;
import com.tedredington.jazzclub.app.event.PlayerEvents;
import com.tedredington.jazzclub.pandora.error.PandoraException;
import com.tedredington.jazzclub.ui.Console;

/**
 * The shape every user-visible Pandora call has in pianobar, in one place: announce it, do it, say
 * {@code Ok.}, and tell listeners how it went. On failure the event is still emitted, with the error,
 * and the exception travels on to whoever completes the announcement with {@code Error: ...}.
 */
public final class PandoraCalls {

    private final Console console;
    private final PlayerEvents events;

    public PandoraCalls(Console console, PlayerEvents events) {
        this.console = console;
        this.events = events;
    }

    /**
     * @param announcement e.g. {@code "Loving song... "}
     * @param event        what to emit afterwards, or {@code null} for calls pianobar has no event for
     */
    public <T> T call(String announcement, EventType event, Selection selection, Supplier<T> call) {
        console.info(announcement);
        T value;
        try {
            value = call.get();
        } catch (PandoraException e) {
            emit(event, selection, EventResult.of(e));
            throw e;
        }
        console.append("Ok.\n");
        emit(event, selection, EventResult.OK);
        return value;
    }

    public void run(String announcement, EventType event, Selection selection, Runnable call) {
        call(announcement, event, selection, () -> {
            call.run();
            return null;
        });
    }

    private void emit(EventType event, Selection selection, EventResult result) {
        if (event != null) {
            events.emit(event, selection, result);
        }
    }
}

package com.tedredington.jazzclub.app;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.tedredington.jazzclub.pandora.error.PandoraException;
import com.tedredington.jazzclub.ui.Console;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Routes a key press to its action, if the action's precondition holds. */
public final class ActionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ActionDispatcher.class);

    private final KeyBindings bindings;
    private final PlaybackState state;
    private final Console console;
    private final Map<ActionId, KeyAction> actions = new EnumMap<>(ActionId.class);

    public ActionDispatcher(KeyBindings bindings, PlaybackState state, Console console, List<KeyAction> actions) {
        this.bindings = bindings;
        this.state = state;
        this.console = console;
        for (KeyAction action : actions) {
            for (ActionId id : action.ids()) {
                if (this.actions.put(id, action) != null) {
                    throw new IllegalStateException("Two implementations for " + id);
                }
            }
        }
        for (ActionId id : ActionId.values()) {
            if (!this.actions.containsKey(id)) {
                throw new IllegalStateException("No implementation for " + id);
            }
        }
    }

    public void dispatch(char key) {
        bindings.actionFor(key).filter(this::applicable).ifPresent(id -> {
            try {
                actions.get(id).execute(id);
            } catch (PandoraException e) {
                // the action has typically printed "(i) Doing something... " already
                log.debug("{} failed", id, e);
                console.append("Error: " + e.getMessage() + "\n");
            }
        });
    }

    private boolean applicable(ActionId id) {
        return switch (id.requires()) {
            case NOTHING -> true;
            case STATION -> state.station().isPresent();
            case SONG -> state.song().isPresent();
        };
    }
}

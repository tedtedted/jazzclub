package com.tedredington.jazzclub.app;

import java.util.Optional;

/**
 * Handed to every action: what it applies to, and a way to trigger another action, which the history
 * menu needs ("what to do with this song?").
 */
public record ActionContext(Selection selection, Redispatch redispatch) {

    @FunctionalInterface
    public interface Redispatch {

        /** @return the action that ran, or empty if the key is unbound or does not apply */
        Optional<ActionId> dispatch(char key, Selection selection);
    }
}

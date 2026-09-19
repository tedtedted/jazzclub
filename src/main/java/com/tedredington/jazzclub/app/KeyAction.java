package com.tedredington.jazzclub.app;

/** What happens when a bound key is pressed. Implementations are Spring beans, collected by the dispatcher. */
public interface KeyAction {

    /** The actions served; several ids may share one implementation (pause has two keys). */
    java.util.Set<ActionId> ids();

    void execute(ActionId id);
}

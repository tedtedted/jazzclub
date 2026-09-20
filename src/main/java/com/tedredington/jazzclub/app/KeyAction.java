package com.tedredington.jazzclub.app;

import java.util.Set;

/** What happens when a bound key is pressed. Implementations are Spring beans, collected by the dispatcher. */
public interface KeyAction {

    /** The actions served; several ids may share one implementation (pause has two keys). */
    Set<ActionId> ids();

    /** Only called when {@code context.selection()} satisfies {@link ActionId#requires()}. */
    void execute(ActionId id, ActionContext context);
}

package com.tedredington.jazzclub.app.action;

import java.util.Set;

import com.tedredington.jazzclub.app.ActionId;
import com.tedredington.jazzclub.app.KeyAction;
import com.tedredington.jazzclub.app.KeyBindings;
import com.tedredington.jazzclub.ui.Console;
import org.springframework.stereotype.Component;

@Component
class HelpAction implements KeyAction {

    private final KeyBindings bindings;
    private final Console console;

    HelpAction(KeyBindings bindings, Console console) {
        this.bindings = bindings;
        this.console = console;
    }

    @Override
    public Set<ActionId> ids() {
        return Set.of(ActionId.HELP);
    }

    @Override
    public void execute(ActionId id) {
        console.append("\r");
        for (ActionId action : ActionId.values()) {
            if (action.helpText() != null) {
                bindings.keyFor(action).ifPresent(key -> console.list(key + "    " + action.helpText() + "\n"));
            }
        }
    }
}

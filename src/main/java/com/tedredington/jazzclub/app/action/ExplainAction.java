package com.tedredington.jazzclub.app.action;

import java.util.Set;

import com.tedredington.jazzclub.app.ActionContext;
import com.tedredington.jazzclub.app.ActionId;
import com.tedredington.jazzclub.app.KeyAction;
import com.tedredington.jazzclub.app.PandoraCalls;
import com.tedredington.jazzclub.app.event.EventType;
import com.tedredington.jazzclub.pandora.PandoraClient;
import com.tedredington.jazzclub.ui.Console;
import org.springframework.stereotype.Component;

@Component
class ExplainAction implements KeyAction {

    private final PandoraClient client;
    private final PandoraCalls calls;
    private final Console console;

    ExplainAction(PandoraClient client, PandoraCalls calls, Console console) {
        this.client = client;
        this.calls = calls;
        this.console = console;
    }

    @Override
    public Set<ActionId> ids() {
        return Set.of(ActionId.SONG_EXPLAIN);
    }

    @Override
    public void execute(ActionId id, ActionContext context) {
        calls.call("Receiving explanation... ", EventType.SONG_EXPLAIN, context.selection(),
                        () -> client.explain(context.selection().song()))
                .ifPresentOrElse(
                        text -> console.info(text + "\n"),
                        () -> console.error("No explanation provided.\n"));
    }
}

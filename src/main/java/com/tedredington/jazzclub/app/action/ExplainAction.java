package com.tedredington.jazzclub.app.action;

import java.util.Set;

import com.tedredington.jazzclub.app.ActionId;
import com.tedredington.jazzclub.app.KeyAction;
import com.tedredington.jazzclub.app.PlaybackState;
import com.tedredington.jazzclub.pandora.PandoraClient;
import com.tedredington.jazzclub.ui.Console;
import org.springframework.stereotype.Component;

@Component
class ExplainAction implements KeyAction {

    private final PandoraClient client;
    private final PlaybackState state;
    private final Console console;

    ExplainAction(PandoraClient client, PlaybackState state, Console console) {
        this.client = client;
        this.state = state;
        this.console = console;
    }

    @Override
    public Set<ActionId> ids() {
        return Set.of(ActionId.SONG_EXPLAIN);
    }

    @Override
    public void execute(ActionId id) {
        console.info("Receiving explanation... ");
        var explanation = client.explain(state.song().orElseThrow());
        console.append("Ok.\n");
        explanation.ifPresentOrElse(
                text -> console.info(text + "\n"),
                () -> console.error("No explanation provided.\n"));
    }
}

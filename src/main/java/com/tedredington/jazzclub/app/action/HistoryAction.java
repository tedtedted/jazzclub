package com.tedredington.jazzclub.app.action;

import java.util.Optional;
import java.util.Set;

import com.tedredington.jazzclub.app.ActionContext;
import com.tedredington.jazzclub.app.ActionId;
import com.tedredington.jazzclub.app.KeyAction;
import com.tedredington.jazzclub.app.ListPicker;
import com.tedredington.jazzclub.app.PlaybackState;
import com.tedredington.jazzclub.app.Selection;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.pandora.model.Station;
import com.tedredington.jazzclub.ui.Console;
import com.tedredington.jazzclub.ui.MessageType;
import com.tedredington.jazzclub.ui.Prompter;
import com.tedredington.jazzclub.ui.Renderer;
import org.springframework.stereotype.Component;

/**
 * Pick a song that already played, then press any action key to apply it to that song: love it
 * after all, ban it, bookmark it, make a station from it.
 */
@Component
class HistoryAction implements KeyAction {

    private final PlaybackState state;
    private final ListPicker picker;
    private final Prompter prompter;
    private final Console console;
    private final Renderer renderer;

    HistoryAction(PlaybackState state, ListPicker picker, Prompter prompter, Console console, Renderer renderer) {
        this.state = state;
        this.picker = picker;
        this.prompter = prompter;
        this.console = console;
        this.renderer = renderer;
    }

    @Override
    public Set<ActionId> ids() {
        return Set.of(ActionId.HISTORY);
    }

    @Override
    public void execute(ActionId id, ActionContext context) {
        if (state.history().isEmpty()) {
            console.info(state.historySize() == 0 ? "History disabled.\n" : "No history yet.\n");
            return;
        }
        String playing = state.station().map(Station::token).orElse("");
        Optional<Song> picked = picker.pick(state.history(),
                (index, song) -> renderer.listSong(index, song, otherStation(song, playing)),
                song -> song.artist() + " " + song.title(),
                "Select song: ");
        if (picked.isEmpty()) {
            return;
        }
        Optional<Station> songStation = state.findStation(picked.get().stationId());
        if (songStation.isEmpty()) {
            console.error("Station does not exist any more.\n");
            return;
        }

        Selection selection = new Selection(songStation.get(), picked.get());
        Optional<ActionId> ran;
        do {
            console.print(MessageType.QUESTION, "What to do with this song? ");
            ran = prompter.readChar(null).flatMap(key -> context.redispatch().dispatch(key, selection));
            // after the help screen the question is asked again, as in pianobar
        } while (ran.filter(action -> action == ActionId.HELP).isPresent());
    }

    private String otherStation(Song song, String playingStationToken) {
        if (playingStationToken.equals(song.stationId())) {
            return null;
        }
        return state.findStation(song.stationId()).map(Station::name).orElse("(deleted)");
    }
}

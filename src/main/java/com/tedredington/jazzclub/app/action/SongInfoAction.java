package com.tedredington.jazzclub.app.action;

import java.util.Set;

import com.tedredington.jazzclub.app.ActionContext;
import com.tedredington.jazzclub.app.ActionId;
import com.tedredington.jazzclub.app.KeyAction;
import com.tedredington.jazzclub.app.PlaybackState;
import com.tedredington.jazzclub.app.Selection;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.pandora.model.Station;
import com.tedredington.jazzclub.ui.Console;
import com.tedredington.jazzclub.ui.MessageType;
import com.tedredington.jazzclub.ui.Renderer;
import org.springframework.stereotype.Component;

/** Song info and the upcoming list: both only render what is already known. */
@Component
class SongInfoAction implements KeyAction {

    private final PlaybackState state;
    private final Console console;
    private final Renderer renderer;

    SongInfoAction(PlaybackState state, Console console, Renderer renderer) {
        this.state = state;
        this.console = console;
        this.renderer = renderer;
    }

    @Override
    public Set<ActionId> ids() {
        return Set.of(ActionId.SONG_INFO, ActionId.UPCOMING);
    }

    @Override
    public void execute(ActionId id, ActionContext context) {
        if (id == ActionId.SONG_INFO) {
            printInfo(context.selection());
        } else {
            printUpcoming();
        }
    }

    private void printInfo(Selection selection) {
        Song song = state.current(selection.song());
        selection.stationIfAny().ifPresent(station -> {
            console.print(MessageType.PLAYING, renderer.nowPlayingStation(station) + "\n");
            Station realStation = station.quickMix() ? state.findStation(song.stationId()).orElse(null) : null;
            console.print(MessageType.PLAYING, renderer.nowPlayingSong(song, realStation) + "\n");
        });
    }

    private void printUpcoming() {
        if (state.upcoming().isEmpty()) {
            console.info("No songs in queue.\n");
            return;
        }
        String playing = state.station().map(Station::token).orElse("");
        int index = 0;
        for (Song song : state.upcoming()) {
            String otherStation = playing.equals(song.stationId()) ? null
                    : state.findStation(song.stationId()).map(Station::name).orElse("(deleted)");
            console.list(renderer.listSong(index++, song, otherStation) + "\n");
        }
    }
}

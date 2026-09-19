package com.tedredington.jazzclub.app.action;

import java.util.Set;

import com.tedredington.jazzclub.app.ActionId;
import com.tedredington.jazzclub.app.KeyAction;
import com.tedredington.jazzclub.app.PlaybackState;
import com.tedredington.jazzclub.app.Radio;
import com.tedredington.jazzclub.pandora.PandoraClient;
import com.tedredington.jazzclub.pandora.model.Rating;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.ui.Console;
import org.springframework.stereotype.Component;

/** Love, ban and tired. The latter two also end the song, as there is no point in hearing it out. */
@Component
class RateSongAction implements KeyAction {

    private final PandoraClient client;
    private final PlaybackState state;
    private final Radio radio;
    private final Console console;

    RateSongAction(PandoraClient client, PlaybackState state, Radio radio, Console console) {
        this.client = client;
        this.state = state;
        this.radio = radio;
        this.console = console;
    }

    @Override
    public Set<ActionId> ids() {
        return Set.of(ActionId.SONG_LOVE, ActionId.SONG_BAN, ActionId.SONG_TIRED);
    }

    @Override
    public void execute(ActionId id) {
        Song song = state.song().orElseThrow();
        switch (id) {
            case SONG_LOVE -> {
                console.info("Loving song... ");
                client.addFeedback(song, true);
                state.updateSong(song.withRating(Rating.LOVE));
                console.append("Ok.\n");
            }
            case SONG_BAN -> {
                console.info("Banning song... ");
                client.addFeedback(song, false);
                state.updateSong(song.withRating(Rating.BAN));
                console.append("Ok.\n");
                radio.skip();
            }
            case SONG_TIRED -> {
                console.info("Putting song on shelf... ");
                client.sleepSong(song);
                state.updateSong(song.withRating(Rating.TIRED));
                console.append("Ok.\n");
                radio.skip();
            }
            default -> throw new IllegalArgumentException("Not a rating action: " + id);
        }
    }
}

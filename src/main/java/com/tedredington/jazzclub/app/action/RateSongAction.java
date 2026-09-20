package com.tedredington.jazzclub.app.action;

import java.util.Set;

import com.tedredington.jazzclub.app.ActionContext;
import com.tedredington.jazzclub.app.ActionId;
import com.tedredington.jazzclub.app.KeyAction;
import com.tedredington.jazzclub.app.PandoraCalls;
import com.tedredington.jazzclub.app.PlaybackState;
import com.tedredington.jazzclub.app.Radio;
import com.tedredington.jazzclub.app.Selection;
import com.tedredington.jazzclub.app.StationService;
import com.tedredington.jazzclub.app.event.EventType;
import com.tedredington.jazzclub.pandora.PandoraClient;
import com.tedredington.jazzclub.pandora.model.Rating;
import com.tedredington.jazzclub.pandora.model.Song;
import org.springframework.stereotype.Component;

/**
 * Love, ban and tired. A banned or shelved song is also skipped if it is the one playing; rated from
 * the history there is nothing to skip.
 */
@Component
class RateSongAction implements KeyAction {

    private final PandoraClient client;
    private final PandoraCalls calls;
    private final PlaybackState state;
    private final Radio radio;
    private final StationService stations;

    RateSongAction(PandoraClient client, PandoraCalls calls, PlaybackState state, Radio radio,
                   StationService stations) {
        this.client = client;
        this.calls = calls;
        this.state = state;
        this.radio = radio;
        this.stations = stations;
    }

    @Override
    public Set<ActionId> ids() {
        return Set.of(ActionId.SONG_LOVE, ActionId.SONG_BAN, ActionId.SONG_TIRED);
    }

    @Override
    public void execute(ActionId id, ActionContext context) {
        Selection selection = context.selection();
        Song song = selection.song();
        if (id != ActionId.SONG_TIRED) {
            // feedback lands on the song's own station, which on QuickMix is not the one playing
            state.findStation(song.stationId()).ifPresent(stations::transformIfShared);
        }
        switch (id) {
            case SONG_LOVE -> calls.run("Loving song... ", EventType.SONG_LOVE, selection, () -> {
                client.addFeedback(song, true);
                state.updateSong(song.withRating(Rating.LOVE));
            });
            case SONG_BAN -> {
                calls.run("Banning song... ", EventType.SONG_BAN, selection, () -> {
                    client.addFeedback(song, false);
                    state.updateSong(song.withRating(Rating.BAN));
                });
                skipIfPlaying(song);
            }
            case SONG_TIRED -> {
                calls.run("Putting song on shelf... ", EventType.SONG_SHELF, selection, () -> {
                    client.sleepSong(song);
                    state.updateSong(song.withRating(Rating.TIRED));
                });
                skipIfPlaying(song);
            }
            default -> throw new IllegalArgumentException("Not a rating action: " + id);
        }
    }

    private void skipIfPlaying(Song song) {
        if (state.isPlaying(song)) {
            radio.skip();
        }
    }
}

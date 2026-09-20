package com.tedredington.jazzclub.app;

import java.util.List;

import com.tedredington.jazzclub.app.event.EventResult;
import com.tedredington.jazzclub.app.event.EventType;
import com.tedredington.jazzclub.app.event.PlayerEvents;
import com.tedredington.jazzclub.pandora.PandoraClient;
import com.tedredington.jazzclub.pandora.error.PandoraException;
import com.tedredington.jazzclub.pandora.model.AudioQuality;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.pandora.model.Station;
import com.tedredington.jazzclub.player.AudioPlayer;
import com.tedredington.jazzclub.player.PlaybackResult;
import com.tedredington.jazzclub.ui.Console;
import com.tedredington.jazzclub.ui.MessageType;
import com.tedredington.jazzclub.ui.Renderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Keeps the music going: fetches playlists when the queue runs dry and starts the next song when one ends. */
public final class Radio {

    private static final Logger log = LoggerFactory.getLogger(Radio.class);
    private static final long NOTHING_PLAYING = -1;

    private final PandoraClient client;
    private final AudioPlayer player;
    private final PlaybackState state;
    private final Console console;
    private final Renderer renderer;
    private final AudioQuality quality;
    private final int maxRetry;
    private final PlayerEvents events;

    private long playbackId = NOTHING_PLAYING;
    private int consecutiveFailures;

    public Radio(PandoraClient client, AudioPlayer player, PlaybackState state, Console console, Renderer renderer,
                 AudioQuality quality, int maxRetry, PlayerEvents events) {
        this.events = events;
        this.client = client;
        this.player = player;
        this.state = state;
        this.console = console;
        this.renderer = renderer;
        this.quality = quality;
        this.maxRetry = maxRetry;
    }

    /** Switches to a station; the current song ends right away. */
    public void tune(Station station) {
        state.changeStation(station);
        consecutiveFailures = 0;
        console.print(MessageType.PLAYING, renderer.nowPlayingStation(station) + "\n");
        skip();
    }

    /** Drops what is queued and moves on, so a change to the playing station is heard right away. */
    public void restartStation() {
        state.station().ifPresent(state::changeStation);
        skip();
    }

    /** Stops the radio: the current song ends and nothing follows until a station is chosen again. */
    public void stop() {
        state.clearStation();
        if (player.isActive()) {
            player.stop();
        } else {
            state.finishSong();
        }
    }

    /** Ends the current song. The next one starts once the player confirms it has stopped. */
    public void skip() {
        if (player.isActive()) {
            player.stop();
        } else {
            playNext();
        }
    }

    public void onTrackFinished(long finishedId, PlaybackResult result) {
        if (finishedId != playbackId) {
            log.debug("Ignoring end of superseded playback {}", finishedId);
            return;
        }
        playbackId = NOTHING_PLAYING;
        events.emit(EventType.SONG_FINISH, state.selection(), EventResult.OK, result.played());
        switch (result.outcome()) {
            case COMPLETED -> consecutiveFailures = 0;
            case STOPPED -> { }
            case FAILED -> {
                console.error(result.detail() + "\n");
                if (++consecutiveFailures >= maxRetry) {
                    console.error("Too many playback errors in a row, stopping. Press s to pick a station.\n");
                    state.clearStation();
                }
            }
        }
        if (state.quitRequested()) {
            state.finishSong();
            return;
        }
        playNext();
    }

    private void playNext() {
        if (state.station().isEmpty() || (state.upcoming().isEmpty() && !fetchPlaylist())) {
            state.finishSong();
            return;
        }
        Song song = state.advance().orElseThrow();
        Station playing = state.station().orElseThrow();
        Station realStation = playing.quickMix() ? state.findStation(song.stationId()).orElse(null) : null;
        console.print(MessageType.PLAYING, renderer.nowPlayingSong(song, realStation) + "\n");
        playbackId = player.play(song.audioUrl(), song.gainDb());
        events.emit(EventType.SONG_START, state.selection(), EventResult.OK);
    }

    /**
     * The program is ending. The player's own end-of-track event will arrive after the main loop has
     * gone, so the song that is cut short is reported here; scrobblers decide on "songfinish".
     */
    public void shutdown() {
        if (playbackId != NOTHING_PLAYING) {
            playbackId = NOTHING_PLAYING;
            events.emit(EventType.SONG_FINISH, state.selection(), EventResult.OK, player.elapsed());
        }
        player.stop();
    }

    private boolean fetchPlaylist() {
        console.info("Receiving new playlist... ");
        List<Song> songs;
        try {
            songs = client.playlist(state.station().orElseThrow(), quality);
        } catch (PandoraException e) {
            log.debug("Fetching the playlist failed", e);
            console.append("Error: " + e.getMessage() + "\n");
            events.emit(EventType.STATION_FETCH_PLAYLIST, state.selection(), EventResult.of(e));
            state.clearStation();
            return false;
        }
        console.append("Ok.\n");
        events.emit(EventType.STATION_FETCH_PLAYLIST, state.selection(), EventResult.OK);
        if (songs.isEmpty()) {
            console.info("No tracks left.\n");
            state.clearStation();
            return false;
        }
        state.enqueue(songs);
        return true;
    }
}

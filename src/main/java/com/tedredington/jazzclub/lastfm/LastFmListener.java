package com.tedredington.jazzclub.lastfm;

import java.time.Instant;
import java.time.InstantSource;

import com.tedredington.jazzclub.app.event.PlayerEvent;
import com.tedredington.jazzclub.pandora.model.Song;
import org.springframework.context.event.EventListener;

/**
 * Turns player events into Last.fm updates: "now playing" when a song starts, a scrobble when it ends
 * after being heard long enough. The player knows nothing about this class; it is one listener among
 * possibly many, like the {@code event_command}.
 *
 * <p>Always a bean, doing nothing without {@code lastfm_user}: the native image fixes the set of beans
 * at build time, long before the config file is read.
 */
public final class LastFmListener implements AutoCloseable {

    private final Scrobbler scrobbler;
    private final InstantSource clock;
    /** Events arrive on the main loop's thread only. */
    private Started current;

    /** @param scrobbler {@code null} when scrobbling is off */
    LastFmListener(Scrobbler scrobbler, InstantSource clock) {
        this.scrobbler = scrobbler;
        this.clock = clock;
    }

    @EventListener
    public void on(PlayerEvent event) {
        Song song = event.song();
        if (scrobbler == null || song == null || !event.result().isOk()) {
            return;
        }
        Track track = Track.of(song);
        switch (event.type()) {
            case SONG_START -> {
                current = new Started(song.trackToken(), clock.instant());
                if (track.isComplete()) {
                    scrobbler.nowPlaying(track);
                }
            }
            case SONG_FINISH -> {
                // missing only if the song started before this listener existed; close enough
                Instant startedAt = current != null && current.trackToken().equals(song.trackToken())
                        ? current.at()
                        : clock.instant().minus(event.played());
                current = null;
                if (track.isComplete() && ScrobbleRules.counts(track.length(), event.played())) {
                    scrobbler.scrobble(new Scrobble(track, startedAt));
                }
            }
            default -> {
                // loves come in a later step
            }
        }
    }

    /** Lets the last scrobble out before the program ends. */
    @Override
    public void close() {
        if (scrobbler != null) {
            scrobbler.close();
        }
    }

    private record Started(String trackToken, Instant at) {
    }
}

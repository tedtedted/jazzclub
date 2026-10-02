package com.tedredington.jazzclub.lastfm;

import java.time.Instant;
import java.time.InstantSource;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.tedredington.jazzclub.app.event.PlayerEvent;
import com.tedredington.jazzclub.pandora.model.Rating;
import com.tedredington.jazzclub.pandora.model.Song;
import org.springframework.context.event.EventListener;

/**
 * Turns player events into Last.fm updates: "now playing" when a song starts, a scrobble when it ends
 * after being heard long enough, and loves. The player knows nothing about this class; it is one
 * listener among possibly many, like the {@code event_command}.
 *
 * <p>Loves follow Pandora's thumbs: a thumbs up loves the track on Last.fm, a ban takes the love back.
 * A song thumbed up long ago is loved when it next plays, so earlier likes reach Last.fm over time.
 *
 * <p>Always a bean, doing nothing without {@code lastfm_user}: the native image fixes the set of beans
 * at build time, long before the config file is read.
 */
public final class LastFmListener implements AutoCloseable {

    private final Scrobbler scrobbler;
    private final InstantSource clock;
    private final boolean love;
    /** Events arrive on the main loop's thread only, so neither field needs guarding. */
    private Started current;
    /** Loved on Last.fm during this run, so a song liked long ago is not loved again every time it plays. */
    private final Set<String> loved = new HashSet<>();

    /**
     * @param scrobbler {@code null} when scrobbling is off
     * @param love      {@code lastfm_love}
     */
    LastFmListener(Scrobbler scrobbler, InstantSource clock, boolean love) {
        this.scrobbler = scrobbler;
        this.clock = clock;
        this.love = love;
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
                    if (song.rating() == Rating.LOVE) {
                        love(track);
                    }
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
            case SONG_LOVE -> love(track);
            case SONG_BAN -> unlove(track);
            default -> {
                // "tired" says nothing about liking the song; the rest are not about a song at all
            }
        }
    }

    private void love(Track track) {
        if (love && track.isComplete() && loved.add(key(track))) {
            scrobbler.love(track);
        }
    }

    private void unlove(Track track) {
        if (love && track.isComplete()) {
            loved.remove(key(track));
            scrobbler.unlove(track);
        }
    }

    /** Last.fm matches artist and title without regard to case. */
    private static String key(Track track) {
        return (track.artist() + "\n" + track.title()).toLowerCase(Locale.ROOT);
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

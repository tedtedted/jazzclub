package com.tedredington.jazzclub.lastfm;

import java.time.Duration;
import java.util.Objects;

import com.tedredington.jazzclub.pandora.model.Song;

/**
 * A song as Last.fm sees it.
 *
 * @param album  may be {@code null}
 * @param length {@link Duration#ZERO} when unknown
 */
record Track(String artist, String title, String album, Duration length) {

    Track {
        Objects.requireNonNull(artist, "artist");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(length, "length");
    }

    static Track of(Song song) {
        return new Track(nullToEmpty(song.artist()), nullToEmpty(song.title()),
                song.album() == null || song.album().isBlank() ? null : song.album(), song.length());
    }

    /** Last.fm refuses a scrobble without artist or title; there is no point in asking. */
    boolean isComplete() {
        return !artist.isBlank() && !title.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.strip();
    }
}

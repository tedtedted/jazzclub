package com.tedredington.jazzclub.lastfm;

import java.time.Instant;
import java.util.Objects;

/** A track heard long enough to count, and when it started playing, which is what Last.fm files it under. */
record Scrobble(Track track, Instant startedAt) {

    Scrobble {
        Objects.requireNonNull(track, "track");
        Objects.requireNonNull(startedAt, "startedAt");
    }
}

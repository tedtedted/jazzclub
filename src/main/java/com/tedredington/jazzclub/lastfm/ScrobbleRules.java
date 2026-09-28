package com.tedredington.jazzclub.lastfm;

import java.time.Duration;

/**
 * Last.fm's definition of a listen: the track is longer than 30 seconds, and it played for half its
 * length or for 4 minutes, whichever comes first. See https://www.last.fm/api/scrobbling.
 *
 * <p>The rating plays no part: a song banned after it was heard long enough still counts.
 */
final class ScrobbleRules {

    static final Duration MINIMUM_LENGTH = Duration.ofSeconds(30);
    static final Duration ALWAYS_ENOUGH = Duration.ofMinutes(4);

    private ScrobbleRules() {
    }

    /** @param played time actually heard; pauses do not count */
    static boolean counts(Duration length, Duration played) {
        if (length.compareTo(MINIMUM_LENGTH) <= 0) {
            return false;
        }
        Duration half = length.dividedBy(2);
        Duration enough = half.compareTo(ALWAYS_ENOUGH) < 0 ? half : ALWAYS_ENOUGH;
        return played.compareTo(enough) >= 0;
    }
}

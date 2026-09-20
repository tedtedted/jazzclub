package com.tedredington.jazzclub.player;

import java.time.Duration;

/**
 * How a track ended.
 *
 * @param detail for {@link Outcome#FAILED}: a message fit for the user; otherwise {@code null}
 * @param played how much of the track was heard
 */
public record PlaybackResult(Outcome outcome, String detail, Duration played) {

    public enum Outcome {
        /** Played to the end. */
        COMPLETED,
        /** Stopped on request: skip, station change, quit. */
        STOPPED,
        /** Could not be played, or broke off. */
        FAILED
    }

    public PlaybackResult withPlayed(Duration newPlayed) {
        return new PlaybackResult(outcome, detail, newPlayed);
    }

    public static PlaybackResult completed() {
        return new PlaybackResult(Outcome.COMPLETED, null, Duration.ZERO);
    }

    public static PlaybackResult stopped() {
        return new PlaybackResult(Outcome.STOPPED, null, Duration.ZERO);
    }

    public static PlaybackResult failed(String detail) {
        return new PlaybackResult(Outcome.FAILED, detail, Duration.ZERO);
    }
}

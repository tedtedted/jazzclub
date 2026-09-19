package com.tedredington.jazzclub.player;

/**
 * How a track ended.
 *
 * @param detail for {@link Outcome#FAILED}: a message fit for the user; otherwise {@code null}
 */
public record PlaybackResult(Outcome outcome, String detail) {

    public enum Outcome {
        /** Played to the end. */
        COMPLETED,
        /** Stopped on request: skip, station change, quit. */
        STOPPED,
        /** Could not be played, or broke off. */
        FAILED
    }

    public static PlaybackResult completed() {
        return new PlaybackResult(Outcome.COMPLETED, null);
    }

    public static PlaybackResult stopped() {
        return new PlaybackResult(Outcome.STOPPED, null);
    }

    public static PlaybackResult failed(String detail) {
        return new PlaybackResult(Outcome.FAILED, detail);
    }
}

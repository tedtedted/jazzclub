package com.tedredington.jazzclub.event;

import com.tedredington.jazzclub.player.PlaybackResult;

/** Everything the main loop reacts to. Produced by the key reader, the player and the clock. */
public sealed interface Event {

    record KeyPressed(char key) implements Event {
    }

    /** @param playbackId identifies the track, so a late event for a track already replaced can be ignored */
    record TrackFinished(long playbackId, PlaybackResult result) implements Event {
    }

    /** Once a second, to refresh the elapsed time. */
    record Tick() implements Event {
    }

    /** Standard input reached its end, or the process was asked to terminate. */
    record InputClosed() implements Event {
    }
}

package com.tedredington.jazzclub.event;

import com.tedredington.jazzclub.player.PlaybackResult;
import com.tedredington.jazzclub.ui.MessageType;

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

    /**
     * A message from a background thread, e.g. the Last.fm scrobbler. Printed by the main loop between
     * key presses, so it cannot break up a prompt the user is typing into.
     *
     * @param text one line, without the trailing newline
     */
    record Notice(MessageType type, String text) implements Event {
    }
}

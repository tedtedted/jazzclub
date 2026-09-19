package com.tedredington.jazzclub.player;

/** Told exactly once per {@link AudioPlayer#play}, from the player's own thread. */
@FunctionalInterface
public interface PlaybackListener {

    void finished(long playbackId, PlaybackResult result);
}

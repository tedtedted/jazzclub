package com.tedredington.jazzclub.player;

import java.net.URI;
import java.time.Duration;

/** Plays one stream at a time. All methods return immediately; the end of a track is reported to the listener. */
public interface AudioPlayer {

    /**
     * Starts playing, replacing whatever is playing now.
     *
     * @param trackGainDb the track's ReplayGain correction
     * @return an id that the matching {@link PlaybackListener#finished} call will carry
     */
    long play(URI audioUrl, double trackGainDb);

    /** Ends the current track, if any. The listener is told {@code STOPPED}. */
    void stop();

    void setPaused(boolean paused);

    boolean isPaused();

    /** {@code true} from {@link #play} until the listener has been told. */
    boolean isActive();

    Duration elapsed();

    /** The user's volume correction in dB, pianobar's {@code volume}; applies immediately. */
    void setVolume(int volumeDb);

    int volume();
}

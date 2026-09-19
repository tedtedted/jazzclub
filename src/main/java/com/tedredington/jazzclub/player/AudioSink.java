package com.tedredington.jazzclub.player;

import java.io.IOException;
import java.time.Duration;

/** A device that accepts 44.1 kHz, 16 bit, stereo, little-endian PCM. One instance serves one track. */
public interface AudioSink extends AutoCloseable {

    void open() throws IOException;

    /** Blocks until the bytes are accepted. */
    void write(byte[] buffer, int offset, int length);

    /** Suspends or resumes output without losing buffered audio. */
    void setPaused(boolean paused);

    /** Requests a gain in dB; implementations clamp to what the device supports. */
    void setGain(double gainDb);

    /** Blocks until everything written has been heard. */
    void drain();

    Duration position();

    @Override
    void close();
}

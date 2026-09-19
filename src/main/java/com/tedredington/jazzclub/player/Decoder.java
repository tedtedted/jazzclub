package com.tedredington.jazzclub.player;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/** A running decode of one stream into PCM as {@link AudioSink} expects it. */
public interface Decoder extends AutoCloseable {

    InputStream pcm();

    /**
     * Call after {@link #pcm()} reached its end.
     *
     * @return {@code null} if decoding succeeded, otherwise what went wrong
     */
    String failure() throws InterruptedException;

    /** Aborts decoding; safe to call from another thread. */
    @Override
    void close();

    @FunctionalInterface
    interface Factory {

        /** @throws IOException if the decoder cannot be started, e.g. ffmpeg is not installed */
        Decoder open(URI audioUrl) throws IOException;
    }
}

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

        /** The same decoders, reading up to {@code capacityBytes} ahead of what is being played. */
        default Factory prefetching(int capacityBytes) {
            if (capacityBytes <= 0) {
                return this;
            }
            return audioUrl -> {
                Decoder decoder = open(audioUrl);
                PrefetchingInputStream pcm = new PrefetchingInputStream(decoder.pcm(), capacityBytes);
                return new Decoder() {
                    @Override
                    public InputStream pcm() {
                        return pcm;
                    }

                    @Override
                    public String failure() throws InterruptedException {
                        return decoder.failure();
                    }

                    /**
                     * The decoder first: ending the process gives the prefetch thread an end-of-file
                     * to leave on. Closing the stream under a blocked reader can hang instead.
                     */
                    @Override
                    public void close() {
                        decoder.close();
                        try {
                            pcm.close();
                        } catch (IOException e) {
                            // the decoder is gone already
                        }
                    }
                };
            };
        }
    }
}

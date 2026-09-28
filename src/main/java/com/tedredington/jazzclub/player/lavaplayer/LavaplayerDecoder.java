package com.tedredington.jazzclub.player.lavaplayer;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegFileLoader;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegTrackInfo;
import com.sedmelluq.discord.lavaplayer.container.mpeg.reader.MpegFileTrackProvider;
import com.tedredington.jazzclub.player.Decoder;
import com.tedredington.jazzclub.player.PcmFormat;

/**
 * Decodes Pandora's AAC-in-MP4 in-process: our {@link HttpSeekableStream} feeds LavaPlayer's MP4
 * parser, whose packets {@link AacPcmConsumer} decodes with fdk-aac. One thread per song does all
 * of it and writes into a pipe; {@link #pcm()} is the other end.
 */
public final class LavaplayerDecoder implements Decoder {

    private static final int PIPE_BYTES = 64 * 1024;

    private final PipedInputStream pcm;
    private final Thread worker;
    private volatile HttpSeekableStream stream;
    private volatile String failure;
    private volatile boolean closed;

    private LavaplayerDecoder(HttpClient http, URI audioUrl, PcmFormat format) throws IOException {
        pcm = new PipedInputStream(PIPE_BYTES);
        PipedOutputStream out = new PipedOutputStream(pcm);
        worker = Thread.ofPlatform().daemon().name("decoder").unstarted(() -> decode(http, audioUrl, format, out));
    }

    /** @param http jazzclub's client for audio, carrying the stream proxy */
    public static Decoder.Factory factory(HttpClient http, PcmFormat format) {
        return audioUrl -> {
            String scheme = audioUrl.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Invalid song url.");
            }
            LavaplayerDecoder decoder = new LavaplayerDecoder(http, audioUrl, format);
            decoder.worker.start();
            return decoder;
        };
    }

    private void decode(HttpClient http, URI audioUrl, PcmFormat format, PipedOutputStream out) {
        AacPcmConsumer consumer = null;
        try (out) {
            stream = HttpSeekableStream.open(http, audioUrl);
            if (closed) {
                return;
            }
            MpegFileLoader file = new MpegFileLoader(stream);
            file.parseHeaders();
            MpegTrackInfo track = file.getTrackList().stream().filter(AacPcmConsumer::canDecode).findFirst()
                    .orElseThrow(() -> new IllegalStateException("The stream has no AAC audio track"));
            consumer = new AacPcmConsumer(track, format, out);
            consumer.initialise();
            MpegFileTrackProvider frames = file.loadReader(consumer);
            if (frames == null) {
                throw new IllegalStateException("Unknown MP4 layout");
            }
            frames.provideFrames();
            consumer.flush();
        } catch (Exception e) {
            if (!closed) {
                failure = "Decoding failed: " + describe(e);
            }
        } finally {
            if (consumer != null) {
                consumer.close();
            }
            closeQuietly(stream);
        }
    }

    @Override
    public InputStream pcm() {
        return pcm;
    }

    @Override
    public String failure() throws InterruptedException {
        if (!worker.join(Duration.ofSeconds(5))) {
            return "The decoder did not finish.";
        }
        return failure;
    }

    /** Stops the download, which ends the worker; safe from any thread. */
    @Override
    public void close() {
        closed = true;
        closeQuietly(stream);
        worker.interrupt();
        closeQuietly(pcm);
    }

    /** The innermost message, which is the one that says what went wrong. */
    private static String describe(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message != null ? message : root.getClass().getSimpleName();
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            // closing anyway
        }
    }
}

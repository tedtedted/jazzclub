package com.tedredington.jazzclub.player.lavaplayer;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegFileLoader;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegTrackInfo;
import com.sedmelluq.discord.lavaplayer.container.mpeg.reader.MpegFileTrackProvider;
import com.tedredington.jazzclub.player.Decoder;
import com.tedredington.jazzclub.player.PcmFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decodes Pandora's AAC-in-MP4 in-process: a {@link SongDownload} fetches the file in the
 * background, our {@link HttpSeekableStream} feeds it to LavaPlayer's MP4 parser, and
 * {@link AacPcmConsumer} decodes the packets with fdk-aac. One thread per song decodes and writes
 * into a pipe; {@link #pcm()} is the other end.
 */
public final class LavaplayerDecoder implements Decoder {

    private static final Logger log = LoggerFactory.getLogger(LavaplayerDecoder.class);
    private static final int PIPE_BYTES = 64 * 1024;

    private final PipedInputStream pcm;
    private final Thread worker;
    private volatile SongDownload download;
    private volatile String failure;
    private volatile boolean closed;

    private LavaplayerDecoder(HttpClient http, URI audioUrl, PcmFormat format, DownloadPolicy policy,
                              ScheduledExecutorService watchdog) throws IOException {
        pcm = new PipedInputStream(PIPE_BYTES);
        PipedOutputStream out = new PipedOutputStream(pcm);
        worker = Thread.ofPlatform().daemon().name("decoder")
                .unstarted(() -> decode(http, audioUrl, format, policy, watchdog, out));
    }

    /** @param http jazzclub's client for audio, carrying the stream proxy */
    public static Decoder.Factory factory(HttpClient http, PcmFormat format, ScheduledExecutorService watchdog) {
        return factory(http, format, DownloadPolicy.withTimeout(Duration.ofSeconds(30)), watchdog);
    }

    /**
     * @param http   jazzclub's client for audio, carrying the stream proxy
     * @param policy the bounds of each song's download
     * @param watchdog shared scheduler owned by the caller, kept alive until decoding stops
     */
    public static Decoder.Factory factory(HttpClient http, PcmFormat format, DownloadPolicy policy,
                                          ScheduledExecutorService watchdog) {
        return audioUrl -> {
            String scheme = audioUrl.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Invalid song url.");
            }
            LavaplayerDecoder decoder = new LavaplayerDecoder(http, audioUrl, format, policy, watchdog);
            decoder.worker.start();
            return decoder;
        };
    }

    private void decode(HttpClient http, URI audioUrl, PcmFormat format, DownloadPolicy policy,
                        ScheduledExecutorService watchdog, PipedOutputStream out) {
        AacPcmConsumer consumer = null;
        SongDownload song = new SongDownload(http, audioUrl, policy, watchdog);
        // published before anything can block, so that close() reaches a download still connecting
        download = song;
        try (out) {
            if (closed) {
                return;
            }
            song.start();
            HttpSeekableStream stream = HttpSeekableStream.over(song);
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
                log.debug("Decoding failed", e);
                failure = "Decoding failed: " + describe(e);
            }
        } finally {
            if (consumer != null) {
                consumer.close();
            }
            song.close();
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
        SongDownload song = download;
        if (song != null) {
            song.close();
        }
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

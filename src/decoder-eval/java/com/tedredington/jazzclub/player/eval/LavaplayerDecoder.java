package com.tedredington.jazzclub.player.eval;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEvent;
import com.sedmelluq.discord.lavaplayer.player.event.TrackEndEvent;
import com.sedmelluq.discord.lavaplayer.player.event.TrackExceptionEvent;
import com.sedmelluq.discord.lavaplayer.player.event.TrackStuckEvent;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import com.tedredington.jazzclub.player.Decoder;

/**
 * LavaPlayer candidate: its HTTP source, container probing and fdk-aac (JNI) decoding, pulled as
 * 44.1 kHz s16le frames. Only the HTTP source is registered, so none of YouTube &amp; co. is used.
 */
final class LavaplayerDecoder implements Decoder {

    private final AudioPlayer player;
    private final CountDownLatch ended = new CountDownLatch(1);
    private final CountDownLatch errored = new CountDownLatch(1);
    private volatile String failure;
    private volatile boolean closed;
    private final InputStream pcm = new FrameStream();

    private LavaplayerDecoder(AudioPlayer player) {
        this.player = player;
    }

    static Decoder.Factory factory() {
        AudioPlayerManager manager = new DefaultAudioPlayerManager();
        manager.getConfiguration().setOutputFormat(StandardAudioDataFormats.COMMON_PCM_S16_LE);
        manager.registerSourceManager(new HttpAudioSourceManager());
        return audioUrl -> open(manager, audioUrl);
    }

    private static Decoder open(AudioPlayerManager manager, URI audioUrl) throws IOException {
        AudioItem item;
        try {
            item = manager.loadItemSync(new AudioReference(audioUrl.toString(), null));
        } catch (FriendlyException e) {
            throw new IOException("LavaPlayer could not load the stream: " + e.getMessage(), e);
        }
        if (!(item instanceof AudioTrack track)) {
            throw new IOException("LavaPlayer found no playable track (" + item + ")");
        }
        AudioPlayer player = manager.createPlayer();
        LavaplayerDecoder decoder = new LavaplayerDecoder(player);
        player.addListener(decoder::onEvent);
        player.playTrack(track);
        return decoder;
    }

    private void onEvent(AudioEvent event) {
        switch (event) {
            case TrackExceptionEvent e -> {
                failure = "Decoding failed: " + e.exception.getMessage()
                        + (e.exception.getCause() != null ? " (" + e.exception.getCause().getMessage() + ")" : "");
                errored.countDown();
            }
            case TrackStuckEvent e -> failure = "Decoding stuck for " + e.thresholdMs + " ms";
            case TrackEndEvent e -> {
                if (failure == null && e.endReason != null && !e.endReason.mayStartNext) {
                    failure = "Track ended: " + e.endReason;
                }
                ended.countDown();
            }
            default -> {
            }
        }
    }

    @Override
    public InputStream pcm() {
        return pcm;
    }

    @Override
    public String failure() throws InterruptedException {
        if (!ended.await(5, TimeUnit.SECONDS)) {
            return "LavaPlayer did not end the track.";
        }
        // A failed track ends with FINISHED first and the exception event only afterwards, from
        // another thread: without this wait, a stream that broke looks like one that played to the end.
        errored.await(250, TimeUnit.MILLISECONDS);
        return failure;
    }

    @Override
    public void close() {
        closed = true;
        player.destroy();
        ended.countDown();
    }

    /** Pulls frames from the player on the reading thread, like a SourceDataLine feeder would. */
    private final class FrameStream extends InputStream {
        private ByteBuffer current = ByteBuffer.allocate(0);

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            while (!current.hasRemaining()) {
                if (closed) {
                    return -1;
                }
                AudioFrame frame;
                try {
                    frame = player.provide(100, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    continue;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new java.io.InterruptedIOException();
                }
                if (frame == null || frame.isTerminator()) {
                    if (ended.getCount() == 0 || player.getPlayingTrack() == null) {
                        return -1;
                    }
                    continue;
                }
                current = ByteBuffer.wrap(frame.getData());
            }
            int piece = Math.min(length, current.remaining());
            current.get(buffer, offset, piece);
            return piece;
        }
    }
}

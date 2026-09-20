package com.tedredington.jazzclub.player;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pumps PCM from a {@link Decoder} into an {@link AudioSink} on a thread per track.
 * Holds all playback logic; the two collaborators only touch ffmpeg and the sound card.
 */
public final class StreamingAudioPlayer implements AudioPlayer {

    private static final Logger log = LoggerFactory.getLogger(StreamingAudioPlayer.class);
    private static final int BUFFER_SIZE = 16 * 1024;

    private final Decoder.Factory decoders;
    private final Supplier<AudioSink> sinks;
    private final PlaybackListener listener;
    private final double gainMultiplier;
    private final AtomicLong ids = new AtomicLong();

    private final Object lock = new Object();
    private Playback current; // guarded by lock
    private int volumeDb;     // guarded by lock
    private boolean paused;   // guarded by lock

    public StreamingAudioPlayer(Decoder.Factory decoders, Supplier<AudioSink> sinks, PlaybackListener listener,
                                int initialVolumeDb, double gainMultiplier) {
        this.decoders = decoders;
        this.sinks = sinks;
        this.listener = listener;
        this.volumeDb = initialVolumeDb;
        this.gainMultiplier = gainMultiplier;
    }

    @Override
    public long play(URI audioUrl, double trackGainDb) {
        stop();
        Playback playback = new Playback(ids.incrementAndGet(), audioUrl, trackGainDb);
        synchronized (lock) {
            current = playback;
            paused = false;
        }
        Thread.ofPlatform().name("player-" + playback.id).daemon(true).start(playback);
        return playback.id;
    }

    @Override
    public void stop() {
        Playback playback;
        synchronized (lock) {
            playback = current;
            paused = false;
            lock.notifyAll();
        }
        if (playback != null) {
            playback.requestStop();
        }
    }

    @Override
    public void setPaused(boolean paused) {
        synchronized (lock) {
            if (current == null || this.paused == paused) {
                return;
            }
            this.paused = paused;
            current.applyPause(paused);
            lock.notifyAll();
        }
    }

    @Override
    public boolean isPaused() {
        synchronized (lock) {
            return paused;
        }
    }

    @Override
    public boolean isActive() {
        synchronized (lock) {
            return current != null;
        }
    }

    @Override
    public Duration elapsed() {
        synchronized (lock) {
            return current == null ? Duration.ZERO : current.position();
        }
    }

    @Override
    public void setVolume(int volumeDb) {
        synchronized (lock) {
            this.volumeDb = volumeDb;
            if (current != null) {
                current.applyGain(volumeDb);
            }
        }
    }

    @Override
    public int volume() {
        synchronized (lock) {
            return volumeDb;
        }
    }

    private final class Playback implements Runnable {

        private final long id;
        private final URI audioUrl;
        private final double trackGainDb;
        private volatile boolean stopRequested;
        private volatile Decoder decoder;
        private volatile AudioSink sink;
        /** Remembered before the sink is closed; a closed sound card line reports position zero. */
        private volatile Duration positionAtStop = Duration.ZERO;

        Playback(long id, URI audioUrl, double trackGainDb) {
            this.id = id;
            this.audioUrl = audioUrl;
            this.trackGainDb = trackGainDb;
        }

        @Override
        public void run() {
            PlaybackResult result;
            Duration played = Duration.ZERO;
            try {
                result = pump();
            } catch (IOException | RuntimeException e) {
                log.debug("Playback {} broke off", id, e);
                result = stopRequested ? PlaybackResult.stopped() : PlaybackResult.failed(describe(e));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                result = PlaybackResult.stopped();
            } finally {
                Duration atEnd = position();
                played = atEnd.compareTo(positionAtStop) > 0 ? atEnd : positionAtStop;
                release();
            }
            result = result.withPlayed(played);
            synchronized (lock) {
                if (current == this) {
                    current = null;
                    paused = false;
                }
            }
            listener.finished(id, result);
        }

        private PlaybackResult pump() throws IOException, InterruptedException {
            decoder = decoders.open(audioUrl);
            AudioSink newSink = sinks.get();
            newSink.open();
            sink = newSink;
            synchronized (lock) {
                newSink.setGain(Gain.combinedDb(volumeDb, trackGainDb, gainMultiplier));
            }

            InputStream pcm = decoder.pcm();
            byte[] buffer = new byte[BUFFER_SIZE];
            long total = 0;
            int read;
            while (!stopRequested && (read = pcm.read(buffer)) > 0) {
                awaitResume();
                if (stopRequested) {
                    break;
                }
                newSink.write(buffer, 0, read);
                total += read;
            }
            if (stopRequested) {
                return PlaybackResult.stopped();
            }
            String failure = decoder.failure();
            if (failure != null) {
                return PlaybackResult.failed(failure);
            }
            if (total == 0) {
                return PlaybackResult.failed("The stream contained no audio.");
            }
            newSink.drain();
            return stopRequested ? PlaybackResult.stopped() : PlaybackResult.completed();
        }

        private void awaitResume() throws InterruptedException {
            synchronized (lock) {
                while (paused && !stopRequested) {
                    lock.wait();
                }
            }
        }

        void requestStop() {
            positionAtStop = position();
            stopRequested = true;
            synchronized (lock) {
                lock.notifyAll();
            }
            // closing the decoder unblocks a read(), closing the sink unblocks a write()
            Decoder d = decoder;
            if (d != null) {
                d.close();
            }
            AudioSink s = sink;
            if (s != null) {
                s.close();
            }
        }

        void applyPause(boolean pause) {
            AudioSink s = sink;
            if (s != null) {
                s.setPaused(pause);
            }
        }

        void applyGain(int newVolumeDb) {
            AudioSink s = sink;
            if (s != null) {
                s.setGain(Gain.combinedDb(newVolumeDb, trackGainDb, gainMultiplier));
            }
        }

        Duration position() {
            AudioSink s = sink;
            return s == null ? Duration.ZERO : s.position();
        }

        private void release() {
            AudioSink s = sink;
            if (s != null) {
                s.close();
            }
            Decoder d = decoder;
            if (d != null) {
                d.close();
            }
        }

        private String describe(Exception e) {
            return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }
    }
}

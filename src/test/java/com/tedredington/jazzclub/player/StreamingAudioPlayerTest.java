package com.tedredington.jazzclub.player;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class StreamingAudioPlayerTest {

    private static final URI URL = URI.create("https://audio.example/1.m4a");

    private record Finished(long id, PlaybackResult result) {
    }

    /** Collects what reaches the "sound card". */
    private static final class FakeSink implements AudioSink {
        final AtomicInteger bytesWritten = new AtomicInteger();
        final List<Double> gains = new ArrayList<>();
        final List<Boolean> pauses = new ArrayList<>();
        volatile boolean opened;
        volatile boolean drained;
        volatile boolean closed;
        IOException openFailure;

        @Override
        public void open() throws IOException {
            if (openFailure != null) {
                throw openFailure;
            }
            opened = true;
        }

        @Override
        public void write(byte[] buffer, int offset, int length) {
            bytesWritten.addAndGet(length);
        }

        @Override
        public synchronized void setPaused(boolean paused) {
            pauses.add(paused);
        }

        @Override
        public synchronized void setGain(double gainDb) {
            gains.add(gainDb);
        }

        @Override
        public void drain() {
            drained = true;
        }

        @Override
        public Duration position() {
            return Duration.ofMillis(bytesWritten.get());
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class FakeDecoder implements Decoder {
        final InputStream pcm;
        final String failure;
        final PipedOutputStream processOutput;
        volatile boolean closed;

        FakeDecoder(InputStream pcm, String failure) {
            this(pcm, failure, null);
        }

        /** @param processOutput closed along with the decoder, the way killing ffmpeg ends its pipe */
        FakeDecoder(InputStream pcm, String failure, PipedOutputStream processOutput) {
            this.pcm = pcm;
            this.failure = failure;
            this.processOutput = processOutput;
        }

        @Override
        public InputStream pcm() {
            return pcm;
        }

        @Override
        public String failure() {
            return failure;
        }

        @Override
        public void close() {
            closed = true;
            try {
                if (processOutput != null) {
                    processOutput.close();
                }
                pcm.close();
            } catch (IOException ignored) {
                // closing a test stream cannot fail in a way that matters
            }
        }
    }

    private final BlockingQueue<Finished> finished = new LinkedBlockingQueue<>();
    private final FakeSink sink = new FakeSink();
    private Decoder.Factory decoders = url -> new FakeDecoder(new ByteArrayInputStream(new byte[40_000]), null);

    private StreamingAudioPlayer player() {
        return new StreamingAudioPlayer(url -> decoders.open(url), () -> sink,
                (id, result) -> finished.add(new Finished(id, result)), -2, 0.5);
    }

    private Finished awaitFinished() throws InterruptedException {
        Finished result = finished.poll(5, TimeUnit.SECONDS);
        assertThat(result).as("the listener must always be told").isNotNull();
        return result;
    }

    @Test
    void playsTheWholeStreamThenDrainsAndReportsCompletion() throws InterruptedException {
        StreamingAudioPlayer player = player();

        long id = player.play(URL, -6.0);

        assertThat(awaitFinished()).isEqualTo(new Finished(id, PlaybackResult.completed()));
        assertThat(sink.bytesWritten.get()).isEqualTo(40_000);
        assertThat(sink.drained).isTrue();
        assertThat(sink.closed).isTrue();
        assertThat(player.isActive()).isFalse();
        assertThat(player.elapsed()).isZero();
    }

    @Test
    void appliesVolumePlusScaledTrackGain() throws InterruptedException {
        player().play(URL, -6.0);
        awaitFinished();

        assertThat(sink.gains).containsExactly(-5.0); // -2 + (-6 * 0.5)
    }

    @Test
    void aDecoderErrorIsReportedAsFailureWithItsMessage() throws InterruptedException {
        decoders = url -> new FakeDecoder(new ByteArrayInputStream(new byte[100]), "Decoding failed: 403 Forbidden");

        player().play(URL, 0);

        assertThat(awaitFinished().result()).isEqualTo(PlaybackResult.failed("Decoding failed: 403 Forbidden"));
        assertThat(sink.drained).isFalse();
    }

    @Test
    void anEmptyStreamIsAFailureNotASilentSuccess() throws InterruptedException {
        decoders = url -> new FakeDecoder(new ByteArrayInputStream(new byte[0]), null);

        player().play(URL, 0);

        assertThat(awaitFinished().result().outcome()).isEqualTo(PlaybackResult.Outcome.FAILED);
    }

    @Test
    void aDecoderThatCannotStartIsAFailureCarryingTheHint() throws InterruptedException {
        decoders = url -> {
            throw new IOException("Could not start 'ffmpeg'. jazzclub needs ffmpeg");
        };

        player().play(URL, 0);

        assertThat(awaitFinished().result().detail()).contains("needs ffmpeg");
    }

    @Test
    void anInvalidUrlIsAFailure() throws InterruptedException {
        decoders = url -> {
            throw new IllegalArgumentException("Invalid song url.");
        };

        player().play(URL, 0);

        assertThat(awaitFinished().result()).isEqualTo(PlaybackResult.failed("Invalid song url."));
    }

    @Test
    void noSoundCardIsAFailure() throws InterruptedException {
        sink.openFailure = new IOException("No audio output device is available");

        player().play(URL, 0);

        assertThat(awaitFinished().result().detail()).contains("No audio output device");
    }

    @Test
    void stopEndsAnEndlessStreamAndReportsStopped() throws Exception {
        PipedOutputStream source = new PipedOutputStream();
        FakeDecoder decoder = new FakeDecoder(new PipedInputStream(source), null, source);
        decoders = url -> decoder;
        StreamingAudioPlayer player = player();

        long id = player.play(URL, 0);
        source.write(new byte[1000]);
        source.flush();
        while (sink.bytesWritten.get() < 1000) {
            Thread.onSpinWait();
        }
        assertThat(player.isActive()).isTrue();
        assertThat(player.elapsed()).isPositive();

        player.stop();

        assertThat(awaitFinished()).isEqualTo(new Finished(id, PlaybackResult.stopped()));
        assertThat(decoder.closed).isTrue();
        assertThat(sink.closed).isTrue();
    }

    @Test
    void pauseHoldsThePumpAndResumeContinues() throws Exception {
        PipedOutputStream source = new PipedOutputStream();
        PipedInputStream pcm = new PipedInputStream(source, 1 << 16);
        decoders = url -> new FakeDecoder(pcm, null);
        StreamingAudioPlayer player = player();
        player.play(URL, 0);
        source.write(new byte[500]);
        source.flush();
        while (sink.bytesWritten.get() < 500) {
            Thread.onSpinWait();
        }

        player.setPaused(true);
        assertThat(player.isPaused()).isTrue();
        source.write(new byte[700]);
        source.flush();
        Thread.sleep(200);
        // at most the chunk already being read slips through; the rest waits
        int whilePaused = sink.bytesWritten.get();

        player.setPaused(false);
        source.close();

        assertThat(awaitFinished().result()).isEqualTo(PlaybackResult.completed());
        assertThat(sink.bytesWritten.get()).isEqualTo(1200);
        assertThat(whilePaused).isLessThan(1200);
        assertThat(sink.pauses).containsExactly(true, false);
        assertThat(player.isPaused()).isFalse();
    }

    @Test
    void pausingTwiceOrWithNothingPlayingIsHarmless() {
        StreamingAudioPlayer player = player();

        player.setPaused(true);

        assertThat(player.isPaused()).isFalse();
        assertThat(sink.pauses).isEmpty();
    }

    @Test
    void volumeChangesApplyToThePlayingTrackImmediately() throws Exception {
        PipedOutputStream source = new PipedOutputStream();
        PipedInputStream pcm = new PipedInputStream(source);
        decoders = url -> new FakeDecoder(pcm, null);
        StreamingAudioPlayer player = player();
        player.play(URL, -6.0);
        source.write(new byte[10]);
        source.flush();
        while (sink.bytesWritten.get() < 10) {
            Thread.onSpinWait();
        }

        player.setVolume(4);
        source.close();
        awaitFinished();

        assertThat(player.volume()).isEqualTo(4);
        assertThat(sink.gains).containsExactly(-5.0, 1.0);
    }

    @Test
    void startingANewTrackStopsTheOldOneAndBothAreReported() throws Exception {
        PipedOutputStream endless = new PipedOutputStream();
        PipedInputStream endlessPcm = new PipedInputStream(endless);
        List<Decoder> opened = new ArrayList<>();
        decoders = url -> {
            Decoder decoder = opened.isEmpty()
                    ? new FakeDecoder(endlessPcm, null, endless)
                    : new FakeDecoder(new ByteArrayInputStream(new byte[64]), null);
            opened.add(decoder);
            return decoder;
        };
        StreamingAudioPlayer player = player();

        long first = player.play(URL, 0);
        endless.write(1);
        endless.flush();
        while (sink.bytesWritten.get() < 1) {
            Thread.onSpinWait();
        }
        long second = player.play(URL, 0);

        List<Finished> results = List.of(awaitFinished(), awaitFinished());
        assertThat(results).containsExactlyInAnyOrder(
                new Finished(first, PlaybackResult.stopped()), new Finished(second, PlaybackResult.completed()));
        assertThat(second).isGreaterThan(first);
    }

    @Test
    void stopWithNothingPlayingIsHarmless() {
        player().stop();

        assertThat(finished).isEmpty();
    }
}

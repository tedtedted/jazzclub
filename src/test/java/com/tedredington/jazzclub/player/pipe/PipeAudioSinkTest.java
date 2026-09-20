package com.tedredington.jazzclub.player.pipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.tedredington.jazzclub.player.PcmFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Against a real named pipe. */
@Timeout(20)
class PipeAudioSinkTest {

    private static final PcmFormat FORMAT = PcmFormat.of(0);

    @TempDir
    Path directory;

    private Path mkfifo() throws IOException, InterruptedException {
        Path pipe = directory.resolve("snapfifo");
        Process process = new ProcessBuilder("mkfifo", pipe.toString()).inheritIO().start();
        assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
        return pipe;
    }

    private static byte[] samples(int... values) {
        byte[] bytes = new byte[values.length * 2];
        for (int i = 0; i < values.length; i++) {
            bytes[2 * i] = (byte) values[i];
            bytes[2 * i + 1] = (byte) (values[i] >> 8);
        }
        return bytes;
    }

    @Test
    void whatIsWrittenComesOutOfThePipeAndCountsAsPlayed() throws Exception {
        Path pipe = mkfifo();
        PipeAudioSink sink = new PipeAudioSink(pipe, FORMAT);
        sink.open();
        // well below what a pipe holds (16 KB on macOS): this thread writes first and reads afterwards
        byte[] audio = new byte[FORMAT.bytesPerSecond() / 20];
        audio[0] = 42;

        try (InputStream reader = Files.newInputStream(pipe)) {
            sink.write(audio, 0, audio.length);
            assertThat(reader.readNBytes(audio.length)).isEqualTo(audio);
        }

        assertThat(sink.position()).isEqualTo(Duration.ofMillis(50));
        sink.close();
    }

    @Test
    void volumeIsAppliedToTheSamplesBecauseThereIsNoSoundCardToDoIt() {
        byte[] loud = samples(1000, -1000, 20_000, -20_000);

        assertThat(PipeAudioSink.scale(loud, 0, loud.length, 0.5)).isEqualTo(samples(500, -500, 10_000, -10_000));
        assertThat(PipeAudioSink.scale(loud, 0, loud.length, 1.0)).isEqualTo(loud);
    }

    @Test
    void amplificationClipsInsteadOfWrappingAroundIntoNoise() {
        byte[] loud = samples(30_000, -30_000);

        assertThat(PipeAudioSink.scale(loud, 0, loud.length, 2.0)).isEqualTo(samples(32_767, -32_768));
    }

    @Test
    void gainInDecibelsBecomesAFactor() throws Exception {
        Path pipe = mkfifo();
        PipeAudioSink sink = new PipeAudioSink(pipe, FORMAT);
        sink.open();
        sink.setGain(-20); // a tenth of the amplitude

        try (InputStream reader = Files.newInputStream(pipe)) {
            byte[] in = samples(10_000, -10_000);
            sink.write(in, 0, in.length);
            assertThat(reader.readNBytes(4)).isEqualTo(samples(1000, -1000));
        }
        sink.close();
    }

    /**
     * Nobody reads the pipe, the pump is stuck writing, the user skips. Inside one JVM this cannot be
     * solved on macOS: a blocked write cannot be interrupted, closing under it hangs, and available()
     * reports 0 for a pipe. Hence the child process, which can simply be killed.
     */
    @Test
    @Timeout(8)
    void aWriterStuckOnAFullPipeDoesNotPreventClosing() throws Exception {
        Path pipe = mkfifo();
        PipeAudioSink sink = new PipeAudioSink(pipe, FORMAT);
        sink.open();
        Thread pump = Thread.ofPlatform().daemon(true).start(() -> {
            byte[] chunk = new byte[16 * 1024];
            for (int i = 0; i < 64; i++) { // far more than a pipe holds
                sink.write(chunk, 0, chunk.length);
            }
        });
        Thread.sleep(500); // every buffer on the way is full now and the pump blocked

        long start = System.nanoTime();
        sink.close();
        pump.join(3_000);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        assertThat(pump.isAlive()).as("the pump got out of write()").isFalse();
    }

    @Test
    void aReaderThatGoesAwayIsReportedInWordsTheUserUnderstands() throws Exception {
        Path pipe = mkfifo();
        PipeAudioSink sink = new PipeAudioSink(pipe, FORMAT);
        sink.open();
        try (InputStream reader = Files.newInputStream(pipe)) {
            sink.write(new byte[64], 0, 64);
            reader.readNBytes(64);
        }
        // the reader has closed its end; the next writes hit a broken pipe
        assertThatThrownBy(() -> {
            byte[] chunk = new byte[16 * 1024];
            for (int i = 0; i < 64; i++) {
                sink.write(chunk, 0, chunk.length);
                Thread.sleep(20);
            }
        }).isInstanceOf(java.io.UncheckedIOException.class).hasMessageContaining("Nothing is reading audio pipe");
        sink.close();
    }

    @Test
    void pauseAndDrainNeedNoSpecialHandling() throws Exception {
        PipeAudioSink sink = new PipeAudioSink(mkfifo(), FORMAT);
        sink.open();
        sink.setPaused(true);
        sink.setPaused(false);
        sink.drain();
        sink.close();
        sink.close();
        sink.write(new byte[4], 0, 4); // after close: ignored

        assertThat(sink.position()).isZero();
    }

    @Test
    void aMissingPipeTellsHowToCreateIt() {
        Path absent = directory.resolve("absent");

        assertThatThrownBy(() -> new PipeAudioSink(absent, FORMAT).open())
                .isInstanceOf(IOException.class).hasMessageContaining("mkfifo " + absent);
    }

    @Test
    void anOrdinaryFileIsRefusedSoItCannotFillTheDisk() throws IOException {
        Path file = Files.writeString(directory.resolve("file"), "");

        assertThatThrownBy(() -> new PipeAudioSink(file, FORMAT).open())
                .isInstanceOf(IOException.class).hasMessageContaining("is not a pipe");
    }
}

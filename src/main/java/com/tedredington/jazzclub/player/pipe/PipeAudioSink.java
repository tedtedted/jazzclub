package com.tedredington.jazzclub.player.pipe;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import com.tedredington.jazzclub.player.AudioSink;
import com.tedredington.jazzclub.player.PcmFormat;
import com.tedredington.jazzclub.remote.ControlFifo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * pianobar's {@code audio_pipe}: raw PCM goes to a named pipe instead of the sound card, for
 * multi-room setups such as Snapcast. There is no sound card to turn the volume, so volume and
 * ReplayGain are applied to the samples here.
 */
public final class PipeAudioSink implements AudioSink {

    private static final Logger log = LoggerFactory.getLogger(PipeAudioSink.class);
    private static final long CLOSE_TIMEOUT_NANOS = Duration.ofSeconds(1).toNanos();

    private final Path pipe;
    private final PcmFormat format;
    private final AtomicLong bytesWritten = new AtomicLong();

    private volatile Process feeder;
    private volatile OutputStream out;
    private volatile double factor = 1.0;
    private volatile boolean writing;
    private volatile boolean closing;

    public PipeAudioSink(Path pipe, PcmFormat format) {
        this.pipe = pipe;
        this.format = format;
    }

    /**
     * The pipe is not opened by this process but by a {@code cat} child writing into it. Opening a
     * pipe blocks until somebody reads it, writing blocks while it is full, and neither can be
     * interrupted inside the JVM; on macOS even closing under a blocked writer hangs. A child process
     * can always be killed, which is what skipping a song comes down to.
     */
    @Override
    public void open() throws IOException {
        if (!Files.exists(pipe)) {
            throw new IOException("Cannot find audio pipe " + pipe + ". Create it with: mkfifo " + pipe);
        }
        if (!ControlFifo.isFifo(pipe)) {
            throw new IOException("audio_pipe " + pipe + " is not a pipe.");
        }
        // the path travels as an argument, never as part of the script
        feeder = new ProcessBuilder("/bin/sh", "-c", "exec cat > \"$1\"", "sh", pipe.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        out = feeder.getOutputStream();
    }

    @Override
    public void write(byte[] buffer, int offset, int length) {
        OutputStream target = out;
        if (closing || target == null) {
            return;
        }
        byte[] scaled = scale(buffer, offset, length, factor);
        writing = true;
        try {
            target.write(scaled, 0, length);
            target.flush();
            bytesWritten.addAndGet(length);
        } catch (IOException e) {
            if (!closing) {
                // "broken pipe": whoever was reading has gone, which takes the cat child with it
                throw new java.io.UncheckedIOException(
                        "Nothing is reading audio pipe " + pipe + " any more.", e);
            }
        } finally {
            writing = false;
        }
    }

    /** Signed 16 bit little-endian samples multiplied by {@code factor}, clipped instead of wrapping around. */
    static byte[] scale(byte[] buffer, int offset, int length, double factor) {
        byte[] out = new byte[length];
        if (factor == 1.0) {
            System.arraycopy(buffer, offset, out, 0, length);
            return out;
        }
        for (int i = 0; i + 1 < length; i += 2) {
            int sample = (short) ((buffer[offset + i] & 0xff) | (buffer[offset + i + 1] << 8));
            int scaled = (int) Math.round(sample * factor);
            scaled = Math.clamp(scaled, Short.MIN_VALUE, Short.MAX_VALUE);
            out[i] = (byte) scaled;
            out[i + 1] = (byte) (scaled >> 8);
        }
        return out;
    }

    /** The pump simply stops writing while paused; the reader of the pipe runs dry, as with pianobar. */
    @Override
    public void setPaused(boolean paused) {
        // nothing to do
    }

    @Override
    public void setGain(double gainDb) {
        factor = Math.pow(10, gainDb / 20);
    }

    @Override
    public void drain() {
        Process child = feeder;
        OutputStream target = out;
        if (closing || child == null || target == null || bytesWritten.get() == 0) {
            return;
        }
        try {
            // Bytes accepted by our stdin pipe may still be waiting for cat to copy
            // them to the audio FIFO. Send EOF and let it finish before close kills it.
            target.close();
            int status = child.waitFor();
            if (!closing && status != 0) {
                throw new IOException("Nothing is reading audio pipe " + pipe + " any more.");
            }
        } catch (IOException e) {
            if (!closing) {
                throw new java.io.UncheckedIOException(e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!closing) {
                throw new java.io.UncheckedIOException("Interrupted while draining audio pipe " + pipe,
                        new IOException(e));
            }
        }
    }

    @Override
    public Duration position() {
        return Duration.ofMillis(bytesWritten.get() * 1000 / format.bytesPerSecond());
    }

    /** Killing the child fails a blocked write with "broken pipe", after which our end can be closed safely. */
    @Override
    public void close() {
        Process child = feeder;
        if (child == null || closing) {
            return;
        }
        closing = true;
        child.destroyForcibly();
        long deadline = System.nanoTime() + CLOSE_TIMEOUT_NANOS;
        while (writing && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        if (writing) {
            log.debug("Audio pipe writer did not stop; leaving its stream open");
            return;
        }
        try {
            out.close();
        } catch (IOException e) {
            log.debug("Closing the audio pipe feeder failed", e);
        }
    }
}

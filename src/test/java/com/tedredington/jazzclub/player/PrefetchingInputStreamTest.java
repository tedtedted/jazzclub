package com.tedredington.jazzclub.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class PrefetchingInputStreamTest {

    @Test
    void waitsAtEndOfAFullBufferAndCloseReleasesTheProducer() throws Exception {
        AtomicReference<Thread> producer = new AtomicReference<>();
        CountDownLatch reachedEnd = new CountDownLatch(1);
        AtomicBoolean sourceClosed = new AtomicBoolean();
        InputStream source = new ByteArrayInputStream(new byte[16 * 1024]) {
            @Override
            public synchronized int read(byte[] bytes, int offset, int length) {
                producer.set(Thread.currentThread());
                int count = super.read(bytes, offset, length);
                if (count == -1) {
                    reachedEnd.countDown();
                }
                return count;
            }

            @Override
            public void close() {
                sourceClosed.set(true);
            }
        };
        try (var stream = new PrefetchingInputStream(source, 16 * 1024)) {
            assertThat(reachedEnd.await(2, TimeUnit.SECONDS)).isTrue();
            // Nothing consumes the full queue, as when playback is paused near EOF.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            boolean waited = false;
            while (System.nanoTime() < deadline) {
                if (producer.get().getState() == Thread.State.TIMED_WAITING) {
                    waited = true;
                    break;
                }
                Thread.sleep(5);
            }
            assertThat(waited).as("producer waits instead of spinning at EOF").isTrue();
        }
        assertThat(producer.get().isAlive()).isFalse();
        assertThat(sourceClosed).isTrue();
    }

    private static byte[] pattern(int length) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) (i * 31);
        }
        return data;
    }

    @Test
    void deliversExactlyWhatTheSourceHeldInOrder() throws IOException {
        byte[] data = pattern(100_000);

        try (InputStream in = new PrefetchingInputStream(new ByteArrayInputStream(data), 64 * 1024)) {
            assertThat(in.readAllBytes()).isEqualTo(data);
            assertThat(in.read()).as("stays at end of stream").isEqualTo(-1);
        }
    }

    @Test
    void singleByteReadsWorkToo() throws IOException {
        try (InputStream in = new PrefetchingInputStream(new ByteArrayInputStream(new byte[] {1, (byte) 200}), 1024)) {
            assertThat(in.read()).isEqualTo(1);
            assertThat(in.read()).isEqualTo(200);
            assertThat(in.read()).isEqualTo(-1);
            assertThat(in.read(new byte[4], 0, 0)).isZero();
        }
    }

    @Test
    void readsAheadSoAStallOfTheSourceIsBridgedFromMemory() throws Exception {
        PipedOutputStream source = new PipedOutputStream();
        PrefetchingInputStream in = new PrefetchingInputStream(new PipedInputStream(source, 1 << 20), 512 * 1024);
        source.write(pattern(200_000));
        source.flush();

        while (in.buffered() < 200_000) {
            Thread.onSpinWait();
        }
        // the source now "stalls": nothing more is written, yet everything sent so far can be played
        byte[] played = in.readNBytes(200_000);

        assertThat(played).isEqualTo(pattern(200_000));
        in.close();
    }

    @Test
    void neverHoldsMoreThanItsCapacity() throws Exception {
        PipedOutputStream source = new PipedOutputStream();
        PrefetchingInputStream in = new PrefetchingInputStream(new PipedInputStream(source, 1 << 20), 64 * 1024);
        Thread writer = Thread.ofPlatform().daemon(true).start(() -> {
            try {
                source.write(new byte[600_000]);
            } catch (IOException ignored) {
                // closed at the end of the test
            }
        });
        Thread.sleep(300);

        assertThat(in.buffered()).isLessThanOrEqualTo(64 * 1024 + 16 * 1024);
        in.close();
        writer.interrupt();
    }

    @Test
    void aFailureOfTheSourceSurfacesAfterWhatWasReadBeforeIt() {
        InputStream failing = new InputStream() {
            private int served;

            @Override
            public int read() throws IOException {
                if (served++ < 10) {
                    return 7;
                }
                throw new IOException("connection reset");
            }
        };
        PrefetchingInputStream in = new PrefetchingInputStream(failing, 1024);

        assertThatThrownBy(in::readAllBytes).isInstanceOf(IOException.class).hasMessage("connection reset");
    }

    /**
     * With a real process as source, like ffmpeg. Closing a stream under a reader blocked in it made
     * skipping hang now and then in the native binary; this pins the safe order: end the process, then
     * close.
     */
    @Test
    @Timeout(8)
    void closingReturnsPromptlyWhileTheSourceIsARealPipeWithNothingToRead() throws Exception {
        Process silent = new ProcessBuilder("/bin/sh", "-c", "sleep 30").start();
        PrefetchingInputStream in = new PrefetchingInputStream(silent.getInputStream(), 64 * 1024);
        Thread.sleep(200); // the prefetch thread is now blocked in read()

        long start = System.nanoTime();
        silent.destroyForcibly(); // what Decoder.close() does first
        in.close();

        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2_000);
    }

    @Test
    @Timeout(8)
    void aSourceThatNeverEndsIsLeftAloneRatherThanClosedUnderItsReader() throws Exception {
        AtomicBoolean sourceClosed = new AtomicBoolean();
        InputStream stuck = new InputStream() {
            @Override
            public int read() throws IOException {
                try {
                    new java.util.concurrent.CountDownLatch(1).await(); // blocks, ignoring everything but interrupt
                } catch (InterruptedException e) {
                    // like a native read: cannot be interrupted, keeps blocking
                    java.util.concurrent.locks.LockSupport.parkNanos(5_000_000_000L);
                }
                return -1;
            }

            @Override
            public void close() {
                sourceClosed.set(true);
            }
        };
        PrefetchingInputStream in = new PrefetchingInputStream(stuck, 1024);
        Thread.sleep(100);

        long start = System.nanoTime();
        in.close();

        assertThat((System.nanoTime() - start) / 1_000_000).as("bounded wait").isLessThan(2_000);
        assertThat(sourceClosed).as("not closed under a reader that is still in it").isFalse();
    }

    @Test
    void closingWakesABlockedReaderAndClosesTheSource() throws Exception {
        AtomicBoolean sourceClosed = new AtomicBoolean();
        PipedOutputStream never = new PipedOutputStream();
        PipedInputStream source = new PipedInputStream(never) {
            @Override
            public void close() throws IOException {
                sourceClosed.set(true);
                super.close();
            }
        };
        PrefetchingInputStream in = new PrefetchingInputStream(source, 1024);
        Thread consumer = Thread.ofPlatform().start(() -> {
            try {
                in.read();
            } catch (IOException ignored) {
                // either outcome is fine; returning at all is the point
            }
        });
        Thread.sleep(100);

        in.close();
        consumer.join(3_000);

        assertThat(consumer.isAlive()).isFalse();
        assertThat(sourceClosed).isTrue();
    }
}

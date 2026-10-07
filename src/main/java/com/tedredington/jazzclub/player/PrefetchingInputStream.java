package com.tedredington.jazzclub.player;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Reads ahead on a thread of its own, up to a fixed amount, so that a stall of the source (the
 * network hiccups, ffmpeg waits) is bridged from memory instead of being heard. This is pianobar's
 * {@code buffer_seconds}.
 */
public final class PrefetchingInputStream extends InputStream {

    private static final int CHUNK_SIZE = 16 * 1024;
    private static final byte[] END = new byte[0];
    private static final long READER_EXIT_TIMEOUT_MILLIS = 500;

    private final InputStream source;
    private final BlockingQueue<byte[]> chunks;
    private final Thread reader;
    private volatile IOException failure;
    private volatile boolean closed;

    private byte[] current = END;
    private int position;
    private boolean ended;

    public PrefetchingInputStream(InputStream source, int capacityBytes) {
        this.source = source;
        this.chunks = new ArrayBlockingQueue<>(Math.max(1, capacityBytes / CHUNK_SIZE));
        this.reader = Thread.ofPlatform().name("audio-prefetch").daemon(true).start(this::fill);
    }

    private void fill() {
        try {
            while (!closed) {
                byte[] chunk = new byte[CHUNK_SIZE];
                int read = source.read(chunk);
                if (read < 0) {
                    break;
                }
                if (read > 0) {
                    chunks.put(read == CHUNK_SIZE ? chunk : java.util.Arrays.copyOf(chunk, read));
                }
            }
        } catch (IOException e) {
            failure = e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // A paused consumer may leave the queue full at EOF. Wait without burning CPU;
        // close() interrupts this wait and supplies its own end marker.
        try {
            while (!closed && !chunks.offer(END, 100, TimeUnit.MILLISECONDS)) {
                // Check close between bounded waits.
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        if (position == current.length && !next()) {
            return -1;
        }
        int count = Math.min(length, current.length - position);
        System.arraycopy(current, position, buffer, offset, count);
        position += count;
        return count;
    }

    private boolean next() throws IOException {
        if (ended) {
            return false;
        }
        try {
            current = chunks.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for audio", e);
        }
        position = 0;
        if (current == END) {
            ended = true;
            if (failure != null && !closed) {
                throw failure;
            }
            return false;
        }
        return true;
    }

    /** How much is ready to be played without touching the source. */
    public int buffered() {
        return chunks.stream().mapToInt(chunk -> chunk.length).sum() + current.length - position;
    }

    /**
     * Never closes the source under a reader that may be blocked in it: on some platforms that hangs,
     * and the caller is typically the main loop. Whoever owns the source ends it first (kills the
     * process); the prefetch thread then sees end-of-file and the source can be closed safely. If the
     * thread does not leave in time, the source is left to the garbage collector.
     */
    @Override
    public void close() throws IOException {
        closed = true;
        reader.interrupt();
        chunks.clear();
        chunks.offer(END); // wakes a consumer blocked in take()
        try {
            reader.join(READER_EXIT_TIMEOUT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!reader.isAlive()) {
            source.close();
        }
    }
}

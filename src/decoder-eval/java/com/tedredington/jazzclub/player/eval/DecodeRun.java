package com.tedredington.jazzclub.player.eval;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.tedredington.jazzclub.player.Decoder;

/**
 * One decode of one URL, read to the end the way jazzclub's player reads it.
 *
 * @param openError   set if {@link Decoder.Factory#open} threw
 * @param failure     what {@link Decoder#failure()} reported; {@code null} means "succeeded"
 * @param hung        the decode did not finish within the time limit
 * @param firstByteMs time from open() to the first PCM byte, or -1
 * @param totalMs     time from open() to the end of the PCM (not counting failure())
 */
record DecodeRun(Pcm pcm, String openError, String failure, boolean hung, long firstByteMs, long totalMs) {

    private static final ExecutorService READERS = Executors.newVirtualThreadPerTaskExecutor();

    boolean succeeded() {
        return openError == null && failure == null && !hung;
    }

    /** Anything that tells the user decoding went wrong. */
    String reportedError() {
        return openError != null ? openError : failure;
    }

    static DecodeRun of(Decoder.Factory factory, URI url, long timeoutSeconds) throws InterruptedException {
        long start = System.nanoTime();
        Future<DecodeRun> run = READERS.submit(() -> decode(factory, url, start));
        try {
            return run.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            run.cancel(true);
            return new DecodeRun(Pcm.fromS16le(new byte[0], Candidate.FORMAT.sampleRate()), null, null, true, -1,
                    elapsedMs(start));
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    private static DecodeRun decode(Decoder.Factory factory, URI url, long start) throws InterruptedException {
        Decoder decoder;
        try {
            decoder = factory.open(url);
        } catch (IOException | RuntimeException e) {
            return new DecodeRun(Pcm.fromS16le(new byte[0], Candidate.FORMAT.sampleRate()), String.valueOf(e), null,
                    false, -1, elapsedMs(start));
        }
        try (decoder) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            long firstByte = -1;
            String readError = null;
            byte[] buffer = new byte[8192];
            InputStream pcm = decoder.pcm();
            try {
                for (int n; (n = pcm.read(buffer)) >= 0; ) {
                    if (n > 0 && firstByte < 0) {
                        firstByte = elapsedMs(start);
                    }
                    out.write(buffer, 0, n);
                }
            } catch (IOException e) {
                readError = "read failed: " + e;
            }
            long total = elapsedMs(start);
            String failure = decoder.failure();
            return new DecodeRun(Pcm.fromS16le(out.toByteArray(), Candidate.FORMAT.sampleRate()), null,
                    failure != null ? failure : readError, false, firstByte, total);
        }
    }

    private static long elapsedMs(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }
}

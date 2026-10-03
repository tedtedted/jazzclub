package com.tedredington.jazzclub.player.lavaplayer;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.OptionalLong;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads one song's audio file into memory as fast as the network allows, on a thread of its
 * own, while the decoder reads what has arrived so far.
 *
 * <p>Playback must not pace the download: reading only as fast as the song plays kept one slow
 * connection open for the whole song, and something on the way (Pandora's CDN, most likely) cut
 * it every 20–25 seconds. Fetched at full speed, a song is normally complete long before that.
 *
 * <p>A connection that breaks is resumed with a {@code Range} request where the download stopped,
 * which is independent of where the decoder reads. Recovery is bounded: a request must deliver
 * {@link DownloadPolicy#minProgressBytes()} to count as progress, only so many requests in a row
 * may fail to, and the waits between them grow. A resumed response is checked before a byte of
 * it is kept, so that two versions of a file are never spliced together.
 *
 * <p>Nothing is written to disk. {@link #close()} cancels the request or response in progress,
 * wakes a waiting reader, ends the thread and lets go of the buffer.
 */
final class SongDownload implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(SongDownload.class);

    static final long UNKNOWN_LENGTH = -1;

    private static final int CHUNK_SIZE = 16 * 1024;
    private static final int UNKNOWN_LENGTH_INITIAL_CAPACITY = 256 * 1024;
    private static final int MAX_REDIRECTS = 5;
    private static final Pattern CONTENT_RANGE = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)");

    /** Cuts off response bodies that deliver nothing; shared, as it does almost nothing. */
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("song-download-watchdog").daemon(true).factory());

    private final HttpClient http;
    private final DownloadPolicy policy;
    private final Thread worker;
    private final Object lock = new Object();

    // guarded by lock
    private byte[] data = new byte[0];
    private int count;
    private long length = UNKNOWN_LENGTH;
    private boolean headersReceived;
    private boolean complete;
    private IOException failure;
    private boolean closed;
    private CompletableFuture<?> pendingRequest;
    private InputStream activeBody;

    // used by the worker only
    private URI uri;
    /** A strong ETag from the first response, so that a resume can tell the file is still the same. */
    private String etag;
    private volatile long lastDataNanos;
    private volatile boolean stalled;

    SongDownload(HttpClient http, URI uri, DownloadPolicy policy) {
        this.http = http;
        this.uri = uri;
        this.policy = policy;
        this.worker = Thread.ofPlatform().name("song-download").daemon(true).unstarted(this::run);
    }

    void start() {
        worker.start();
    }

    /**
     * Waits for the first response.
     *
     * @return the file's size, or {@link #UNKNOWN_LENGTH} if the server did not say
     */
    long awaitLength() throws IOException {
        synchronized (lock) {
            while (!closed && !headersReceived && failure == null) {
                await();
            }
            ensureOpen();
            if (!headersReceived) {
                throw rethrown(failure);
            }
            return length;
        }
    }

    /**
     * Copies bytes from {@code position} on, waiting until at least one has arrived, the file has
     * ended, the download has failed, or it was closed.
     *
     * @return the number of bytes copied, or -1 at the end of the file
     */
    int read(long position, byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        synchronized (lock) {
            while (!closed && position >= count && !complete && failure == null) {
                await();
            }
            ensureOpen();
            if (position < count) {
                int n = (int) Math.min(length, count - position);
                System.arraycopy(data, (int) position, buffer, offset, n);
                return n;
            }
            if (failure != null) {
                throw rethrown(failure);
            }
            return -1;
        }
    }

    /** Safe from any thread, also while another one is blocked in {@link #read}. */
    @Override
    public void close() {
        CompletableFuture<?> request;
        InputStream body;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            request = pendingRequest;
            body = activeBody;
            data = null;
            lock.notifyAll();
        }
        if (request != null) {
            request.cancel(true);
        }
        closeQuietly(body);
    }

    int downloaded() {
        synchronized (lock) {
            return count;
        }
    }

    boolean isComplete() {
        synchronized (lock) {
            return complete;
        }
    }

    /** What the download still holds on to: nothing, once closed. */
    long retainedBytes() {
        synchronized (lock) {
            return data == null ? 0 : data.length;
        }
    }

    boolean awaitWorkerExit(Duration timeout) throws InterruptedException {
        return worker.join(timeout);
    }

    private void run() {
        long started = System.nanoTime();
        int reconnects = 0;
        int withoutProgress = 0;
        while (true) {
            int from = downloaded();
            IOException problem;
            try {
                if (fetch(from)) {
                    finish(started, reconnects);
                    return;
                }
                problem = new EOFException("The server ended the file at byte " + downloaded() + " of "
                        + knownLength());
            } catch (PermanentFailure e) {
                fail(e);
                return;
            } catch (IOException e) {
                problem = e;
            } catch (RuntimeException e) {
                fail(new IOException(e.getMessage() != null ? e.getMessage() : e.toString(), e));
                return;
            }
            if (isClosed()) {
                return;
            }
            if (reachedEnd()) {
                // everything arrived before the connection broke; resuming would ask for nothing
                finish(started, reconnects);
                return;
            }
            boolean progress = downloaded() - from >= policy.minProgressBytes();
            withoutProgress = progress ? 0 : withoutProgress + 1;
            if (withoutProgress >= policy.maxAttemptsWithoutProgress() || reconnects >= policy.maxReconnects()) {
                IOException gaveUp = new IOException("The download kept failing (" + (reconnects + 1)
                        + " attempts): " + innermost(problem).getMessage());
                gaveUp.addSuppressed(problem);
                fail(gaveUp);
                return;
            }
            reconnects++;
            log.debug("Reconnecting at byte {} of {} (reconnect {}, {} in a row without progress) after: {}",
                    downloaded(), knownLength(), reconnects, withoutProgress, causes(problem));
            if (!progress && !pause(backoff(withoutProgress))) {
                return;
            }
        }
    }

    /**
     * One request: from the start of the file if nothing has arrived yet, otherwise a resume.
     *
     * @return whether the whole file is now here
     */
    private boolean fetch(int from) throws IOException {
        HttpResponse<InputStream> response = send(from);
        InputStream body = response.body();
        try {
            long verifyUpTo = accept(response, from);
            if (!install(body)) {
                throw closedException();
            }
            try {
                transfer(body, verifyUpTo);
            } finally {
                uninstall();
            }
            return isWholeFile();
        } finally {
            closeQuietly(body);
        }
    }

    /**
     * Checks a response before any of its body is kept.
     *
     * @return how many bytes at the start of the body must match what is already here: non-zero
     *         only when a server answered a resume with the whole file
     */
    private long accept(HttpResponse<InputStream> response, int from) throws IOException {
        int status = response.statusCode();
        if (status == 200) {
            acceptWholeFile(response, from);
            return from;
        }
        if (status == 206 && from > 0) {
            acceptRange(response, from);
            return 0;
        }
        if (status == 408 || status == 429 || status >= 500) {
            throw new IOException("HTTP " + status);
        }
        throw new PermanentFailure("HTTP " + status + (from > 0 ? " when resuming at byte " + from : ""));
    }

    private void acceptWholeFile(HttpResponse<InputStream> response, int from) throws IOException {
        OptionalLong contentLength = response.headers().firstValueAsLong("Content-Length");
        String responseTag = strongEtag(response);
        synchronized (lock) {
            ensureOpen();
            if (!headersReceived) {
                if (contentLength.isPresent() && contentLength.getAsLong() > policy.maxBytes()) {
                    throw tooLarge(contentLength.getAsLong());
                }
                length = contentLength.orElse(UNKNOWN_LENGTH);
                data = new byte[(int) (length == UNKNOWN_LENGTH
                        ? Math.min(UNKNOWN_LENGTH_INITIAL_CAPACITY, policy.maxBytes()) : length)];
                headersReceived = true;
                etag = responseTag;
                lock.notifyAll();
                return;
            }
            // the server ignored the range or this is a retry from the start: same file?
            if (length != UNKNOWN_LENGTH && contentLength.isPresent() && contentLength.getAsLong() != length) {
                throw changed();
            }
        }
        if (etag != null && responseTag != null && !etag.equals(responseTag)) {
            throw changed();
        }
        if (from > 0) {
            log.debug("The server ignored the range; downloading from the start, checking the first {} bytes", from);
        }
    }

    private void acceptRange(HttpResponse<InputStream> response, int from) throws IOException {
        String header = response.headers().firstValue("Content-Range").orElse(null);
        Matcher range = header == null ? null : CONTENT_RANGE.matcher(header);
        if (range == null || !range.matches()) {
            throw new PermanentFailure("Resuming at byte " + from + " got an unusable Content-Range: " + header);
        }
        long start = Long.parseLong(range.group(1));
        long end = Long.parseLong(range.group(2));
        long total = Long.parseLong(range.group(3));
        if (start != from || end < start || end >= total) {
            throw new PermanentFailure("Resuming at byte " + from + " got the range " + header);
        }
        String responseTag = strongEtag(response);
        if (etag != null && responseTag != null && !etag.equals(responseTag)) {
            throw changed();
        }
        synchronized (lock) {
            ensureOpen();
            if (length == UNKNOWN_LENGTH) {
                if (total > policy.maxBytes()) {
                    throw tooLarge(total);
                }
                length = total;
            } else if (total != length) {
                throw changed();
            }
        }
    }

    /** Copies the body into the buffer, after checking the part that is already here, if any. */
    private void transfer(InputStream body, long verifyUpTo) throws IOException {
        byte[] chunk = new byte[CHUNK_SIZE];
        long position = verifyUpTo > 0 ? 0 : downloaded();
        lastDataNanos = System.nanoTime();
        stalled = false;
        long period = Math.max(10, policy.stallTimeout().toMillis() / 4);
        ScheduledFuture<?> watchdog = WATCHDOG.scheduleAtFixedRate(() -> cutIfStalled(body), period, period,
                TimeUnit.MILLISECONDS);
        try {
            while (true) {
                int n;
                try {
                    n = body.read(chunk);
                } catch (IOException e) {
                    if (stalled) {
                        throw new IOException("No data for " + policy.stallTimeout().toMillis() + " ms", e);
                    }
                    throw e;
                }
                if (n < 0) {
                    return;
                }
                lastDataNanos = System.nanoTime();
                int offset = 0;
                if (position < verifyUpTo) {
                    offset = (int) Math.min(n, verifyUpTo - position);
                    if (!matches(position, chunk, offset)) {
                        throw changed();
                    }
                    position += offset;
                }
                if (offset < n) {
                    append(chunk, offset, n - offset);
                    position += n - offset;
                }
            }
        } finally {
            watchdog.cancel(false);
        }
    }

    private void cutIfStalled(InputStream body) {
        if (System.nanoTime() - lastDataNanos > policy.stallTimeout().toNanos()) {
            stalled = true;
            closeQuietly(body);
        }
    }

    private boolean matches(long position, byte[] chunk, int n) throws IOException {
        synchronized (lock) {
            ensureOpen();
            return position + n <= count
                    && Arrays.equals(data, (int) position, (int) position + n, chunk, 0, n);
        }
    }

    private void append(byte[] chunk, int offset, int n) throws IOException {
        synchronized (lock) {
            ensureOpen();
            long newCount = (long) count + n;
            if (length != UNKNOWN_LENGTH && newCount > length) {
                throw new PermanentFailure("The server sent more than the " + length + " bytes it announced");
            }
            if (newCount > policy.maxBytes()) {
                throw tooLarge(newCount);
            }
            if (newCount > data.length) {
                long grown = Math.max(newCount, Math.min((long) data.length * 2, policy.maxBytes()));
                if (length != UNKNOWN_LENGTH) {
                    grown = Math.max(newCount, Math.min(grown, length));
                }
                data = Arrays.copyOf(data, (int) grown);
            }
            System.arraycopy(chunk, offset, data, count, n);
            count = (int) newCount;
            lock.notifyAll();
        }
    }

    /** Sends one request, following redirects; cancelled by {@link #close()} while it waits. */
    private HttpResponse<InputStream> send(int from) throws IOException {
        URI target = uri;
        for (int redirects = 0; ; redirects++) {
            HttpRequest.Builder request = HttpRequest.newBuilder(target).GET().timeout(policy.headersTimeout());
            if (from > 0) {
                request.header("Range", "bytes=" + from + "-");
                if (etag != null) {
                    // a server whose file changed answers with the whole new file instead of a range
                    request.header("If-Range", etag);
                }
            }
            HttpResponse<InputStream> response = await(http.sendAsync(request.build(),
                    HttpResponse.BodyHandlers.ofInputStream()));
            int status = response.statusCode();
            boolean redirect = status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
            if (!redirect) {
                // where the file really is, so that a resume skips the redirects
                uri = target;
                return response;
            }
            closeQuietly(response.body());
            String location = response.headers().firstValue("Location").orElse(null);
            if (location == null || redirects >= MAX_REDIRECTS) {
                throw new PermanentFailure("HTTP " + status
                        + (location == null ? " without a Location" : ", too many redirects"));
            }
            target = target.resolve(location);
            String scheme = target.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new PermanentFailure("Redirected to an unsupported URL scheme: " + scheme);
            }
        }
    }

    private HttpResponse<InputStream> await(CompletableFuture<HttpResponse<InputStream>> future)
            throws IOException {
        synchronized (lock) {
            if (closed) {
                future.cancel(true);
                throw closedException();
            }
            pendingRequest = future;
        }
        try {
            return future.get();
        } catch (CancellationException e) {
            throw closedException();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new InterruptedIOException("Interrupted while connecting");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw new IOException(e.getCause());
        } finally {
            synchronized (lock) {
                pendingRequest = null;
            }
        }
    }

    /** Makes the body closable by {@link #close()}; false if that already happened. */
    private boolean install(InputStream body) {
        synchronized (lock) {
            if (closed) {
                return false;
            }
            activeBody = body;
            return true;
        }
    }

    private void uninstall() {
        synchronized (lock) {
            activeBody = null;
        }
    }

    private boolean isWholeFile() {
        synchronized (lock) {
            // without a length, a body that ended without an error is all there is
            return length == UNKNOWN_LENGTH ? headersReceived : count == length;
        }
    }

    private boolean reachedEnd() {
        synchronized (lock) {
            return length != UNKNOWN_LENGTH && count == length;
        }
    }

    private long knownLength() {
        synchronized (lock) {
            return length;
        }
    }

    private void finish(long startedNanos, int reconnects) {
        int bytes;
        synchronized (lock) {
            if (closed) {
                return;
            }
            complete = true;
            length = count;
            bytes = count;
            lock.notifyAll();
        }
        log.debug("Downloaded {} bytes in {} ms with {} reconnects", bytes,
                Duration.ofNanos(System.nanoTime() - startedNanos).toMillis(), reconnects);
    }

    private void fail(IOException e) {
        synchronized (lock) {
            if (closed) {
                return;
            }
            failure = e;
            lock.notifyAll();
        }
        log.debug("Download failed at byte {} of {}", downloaded(), knownLength(), e);
    }

    /** Waits before another attempt; false if closed meanwhile. */
    private boolean pause(Duration wait) {
        long deadline = System.nanoTime() + wait.toNanos();
        synchronized (lock) {
            while (!closed) {
                long left = deadline - System.nanoTime();
                if (left <= 0) {
                    return true;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(lock, left);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return false;
        }
    }

    private Duration backoff(int attemptsWithoutProgress) {
        Duration wait = policy.firstBackoff();
        for (int i = 1; i < attemptsWithoutProgress && wait.compareTo(policy.maxBackoff()) < 0; i++) {
            wait = wait.multipliedBy(2);
        }
        return wait.compareTo(policy.maxBackoff()) > 0 ? policy.maxBackoff() : wait;
    }

    private boolean isClosed() {
        synchronized (lock) {
            return closed;
        }
    }

    // callers hold the lock
    private void await() throws InterruptedIOException {
        try {
            lock.wait();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting for audio");
        }
    }

    // callers hold the lock
    private void ensureOpen() throws IOException {
        if (closed) {
            throw closedException();
        }
    }

    private static IOException closedException() {
        return new IOException("Stream closed");
    }

    /** A fresh exception for the reading thread, keeping the download's as its cause. */
    private static IOException rethrown(IOException failure) {
        return new IOException(failure.getMessage(), failure);
    }

    private static PermanentFailure changed() {
        return new PermanentFailure("The song file changed on the server while downloading");
    }

    private PermanentFailure tooLarge(long bytes) {
        return new PermanentFailure("The song file is too large: " + bytes / (1024 * 1024) + " MB, the limit is "
                + policy.maxBytes() / (1024 * 1024) + " MB");
    }

    private static String strongEtag(HttpResponse<?> response) {
        return response.headers().firstValue("ETag").filter(tag -> !tag.startsWith("W/")).orElse(null);
    }

    private static Throwable innermost(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    /** "IOException: closed <- IOException: Socket is not connected": the JDK's message alone hides the cause. */
    private static String causes(Throwable e) {
        StringBuilder chain = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (!chain.isEmpty()) {
                chain.append(" <- ");
            }
            chain.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
        }
        return chain.toString();
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

    /** Not worth retrying: the file is gone, changed, too large, or the server answers nonsense. */
    private static final class PermanentFailure extends IOException {
        PermanentFailure(String message) {
            super(message);
        }
    }
}

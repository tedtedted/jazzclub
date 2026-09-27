package com.tedredington.jazzclub.player.lavaplayer;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.info.AudioTrackInfoProvider;

/**
 * An audio file over HTTP for LavaPlayer's MP4 parser, read through jazzclub's own
 * {@link HttpClient} rather than LavaPlayer's, so jazzclub's proxy settings apply. Reading is
 * sequential; seeking backwards or far ahead costs a {@code Range} request. Pandora's files keep
 * their index before the audio, so a song is normally one request.
 *
 * <p>A connection that drops mid-file is resumed where it stopped, a few times; a file that ends
 * before its {@code Content-Length} is an error, not a short song.
 */
final class HttpSeekableStream extends SeekableInputStream {

    /** Forward seeks up to this far read and discard instead of starting a new request. */
    private static final long MAX_SKIP_DISTANCE = 512 * 1024;
    private static final int MAX_RESUMES = 3;
    /** No Content-Length: the parser then reads to the end, and a drop cannot be told from the end. */
    private static final long UNKNOWN_LENGTH = Long.MAX_VALUE;

    private static final int MAX_REDIRECTS = 5;

    private final HttpClient http;
    /** Where the file really is, after redirects, so that resuming skips them. */
    private final URI uri;
    private InputStream body;
    private long position;
    private int resumes;
    private volatile boolean closed;

    private HttpSeekableStream(HttpClient http, URI uri, InputStream body, long contentLength) {
        super(contentLength, MAX_SKIP_DISTANCE);
        this.http = http;
        this.uri = uri;
        this.body = body;
    }

    /** Starts the download. */
    static HttpSeekableStream open(HttpClient http, URI uri) throws IOException {
        HttpResponse<InputStream> response = send(http, uri, 0);
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("HTTP " + response.statusCode());
        }
        long length = response.headers().firstValueAsLong("Content-Length").orElse(UNKNOWN_LENGTH);
        return new HttpSeekableStream(http, response.uri(), response.body(), length);
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
        while (true) {
            ensureOpen();
            int n;
            try {
                n = body.read(buffer, offset, length);
            } catch (IOException e) {
                resumeAfter(e);
                continue;
            }
            if (n < 0) {
                if (position >= getContentLength() || getContentLength() == UNKNOWN_LENGTH) {
                    return -1;
                }
                resumeAfter(new EOFException("The server ended the file at byte " + position + " of "
                        + getContentLength()));
                continue;
            }
            position += n;
            return n;
        }
    }

    @Override
    public long skip(long distance) throws IOException {
        long skipped = 0;
        byte[] discard = new byte[(int) Math.min(distance, 16 * 1024)];
        while (skipped < distance) {
            int n = read(discard, 0, (int) Math.min(discard.length, distance - skipped));
            if (n < 0) {
                break;
            }
            skipped += n;
        }
        return skipped;
    }

    @Override
    public long getPosition() {
        return position;
    }

    @Override
    protected void seekHard(long target) throws IOException {
        reopenAt(target);
    }

    @Override
    public boolean canSeekHard() {
        return true;
    }

    @Override
    public List<AudioTrackInfoProvider> getTrackInfoProviders() {
        return List.of();
    }

    /** Aborts the download; a read blocked in another thread returns with an exception. */
    @Override
    public void close() throws IOException {
        closed = true;
        body.close();
    }

    private void resumeAfter(IOException cause) throws IOException {
        ensureOpen();
        if (resumes >= MAX_RESUMES || getContentLength() == UNKNOWN_LENGTH) {
            throw cause;
        }
        resumes++;
        try {
            reopenAt(position);
        } catch (IOException e) {
            e.addSuppressed(cause);
            throw e;
        }
    }

    private void reopenAt(long target) throws IOException {
        ensureOpen();
        body.close();
        HttpResponse<InputStream> response = send(http, uri, target);
        InputStream next = response.body();
        if (response.statusCode() == 200) {
            // the server ignored the range: start over and skip ahead
            next.skipNBytes(target);
        } else if (response.statusCode() != 206) {
            next.close();
            throw new IOException("HTTP " + response.statusCode() + " when resuming at byte " + target);
        }
        body = next;
        position = target;
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
    }

    /**
     * Follows redirects itself: jazzclub's clients don't (the Pandora API must not be redirected),
     * but a CDN may, and ffmpeg always did.
     */
    private static HttpResponse<InputStream> send(HttpClient http, URI uri, long from) throws IOException {
        URI target = uri;
        for (int redirects = 0; ; redirects++) {
            HttpRequest.Builder request = HttpRequest.newBuilder(target).GET();
            if (from > 0) {
                request.header("Range", "bytes=" + from + "-");
            }
            HttpResponse<InputStream> response;
            try {
                response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted while connecting");
            }
            int status = response.statusCode();
            boolean redirect = status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
            if (!redirect) {
                return response;
            }
            response.body().close();
            String location = response.headers().firstValue("Location").orElse(null);
            if (location == null || redirects >= MAX_REDIRECTS) {
                throw new IOException("HTTP " + status
                        + (location == null ? " without a Location" : ", too many redirects"));
            }
            target = target.resolve(location);
            String scheme = target.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IOException("Redirected to an unsupported URL scheme: " + scheme);
            }
        }
    }
}

package com.tedredington.jazzclub.player.lavaplayer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;

import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.info.AudioTrackInfoProvider;

/**
 * An audio file over HTTP for LavaPlayer's MP4 parser, read from a {@link SongDownload} that
 * fetches it in the background through jazzclub's own {@link HttpClient}, so jazzclub's proxy
 * settings apply.
 *
 * <p>The read position is the decoder's own; the download goes on ahead of it regardless. Seeking
 * only moves the position, and a read past what has arrived waits for it.
 */
final class HttpSeekableStream extends SeekableInputStream {

    /** No Content-Length: the parser then reads to the end. */
    private static final long UNKNOWN_LENGTH = Long.MAX_VALUE;

    private final SongDownload download;
    private long position;

    private HttpSeekableStream(SongDownload download, long contentLength) {
        // every seek is a hard one: nothing is cheaper than moving the position
        super(contentLength, 0);
        this.download = download;
    }

    /** Waits for the first response of a download that has been started. */
    static HttpSeekableStream over(SongDownload download) throws IOException {
        long length = download.awaitLength();
        return new HttpSeekableStream(download, length == SongDownload.UNKNOWN_LENGTH ? UNKNOWN_LENGTH : length);
    }

    /** Starts downloading {@code uri} and waits for the first response. */
    static HttpSeekableStream open(HttpClient http, URI uri, DownloadPolicy policy) throws IOException {
        SongDownload download = new SongDownload(http, uri, policy);
        download.start();
        try {
            return over(download);
        } catch (IOException e) {
            download.close();
            throw e;
        }
    }

    SongDownload download() {
        return download;
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int n = download.read(position, buffer, offset, length);
        if (n > 0) {
            position += n;
        }
        return n;
    }

    /** Moves the position without waiting for the bytes in between; a later read does. */
    @Override
    public long skip(long distance) {
        if (distance <= 0) {
            return 0;
        }
        long target = getContentLength() == UNKNOWN_LENGTH ? position + distance
                : Math.min(position + distance, getContentLength());
        long skipped = target - position;
        position = target;
        return skipped;
    }

    @Override
    public long getPosition() {
        return position;
    }

    @Override
    protected void seekHard(long target) {
        position = target;
    }

    @Override
    public boolean canSeekHard() {
        return true;
    }

    @Override
    public List<AudioTrackInfoProvider> getTrackInfoProviders() {
        return List.of();
    }

    /** Ends the download; a read blocked in another thread returns with an exception. */
    @Override
    public void close() {
        download.close();
    }
}

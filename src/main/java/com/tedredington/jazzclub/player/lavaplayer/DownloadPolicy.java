package com.tedredington.jazzclub.player.lavaplayer;

import java.time.Duration;

/**
 * The bounds of one song's download: how long to wait, how big a file may be, and how hard to try
 * after the connection breaks.
 *
 * @param headersTimeout          how long one request may wait for its response headers
 * @param stallTimeout            how long a response body may deliver nothing before the
 *                                connection is given up and resumed
 * @param maxBytes                the largest file kept in memory
 * @param maxReconnects           reconnects per song, however much each one delivered
 * @param maxAttemptsWithoutProgress requests in a row that may each deliver less than
 *                                {@code minProgressBytes} before the song fails
 * @param minProgressBytes        what a request must deliver to count as progress, so that a server
 *                                trickling a few bytes per connection is not retried forever
 * @param firstBackoff            the wait before retrying after an attempt without progress; it
 *                                doubles with each further one, up to {@code maxBackoff}
 * @param maxBackoff              the longest wait between attempts
 */
public record DownloadPolicy(Duration headersTimeout, Duration stallTimeout, long maxBytes, int maxReconnects,
                             int maxAttemptsWithoutProgress, long minProgressBytes, Duration firstBackoff,
                             Duration maxBackoff) {

    /**
     * About 90 minutes at Pandora's highest bitrate, 192 kbps; a four-minute song is 2–6 MB.
     */
    static final long DEFAULT_MAX_BYTES = 128L * 1024 * 1024;

    public DownloadPolicy {
        if (headersTimeout.isNegative() || headersTimeout.isZero() || stallTimeout.isNegative()
                || stallTimeout.isZero()) {
            throw new IllegalArgumentException("timeouts must be positive");
        }
        if (maxBytes <= 0 || maxReconnects < 0 || maxAttemptsWithoutProgress < 1 || minProgressBytes < 1) {
            throw new IllegalArgumentException("invalid download bounds");
        }
    }

    /**
     * @param timeout pianobar's {@code timeout}, for the response headers. A stalled body is given
     *                10 seconds at most: the download runs ahead of playback, so the sooner it
     *                reconnects the less of that lead is lost.
     */
    public static DownloadPolicy withTimeout(Duration timeout) {
        Duration stall = timeout.compareTo(Duration.ofSeconds(10)) < 0 ? timeout : Duration.ofSeconds(10);
        return new DownloadPolicy(timeout, stall, DEFAULT_MAX_BYTES, 50, 4, 64 * 1024,
                Duration.ofMillis(500), Duration.ofSeconds(4));
    }
}

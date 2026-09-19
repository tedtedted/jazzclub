package com.tedredington.jazzclub.pandora.model;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * One playlist entry.
 *
 * @param stationId the station the song really came from; differs from the playing station for QuickMix
 * @param gainDb    ReplayGain correction in dB, as sent by Pandora
 */
public record Song(
        String title,
        String artist,
        String album,
        String trackToken,
        String stationId,
        URI audioUrl,
        AudioEncoding encoding,
        URI coverArtUrl,
        URI detailUrl,
        double gainDb,
        Duration length,
        Rating rating) {

    public Song {
        Objects.requireNonNull(trackToken, "trackToken");
        Objects.requireNonNull(audioUrl, "audioUrl");
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(length, "length");
        Objects.requireNonNull(rating, "rating");
    }

    public Song withRating(Rating newRating) {
        return new Song(title, artist, album, trackToken, stationId, audioUrl, encoding, coverArtUrl, detailUrl,
                gainDb, length, newRating);
    }
}

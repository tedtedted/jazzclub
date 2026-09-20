package com.tedredington.jazzclub.pandora.model;

import java.util.List;

/** What a station is built from, and the ratings given on it. Each entry can be deleted by its id. */
public record StationInfo(List<ArtistSeed> artistSeeds, List<SongSeed> songSeeds, List<Feedback> feedback) {

    public StationInfo {
        artistSeeds = List.copyOf(artistSeeds);
        songSeeds = List.copyOf(songSeeds);
        feedback = List.copyOf(feedback);
    }

    public record ArtistSeed(String name, String seedId) {
    }

    public record SongSeed(String title, String artist, String seedId) {
    }

    /** @param positive thumbs up, otherwise thumbs down */
    public record Feedback(String title, String artist, String feedbackId, boolean positive) {
    }
}

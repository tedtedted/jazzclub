package com.tedredington.jazzclub.pandora.model;

import java.util.List;

/** What {@code music.search} found. Each match carries the {@code musicToken} a station can be seeded with. */
public record SearchResult(List<ArtistMatch> artists, List<SongMatch> songs) {

    public SearchResult {
        artists = List.copyOf(artists);
        songs = List.copyOf(songs);
    }

    public boolean isEmpty() {
        return artists.isEmpty() && songs.isEmpty();
    }

    public record ArtistMatch(String name, String musicToken) {
    }

    public record SongMatch(String title, String artist, String musicToken) {
    }
}

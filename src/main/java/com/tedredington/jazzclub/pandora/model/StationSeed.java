package com.tedredington.jazzclub.pandora.model;

/** What a new station is created from. */
public sealed interface StationSeed {

    /** A token from a search result or a genre station; also a shared station's numeric id. */
    record MusicToken(String token) implements StationSeed {
    }

    /** "More like this song", based on a track that is or was playing. */
    record FromSong(Song song) implements StationSeed {
    }

    /** "More by and like this artist", based on a track that is or was playing. */
    record FromArtist(Song song) implements StationSeed {
    }
}

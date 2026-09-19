package com.tedredington.jazzclub.pandora;

import java.util.List;
import java.util.Optional;

import com.tedredington.jazzclub.pandora.error.PandoraException;
import com.tedredington.jazzclub.pandora.model.AudioQuality;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.pandora.model.Station;

/**
 * The Pandora operations jazzclub needs. All methods block, and all failures surface as a
 * {@link PandoraException}.
 */
public interface PandoraClient {

    /** Performs the partner and user login. Must succeed before any other call. */
    void login(UserCredentials credentials);

    /** All of the listener's stations, in the order Pandora returns them. */
    List<Station> stations();

    /** The next handful of songs (usually four) for a station. Audio URLs expire, so fetch late. */
    List<Song> playlist(Station station, AudioQuality quality);

    /** Thumbs up ({@code positive}) or down. Feedback belongs to the station the song came from. */
    void addFeedback(Song song, boolean positive);

    /** "Tired of this song": Pandora will not play it on any station for a month. */
    void sleepSong(Song song);

    /** Why Pandora picked this song, as a sentence; empty if it has nothing to say. */
    Optional<String> explain(Song song);
}

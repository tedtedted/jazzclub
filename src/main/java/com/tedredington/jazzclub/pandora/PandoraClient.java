package com.tedredington.jazzclub.pandora;

import java.util.List;

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
}

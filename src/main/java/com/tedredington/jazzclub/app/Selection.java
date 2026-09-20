package com.tedredington.jazzclub.app;

import java.util.Optional;

import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.pandora.model.Station;

/**
 * What an action works on. Normally the playing station and song; from the history menu it is a past
 * song together with the station it came from.
 *
 * @param station may be {@code null}
 * @param song    may be {@code null}
 */
public record Selection(Station station, Song song) {

    public static final Selection NONE = new Selection(null, null);

    public Optional<Station> stationIfAny() {
        return Optional.ofNullable(station);
    }

    public Optional<Song> songIfAny() {
        return Optional.ofNullable(song);
    }
}

package com.tedredington.jazzclub.app.event;

import java.time.Duration;
import java.util.List;

import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.pandora.model.Station;

/**
 * Something happened that the outside world may care about: a scrobbler, a desktop notification.
 * A snapshot, safe to hand to another thread.
 *
 * @param station     the station concerned, or {@code null}
 * @param song        the song concerned, or {@code null}
 * @param songStation where {@code song} really came from when {@code station} is the QuickMix, else {@code null}
 * @param played      how much of {@code song} has been heard
 * @param upcoming    the songs queued after {@code song}
 * @param stations    all stations, sorted by name
 */
public record PlayerEvent(
        EventType type,
        EventResult result,
        Station station,
        Song song,
        Station songStation,
        Duration played,
        List<Song> upcoming,
        List<Station> stations) {

    public PlayerEvent {
        upcoming = List.copyOf(upcoming);
        stations = List.copyOf(stations);
    }
}

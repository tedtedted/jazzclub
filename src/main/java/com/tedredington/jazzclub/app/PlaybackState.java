package com.tedredington.jazzclub.app;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.pandora.model.Station;

/** What is playing, what comes next and what came before. Touched only by the main loop thread. */
public final class PlaybackState {

    private final int historySize;
    private final Deque<Song> upcoming = new ArrayDeque<>();
    private final Deque<Song> history = new ArrayDeque<>();

    private List<Station> stations = List.of();
    private Station station;
    private Song song;
    private boolean quitRequested;

    public PlaybackState(int historySize) {
        this.historySize = historySize;
    }

    public List<Station> stations() {
        return stations;
    }

    public void stations(List<Station> stations) {
        this.stations = List.copyOf(stations);
    }

    /** Adds a station, or replaces the one with the same token. The playing station is kept in step. */
    public void putStation(Station updated) {
        List<Station> changed = new ArrayList<>(stations);
        changed.removeIf(s -> s.token().equals(updated.token()));
        changed.add(updated);
        stations = List.copyOf(changed);
        if (station != null && station.token().equals(updated.token())) {
            station = updated;
        }
    }

    public void removeStation(Station removed) {
        stations = stations.stream().filter(s -> !s.token().equals(removed.token())).toList();
    }

    public Optional<Station> station() {
        return Optional.ofNullable(station);
    }

    /** Switches station. Songs queued for the old one are dropped. */
    public void changeStation(Station newStation) {
        this.station = newStation;
        upcoming.clear();
    }

    /** Stops the radio: nothing more is fetched or played until a station is chosen again. */
    public void clearStation() {
        this.station = null;
        upcoming.clear();
    }

    public Optional<Song> song() {
        return Optional.ofNullable(song);
    }

    /** Replaces the playing song with an updated copy, e.g. after rating it. */
    public void updateSong(Song updated) {
        this.song = updated;
    }

    public List<Song> upcoming() {
        return List.copyOf(upcoming);
    }

    public void enqueue(List<Song> songs) {
        upcoming.addAll(songs);
    }

    /** Moves on: the playing song goes to the history, the head of the queue becomes current. */
    public Optional<Song> advance() {
        finishSong();
        song = upcoming.pollFirst();
        return song();
    }

    /** The playing song is over, without starting another. */
    public void finishSong() {
        if (song != null && historySize > 0) {
            history.addFirst(song);
            while (history.size() > historySize) {
                history.removeLast();
            }
        }
        song = null;
    }

    public List<Song> history() {
        return List.copyOf(history);
    }

    public Optional<Station> findStation(String stationId) {
        return stations.stream().filter(s -> s.token().equals(stationId)).findFirst();
    }

    public boolean quitRequested() {
        return quitRequested;
    }

    public void requestQuit() {
        quitRequested = true;
    }
}

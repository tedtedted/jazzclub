package com.tedredington.jazzclub.testsupport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import com.tedredington.jazzclub.pandora.PandoraClient;
import com.tedredington.jazzclub.pandora.UserCredentials;
import com.tedredington.jazzclub.pandora.error.PandoraException;
import com.tedredington.jazzclub.pandora.model.AudioQuality;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.pandora.model.Station;

/** A Pandora that answers from canned data and notes what it was asked. */
public final class StubPandoraClient implements PandoraClient {

    public final List<String> calls = new ArrayList<>();
    public final Deque<List<Song>> playlists = new ArrayDeque<>();
    public List<Station> stations = List.of();
    public Optional<String> explanation = Optional.empty();
    /** Thrown by the next call, once. */
    public PandoraException failure;

    private void record(String call) {
        calls.add(call);
        if (failure != null) {
            PandoraException toThrow = failure;
            failure = null;
            throw toThrow;
        }
    }

    @Override
    public void login(UserCredentials credentials) {
        record("login " + credentials.username());
    }

    @Override
    public List<Station> stations() {
        record("stations");
        return stations;
    }

    @Override
    public List<Song> playlist(Station station, AudioQuality quality) {
        record("playlist " + station.name() + " " + quality);
        List<Song> next = playlists.pollFirst();
        return next == null ? List.of() : next;
    }

    @Override
    public void addFeedback(Song song, boolean positive) {
        record((positive ? "love " : "ban ") + song.title());
    }

    @Override
    public void sleepSong(Song song) {
        record("tired " + song.title());
    }

    @Override
    public Optional<String> explain(Song song) {
        record("explain " + song.title());
        return explanation;
    }
}

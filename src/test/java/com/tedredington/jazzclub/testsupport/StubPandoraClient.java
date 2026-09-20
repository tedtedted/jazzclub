package com.tedredington.jazzclub.testsupport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import com.tedredington.jazzclub.pandora.PandoraClient;
import com.tedredington.jazzclub.pandora.UserCredentials;
import com.tedredington.jazzclub.pandora.error.PandoraException;
import com.tedredington.jazzclub.pandora.model.AccountChange;
import com.tedredington.jazzclub.pandora.model.AccountSettings;
import com.tedredington.jazzclub.pandora.model.AudioQuality;
import com.tedredington.jazzclub.pandora.model.GenreCategory;
import com.tedredington.jazzclub.pandora.model.SearchResult;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.pandora.model.Station;
import com.tedredington.jazzclub.pandora.model.StationInfo;
import com.tedredington.jazzclub.pandora.model.StationMode;
import com.tedredington.jazzclub.pandora.model.StationSeed;

/** A Pandora that answers from canned data and notes what it was asked. */
public final class StubPandoraClient implements PandoraClient {

    public final List<String> calls = new ArrayList<>();
    public final Deque<List<Song>> playlists = new ArrayDeque<>();
    public List<Station> stations = List.of();
    public Optional<String> explanation = Optional.empty();
    public SearchResult searchResult = new SearchResult(List.of(), List.of());
    public List<GenreCategory> genres = List.of();
    public Station created = new Station("500", "Created Radio", true, false, false);
    public StationInfo stationInfo = new StationInfo(List.of(), List.of(), List.of());
    public List<StationMode> modes = List.of();
    public AccountSettings settings = new AccountSettings("me@example.com", false);
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

    @Override
    public SearchResult search(String text) {
        record("search " + text);
        return searchResult;
    }

    @Override
    public Station createStation(StationSeed seed) {
        record("create " + switch (seed) {
            case StationSeed.MusicToken(String token) -> "token " + token;
            case StationSeed.FromSong(Song song) -> "song " + song.title();
            case StationSeed.FromArtist(Song song) -> "artist " + song.title();
        });
        return created;
    }

    @Override
    public void addMusic(Station station, String musicToken) {
        record("addMusic " + station.name() + " " + musicToken);
    }

    @Override
    public void renameStation(Station station, String newName) {
        record("rename " + station.name() + " -> " + newName);
    }

    @Override
    public void deleteStation(Station station) {
        record("delete " + station.name());
    }

    @Override
    public List<GenreCategory> genreStations() {
        record("genres");
        return genres;
    }

    @Override
    public void setQuickMix(Collection<Station> members) {
        record("quickmix " + members.stream().map(Station::name).sorted().toList());
    }

    @Override
    public void transformSharedStation(Station station) {
        record("transform " + station.name());
    }

    @Override
    public void bookmarkSong(Song song) {
        record("bookmark song " + song.title());
    }

    @Override
    public void bookmarkArtist(Song song) {
        record("bookmark artist " + song.title());
    }

    @Override
    public StationInfo stationInfo(Station station) {
        record("info " + station.name());
        return stationInfo;
    }

    @Override
    public void deleteSeed(String seedId) {
        record("deleteSeed " + seedId);
    }

    @Override
    public void deleteFeedback(String feedbackId) {
        record("deleteFeedback " + feedbackId);
    }

    @Override
    public List<StationMode> stationModes(Station station) {
        record("modes " + station.name());
        return modes;
    }

    @Override
    public void setStationMode(Station station, StationMode mode) {
        record("setMode " + station.name() + " " + mode.id());
    }

    @Override
    public AccountSettings accountSettings() {
        record("settings");
        return settings;
    }

    @Override
    public void changeAccount(AccountChange change) {
        // the real toString masks the password; tests need to see what was sent
        record("changeAccount user=" + change.newUsername() + " password=" + change.newPassword()
                + " filter=" + change.explicitContentFilter());
    }
}

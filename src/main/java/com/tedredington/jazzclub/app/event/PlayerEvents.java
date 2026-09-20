package com.tedredington.jazzclub.app.event;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import com.tedredington.jazzclub.app.PlaybackState;
import com.tedredington.jazzclub.app.Selection;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.pandora.model.Station;
import com.tedredington.jazzclub.player.AudioPlayer;

/** Builds {@link PlayerEvent}s from the current state and passes them on. Knows nothing about who listens. */
public final class PlayerEvents {

    private final PlaybackState state;
    private final AudioPlayer player;
    private final Consumer<PlayerEvent> sink;
    private final Comparator<Station> order;

    /** @param order the order of the station menu, so {@code station0..n} match the numbers on screen */
    public PlayerEvents(PlaybackState state, AudioPlayer player, Consumer<PlayerEvent> sink,
                        Comparator<Station> order) {
        this.state = state;
        this.player = player;
        this.sink = sink;
        this.order = order;
    }

    public void emit(EventType type, Selection selection, EventResult result) {
        boolean isPlayingSong = selection.song() != null
                && state.song().filter(s -> s.trackToken().equals(selection.song().trackToken())).isPresent();
        emit(type, selection, result, isPlayingSong ? player.elapsed() : Duration.ZERO);
    }

    /** @param played for events about a song that is no longer in the player */
    public void emit(EventType type, Selection selection, EventResult result, Duration played) {
        // actions hold the song as it was when the key was pressed; listeners want it as it is now,
        // e.g. "songlove" with the new rating
        Song song = selection.song() == null ? null : state.current(selection.song());
        Station station = selection.station() == null ? null
                : state.findStation(selection.station().token()).orElse(selection.station());
        Station songStation = song != null && station != null && station.quickMix()
                ? state.findStation(song.stationId()).orElse(null)
                : null;
        boolean isPlayingSong = song != null
                && state.song().filter(s -> s.trackToken().equals(song.trackToken())).isPresent();
        List<Station> sorted = state.stations().stream().sorted(order).toList();
        sink.accept(new PlayerEvent(type, result, station, song, songStation, played,
                isPlayingSong ? state.upcoming() : List.of(), sorted));
    }
}

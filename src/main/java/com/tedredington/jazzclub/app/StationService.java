package com.tedredington.jazzclub.app;

import java.util.List;

import com.tedredington.jazzclub.app.event.EventType;
import com.tedredington.jazzclub.pandora.PandoraClient;
import com.tedredington.jazzclub.pandora.model.Station;
import com.tedredington.jazzclub.pandora.model.StationSeed;

/**
 * Changes to the listener's stations. Each operation calls Pandora and then brings the local station
 * list in line, so the menu is right without fetching it again. Local state only changes when the
 * call succeeded.
 */
public final class StationService {

    private final PandoraClient client;
    private final PandoraCalls calls;
    private final PlaybackState state;
    private final Radio radio;

    public StationService(PandoraClient client, PandoraCalls calls, PlaybackState state, Radio radio) {
        this.client = client;
        this.calls = calls;
        this.state = state;
        this.radio = radio;
    }

    /**
     * @param announcement e.g. {@code "Creating station... "}
     * @param event        creating, adding a genre station and adding a shared one are distinct events
     */
    public Station create(StationSeed seed, String announcement, EventType event, Selection selection) {
        return calls.call(announcement, event, selection, () -> {
            Station created = client.createStation(seed);
            state.putStation(created);
            return created;
        });
    }

    public void addMusic(Station station, String musicToken) {
        Station own = transformIfShared(station);
        calls.run("Adding music to station... ", EventType.STATION_ADD_MUSIC, selectionFor(own),
                () -> client.addMusic(own, musicToken));
    }

    public void rename(Station station, String newName) {
        Station own = transformIfShared(station);
        calls.run("Renaming station... ", EventType.STATION_RENAME, selectionFor(own), () -> {
            client.renameStation(own, newName);
            state.putStation(own.withName(newName));
        });
    }

    public void delete(Station station) {
        boolean wasPlaying = state.station().filter(s -> s.token().equals(station.token())).isPresent();
        calls.run("Deleting station... ", EventType.STATION_DELETE, selectionFor(station), () -> {
            client.deleteStation(station);
            state.removeStation(station);
        });
        if (wasPlaying) {
            radio.stop();
        }
    }

    /** @param selection every station, with {@link Station#inQuickMix()} as the listener wants it */
    public void setQuickMix(List<Station> selection) {
        calls.run("Setting QuickMix stations... ", EventType.STATION_QUICKMIX_TOGGLE, state.selection(), () -> {
            client.setQuickMix(selection.stream().filter(Station::inQuickMix).toList());
            selection.forEach(state::putStation);
        });
    }

    /**
     * Stations shared by somebody else are read-only until transformed into an own copy; pianobar does
     * that silently before every edit and rating, and so does jazzclub.
     *
     * @return the station to continue with
     */
    public Station transformIfShared(Station station) {
        if (station.creator()) {
            return station;
        }
        return calls.call("Transforming station... ", null, Selection.NONE, () -> {
            client.transformSharedStation(station);
            Station own = station.asOwned();
            state.putStation(own);
            return own;
        });
    }

    /** The station being edited, with the playing song only if it belongs to the playing station. */
    private Selection selectionFor(Station station) {
        return new Selection(station, state.song().orElse(null));
    }
}

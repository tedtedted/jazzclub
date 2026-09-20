package com.tedredington.jazzclub.app;

import java.util.List;

import com.tedredington.jazzclub.pandora.PandoraClient;
import com.tedredington.jazzclub.pandora.model.Station;
import com.tedredington.jazzclub.pandora.model.StationSeed;
import com.tedredington.jazzclub.ui.Console;

/**
 * Changes to the listener's stations. Each operation announces itself like pianobar, calls Pandora,
 * and then brings the local station list in line, so the menu is right without fetching it again.
 * A failing call throws; the dispatcher completes the announcement with the error.
 */
public final class StationService {

    private final PandoraClient client;
    private final PlaybackState state;
    private final Radio radio;
    private final Console console;

    public StationService(PandoraClient client, PlaybackState state, Radio radio, Console console) {
        this.client = client;
        this.state = state;
        this.radio = radio;
        this.console = console;
    }

    /** @param announcement e.g. {@code "Creating station... "} */
    public Station create(StationSeed seed, String announcement) {
        console.info(announcement);
        Station created = client.createStation(seed);
        state.putStation(created);
        console.append("Ok.\n");
        return created;
    }

    public void addMusic(Station station, String musicToken) {
        Station own = transformIfShared(station);
        console.info("Adding music to station... ");
        client.addMusic(own, musicToken);
        console.append("Ok.\n");
    }

    public void rename(Station station, String newName) {
        Station own = transformIfShared(station);
        console.info("Renaming station... ");
        client.renameStation(own, newName);
        state.putStation(own.withName(newName));
        console.append("Ok.\n");
    }

    public void delete(Station station) {
        console.info("Deleting station... ");
        client.deleteStation(station);
        boolean wasPlaying = state.station().filter(s -> s.token().equals(station.token())).isPresent();
        state.removeStation(station);
        console.append("Ok.\n");
        if (wasPlaying) {
            radio.stop();
        }
    }

    /** @param selection every station, with {@link Station#inQuickMix()} as the listener wants it */
    public void setQuickMix(List<Station> selection) {
        console.info("Setting QuickMix stations... ");
        client.setQuickMix(selection.stream().filter(Station::inQuickMix).toList());
        selection.forEach(state::putStation);
        console.append("Ok.\n");
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
        console.info("Transforming station... ");
        client.transformSharedStation(station);
        Station own = station.asOwned();
        state.putStation(own);
        console.append("Ok.\n");
        return own;
    }
}

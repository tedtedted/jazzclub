package com.tedredington.jazzclub.app.action;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.tedredington.jazzclub.app.ActionId;
import com.tedredington.jazzclub.app.KeyAction;
import com.tedredington.jazzclub.app.ListPicker;
import com.tedredington.jazzclub.app.MusicSearch;
import com.tedredington.jazzclub.app.PlaybackState;
import com.tedredington.jazzclub.app.StationService;
import com.tedredington.jazzclub.pandora.PandoraClient;
import com.tedredington.jazzclub.pandora.model.GenreCategory;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.pandora.model.StationSeed;
import com.tedredington.jazzclub.ui.Console;
import com.tedredington.jazzclub.ui.MessageType;
import com.tedredington.jazzclub.ui.Prompter;
import org.springframework.stereotype.Component;

/** The four ways to get a new station: search, the playing song, Pandora's genre list, a shared id. */
@Component
class CreateStationAction implements KeyAction {

    private final PandoraClient client;
    private final PlaybackState state;
    private final StationService stations;
    private final MusicSearch search;
    private final ListPicker picker;
    private final Prompter prompter;
    private final Console console;

    /** Fetched once; Pandora's genre list does not change while we run. */
    private List<GenreCategory> genreCategories;

    CreateStationAction(PandoraClient client, PlaybackState state, StationService stations, MusicSearch search,
                        ListPicker picker, Prompter prompter, Console console) {
        this.client = client;
        this.state = state;
        this.stations = stations;
        this.search = search;
        this.picker = picker;
        this.prompter = prompter;
        this.console = console;
    }

    @Override
    public Set<ActionId> ids() {
        return Set.of(ActionId.STATION_CREATE, ActionId.STATION_CREATE_FROM_SONG, ActionId.STATION_ADD_GENRE,
                ActionId.STATION_ADD_SHARED);
    }

    @Override
    public void execute(ActionId id) {
        switch (id) {
            case STATION_CREATE -> search.selectMusicToken("Create station from artist or title: ")
                    .ifPresent(token -> stations.create(new StationSeed.MusicToken(token), "Creating station... "));
            case STATION_CREATE_FROM_SONG -> fromPlayingSong();
            case STATION_ADD_GENRE -> fromGenre();
            case STATION_ADD_SHARED -> fromSharedId();
            default -> throw new IllegalArgumentException("Not a create action: " + id);
        }
    }

    private void fromPlayingSong() {
        Song song = state.song().orElseThrow();
        console.print(MessageType.QUESTION, "Create station from [s]ong or [a]rtist? ");
        prompter.readChar("sa")
                .map(kind -> kind == 's' ? new StationSeed.FromSong(song) : new StationSeed.FromArtist(song))
                .ifPresent(seed -> stations.create(seed, "Creating station... "));
    }

    private void fromGenre() {
        if (genreCategories == null) {
            console.info("Receiving genre stations... ");
            genreCategories = client.genreStations();
            console.append("Ok.\n");
        }
        Optional<GenreCategory.Genre> genre = picker.pick(genreCategories, GenreCategory::name, "Select category: ")
                .flatMap(category -> picker.pick(category.genres(), GenreCategory.Genre::name, "Select genre: "));
        genre.ifPresent(g -> stations.create(new StationSeed.MusicToken(g.musicToken()),
                "Adding genre station \"" + g.name() + "\"... "));
    }

    private void fromSharedId() {
        console.print(MessageType.QUESTION, "Station id: ");
        prompter.readLine("0123456789").ifPresent(stationId ->
                stations.create(new StationSeed.MusicToken(stationId), "Adding shared station... "));
    }
}

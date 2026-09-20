package com.tedredington.jazzclub.app.action;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.tedredington.jazzclub.app.ActionContext;
import com.tedredington.jazzclub.app.ActionId;
import com.tedredington.jazzclub.app.KeyAction;
import com.tedredington.jazzclub.app.ListPicker;
import com.tedredington.jazzclub.app.MusicSearch;
import com.tedredington.jazzclub.app.PandoraCalls;
import com.tedredington.jazzclub.app.Selection;
import com.tedredington.jazzclub.app.StationService;
import com.tedredington.jazzclub.app.event.EventType;
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
    private final PandoraCalls calls;
    private final StationService stations;
    private final MusicSearch search;
    private final ListPicker picker;
    private final Prompter prompter;
    private final Console console;

    /** Fetched once; Pandora's genre list does not change while we run. */
    private List<GenreCategory> genreCategories;

    CreateStationAction(PandoraClient client, PandoraCalls calls, StationService stations, MusicSearch search,
                        ListPicker picker, Prompter prompter, Console console) {
        this.client = client;
        this.calls = calls;
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
    public void execute(ActionId id, ActionContext context) {
        switch (id) {
            case STATION_CREATE -> search.selectMusicToken("Create station from artist or title: ")
                    .ifPresent(token -> stations.create(new StationSeed.MusicToken(token), "Creating station... ",
                            EventType.STATION_CREATE, context.selection()));
            case STATION_CREATE_FROM_SONG -> fromSong(context.selection());
            case STATION_ADD_GENRE -> fromGenre(context.selection());
            case STATION_ADD_SHARED -> fromSharedId(context.selection());
            default -> throw new IllegalArgumentException("Not a create action: " + id);
        }
    }

    private void fromSong(Selection selection) {
        Song song = selection.song();
        console.print(MessageType.QUESTION, "Create station from [s]ong or [a]rtist? ");
        prompter.readChar("sa")
                .map(kind -> kind == 's' ? new StationSeed.FromSong(song) : new StationSeed.FromArtist(song))
                .ifPresent(seed -> stations.create(seed, "Creating station... ", EventType.STATION_CREATE, selection));
    }

    private void fromGenre(Selection selection) {
        if (genreCategories == null) {
            genreCategories = calls.call("Receiving genre stations... ", EventType.STATION_FETCH_GENRE, selection,
                    client::genreStations);
        }
        Optional<GenreCategory.Genre> genre = picker.pick(genreCategories, GenreCategory::name, "Select category: ")
                .flatMap(category -> picker.pick(category.genres(), GenreCategory.Genre::name, "Select genre: "));
        genre.ifPresent(g -> stations.create(new StationSeed.MusicToken(g.musicToken()),
                "Adding genre station \"" + g.name() + "\"... ", EventType.STATION_ADD_GENRE, selection));
    }

    private void fromSharedId(Selection selection) {
        console.print(MessageType.QUESTION, "Station id: ");
        prompter.readLine("0123456789").ifPresent(stationId ->
                stations.create(new StationSeed.MusicToken(stationId), "Adding shared station... ",
                        EventType.STATION_ADD_SHARED, selection));
    }
}

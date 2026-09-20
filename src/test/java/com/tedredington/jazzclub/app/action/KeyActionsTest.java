package com.tedredington.jazzclub.app.action;

import static com.tedredington.jazzclub.testsupport.TestData.EVANS;
import static com.tedredington.jazzclub.testsupport.TestData.FORMAT;
import static com.tedredington.jazzclub.testsupport.TestData.HARD_BOP;
import static com.tedredington.jazzclub.testsupport.TestData.QUICKMIX;
import static com.tedredington.jazzclub.testsupport.TestData.song;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.tedredington.jazzclub.app.ActionDispatcher;
import com.tedredington.jazzclub.app.ActionId;
import com.tedredington.jazzclub.app.KeyAction;
import com.tedredington.jazzclub.app.KeyBindings;
import com.tedredington.jazzclub.app.ListPicker;
import com.tedredington.jazzclub.app.MusicSearch;
import com.tedredington.jazzclub.app.PandoraCalls;
import com.tedredington.jazzclub.app.PlaybackState;
import com.tedredington.jazzclub.app.Radio;
import com.tedredington.jazzclub.app.StationPicker;
import com.tedredington.jazzclub.app.StationService;
import com.tedredington.jazzclub.app.StationSort;
import com.tedredington.jazzclub.app.event.EventType;
import com.tedredington.jazzclub.app.event.PlayerEvents;
import com.tedredington.jazzclub.pandora.error.PandoraApiException;
import com.tedredington.jazzclub.pandora.model.AccountSettings;
import com.tedredington.jazzclub.pandora.model.AudioQuality;
import com.tedredington.jazzclub.pandora.model.GenreCategory;
import com.tedredington.jazzclub.pandora.model.SearchResult;
import com.tedredington.jazzclub.pandora.model.Station;
import com.tedredington.jazzclub.pandora.model.StationInfo;
import com.tedredington.jazzclub.pandora.model.StationMode;
import com.tedredington.jazzclub.pandora.model.Rating;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.player.PlaybackResult;
import com.tedredington.jazzclub.testsupport.FakeAudioPlayer;
import com.tedredington.jazzclub.testsupport.RecordingConsole;
import com.tedredington.jazzclub.testsupport.RecordingEvents;
import com.tedredington.jazzclub.testsupport.ScriptedPrompter;
import com.tedredington.jazzclub.testsupport.StubPandoraClient;
import com.tedredington.jazzclub.ui.Renderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** All key actions, driven through the dispatcher exactly as key presses arrive in the app. */
class KeyActionsTest {

    private final StubPandoraClient client = new StubPandoraClient();
    private final FakeAudioPlayer player = new FakeAudioPlayer();
    private final PlaybackState state = new PlaybackState(5);
    private final RecordingConsole console = new RecordingConsole();
    private final ScriptedPrompter prompter = new ScriptedPrompter();
    private final Renderer renderer = new Renderer(FORMAT);
    private final KeyBindings bindings = new KeyBindings(Map.of());
    private final RecordingEvents events = new RecordingEvents();
    private final PlayerEvents playerEvents = events.on(state, player);
    private final PandoraCalls calls = new PandoraCalls(console, playerEvents);
    private final Radio radio = new Radio(client, player, state, console, renderer, AudioQuality.HIGH, 3,
            playerEvents);
    private final StationService stationService = new StationService(client, calls, state, radio);
    private final StationPicker stationPicker = new StationPicker(console, prompter, renderer,
            StationSort.NAME_AZ.comparator(), true);
    private final ListPicker listPicker = new ListPicker(console, prompter);
    private final MusicSearch musicSearch = new MusicSearch(client, console, prompter, listPicker);
    private final List<KeyAction> actions = List.of(
            new HelpAction(bindings, console),
            new RateSongAction(client, calls, state, radio, stationService),
            new ExplainAction(client, calls, console),
            new SongInfoAction(state, console, renderer),
            new TransportAction(player, radio, state),
            new ChangeStationAction(stationPicker, state, radio),
            new CreateStationAction(client, calls, stationService, musicSearch, listPicker, prompter, console),
            new EditStationAction(state, stationService, musicSearch, stationPicker, prompter, console),
            new BookmarkAction(client, calls, prompter, console),
            new HistoryAction(state, listPicker, prompter, console, renderer),
            new ManageStationAction(client, calls, state, radio, listPicker, prompter, console, renderer),
            new SettingsAction(client, calls, prompter, console));
    private final ActionDispatcher dispatcher = new ActionDispatcher(bindings, state, console, actions);

    private final Song a = song("a", "200");
    private final Song b = song("b", "300");

    @BeforeEach
    void playing() {
        state.stations(List.of(QUICKMIX, EVANS, HARD_BOP));
        client.playlists.add(List.of(a, b));
        radio.tune(EVANS);
        client.calls.clear();
        console.clear();
        events.clear();
    }

    /** Ends the playing song the way the player thread and main loop would. */
    private void songEnds() {
        long id = player.lastId();
        player.finish();
        radio.onTrackFinished(id, PlaybackResult.completed());
        client.calls.clear();
        console.clear();
        events.clear();
    }

    private void press(String keys) {
        keys.chars().forEach(c -> dispatcher.dispatch((char) c));
    }

    @Test
    void everyActionHasExactlyOneImplementation() {
        assertThatThrownBy(() -> new ActionDispatcher(bindings, state, console, actions.subList(1, actions.size())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("HELP");
        List<KeyAction> doubled = new java.util.ArrayList<>(actions);
        doubled.add(new HelpAction(bindings, console));
        assertThatThrownBy(() -> new ActionDispatcher(bindings, state, console, doubled))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Two implementations");
    }

    @Test
    void helpListsEveryBoundActionWithItsCurrentKey() {
        KeyBindings custom = new KeyBindings(Map.of("act_songlove", "l", "act_songban", "disabled"));
        new HelpAction(custom, console).execute(ActionId.HELP, null);

        assertThat(console.output()).startsWith("\r\tl    love song\n\ta    add music to station\n")
                .contains("\tq    quit\n")
                .doesNotContain("    ban song")
                .doesNotContain("act_");
    }

    @Test
    void loveRatesTheSongAndKeepsPlaying() {
        press("+");

        assertThat(client.calls).containsExactly("love a");
        assertThat(state.song()).map(Song::rating).contains(Rating.LOVE);
        assertThat(player.stops()).isZero();
        assertThat(console.output()).isEqualTo("(i) Loving song... Ok.\n");
    }

    @Test
    void banRatesAndSkips() {
        press("-");

        assertThat(client.calls).containsExactly("ban a");
        assertThat(player.stops()).isEqualTo(1);
        assertThat(console.output()).isEqualTo("(i) Banning song... Ok.\n");
    }

    @Test
    void tiredShelvesAndSkips() {
        press("t");

        assertThat(client.calls).containsExactly("tired a");
        assertThat(state.song()).map(Song::rating).contains(Rating.TIRED);
        assertThat(player.stops()).isEqualTo(1);
        assertThat(console.output()).isEqualTo("(i) Putting song on shelf... Ok.\n");
    }

    @Test
    void aFailedRatingIsReportedOnTheSameLineAndChangesNothing() {
        client.failure = new PandoraApiException(1000, null);

        press("-");

        assertThat(console.output()).isEqualTo("(i) Banning song... Error: Read only mode. Try again later.\n");
        assertThat(state.song()).map(Song::rating).contains(Rating.NONE);
        assertThat(player.stops()).isZero();
    }

    @Test
    void explainPrintsPandorasReasoning() {
        client.explanation = Optional.of("We're playing this track because it features swing.");

        press("e");

        assertThat(console.output()).isEqualTo(
                "(i) Receiving explanation... Ok.\n(i) We're playing this track because it features swing.\n");
    }

    @Test
    void explainWithoutAnAnswerSaysSo() {
        press("e");

        assertThat(console.output()).endsWith("/!\\ No explanation provided.\n");
    }

    @Test
    void infoShowsStationAndSong() {
        press("i");

        assertThat(console.output()).isEqualTo("""
                |>  Station "Bill Evans Radio" (200)
                |>  "a" by "Artist of a" on "Album of a"
                """);
    }

    @Test
    void upcomingListsTheQueueNamingForeignStations() {
        press("u");

        assertThat(console.output()).isEqualTo("\t 0) Artist of b - b\n");

        Renderer withStation = new Renderer(new com.tedredington.jazzclub.config.JazzclubProperties.Format(
                "", "", "%t%@%s", "", "", "", "", " @ ", java.util.Map.of()));
        console.clear();
        new SongInfoAction(state, console, withStation).execute(ActionId.UPCOMING, null);
        assertThat(console.output()).isEqualTo("\tb @ Hard Bop Radio\n");
    }

    @Test
    void upcomingWithAnEmptyQueueSaysSo() {
        press("n");
        player.finish();
        radio.onTrackFinished(player.lastId(), PlaybackResult.stopped());
        console.clear();

        press("u");

        assertThat(console.output()).isEqualTo("(i) No songs in queue.\n");
    }

    @Test
    void nextSkips() {
        press("n");

        assertThat(player.stops()).isEqualTo(1);
    }

    @Test
    void pauseKeysToggleAndTheExplicitOnesAreIdempotent() {
        press("p");
        assertThat(player.isPaused()).isTrue();
        press(" ");
        assertThat(player.isPaused()).isFalse();
        press("SS");
        assertThat(player.isPaused()).isTrue();
        press("PP");
        assertThat(player.isPaused()).isFalse();
    }

    @Test
    void volumeMovesInStepsOfOneDecibelAndResetsToZero() {
        press("(((");
        assertThat(player.volume()).isEqualTo(-3);
        press(")");
        assertThat(player.volume()).isEqualTo(-2);
        press("^");
        assertThat(player.volume()).isZero();
    }

    @Test
    void quitOnlyAsksTheLoopToEndSoTheShutdownCanStillReadThePosition() {
        player.elapsed(java.time.Duration.ofSeconds(42));

        press("q");
        assertThat(state.quitRequested()).isTrue();
        assertThat(player.stops()).as("stopping is the shutdown's job").isZero();

        radio.shutdown();
        assertThat(events.last().type()).isEqualTo(EventType.SONG_FINISH);
        assertThat(events.last().played()).hasSeconds(42);
        assertThat(player.stops()).isEqualTo(1);
    }

    @Test
    void changeStationAsksAndTunesIn() {
        client.playlists.add(List.of(song("c", "300")));
        prompter.answer("hard");

        press("s");

        assertThat(state.station()).contains(HARD_BOP);
        assertThat(player.stops()).isEqualTo(1);
    }

    @Test
    void backingOutOfTheStationMenuChangesNothing() {
        prompter.answer("");

        press("s");

        assertThat(state.station()).contains(EVANS);
        assertThat(player.stops()).isZero();
    }

    @Test
    void ratingASongFromASharedStationTransformsThatStationFirst() {
        press("n");
        player.finish();
        radio.onTrackFinished(player.lastId(), PlaybackResult.stopped()); // now playing b, from shared Hard Bop
        client.calls.clear();

        press("+");

        assertThat(client.calls).containsExactly("transform Hard Bop Radio", "love b");
        assertThat(state.findStation("300")).map(Station::creator).contains(true);
    }

    @Test
    void tiredNeedsNoTransformBecauseItIsNotStationFeedback() {
        press("n");
        player.finish();
        radio.onTrackFinished(player.lastId(), PlaybackResult.stopped());
        client.calls.clear();

        press("t");

        assertThat(client.calls).containsExactly("tired b");
    }

    @Test
    void createStationSearchesAndCreatesFromThePick() {
        client.searchResult = new SearchResult(List.of(new SearchResult.ArtistMatch("Miles Davis", "R123")), List.of());
        prompter.answer("miles", "0");

        press("c");

        assertThat(client.calls).containsExactly("search miles", "create token R123");
        assertThat(state.stations()).contains(client.created);
        assertThat(state.station()).as("pianobar does not switch to the new station either").contains(EVANS);
        assertThat(console.output()).endsWith("(i) Creating station... Ok.\n");
    }

    @Test
    void createStationFromThePlayingSongOrArtist() {
        prompter.answer("s");
        press("v");
        prompter.answer("a");
        press("v");
        prompter.answer("");
        press("v");

        assertThat(client.calls).containsExactly("create song a", "create artist a");
        assertThat(console.output()).startsWith("[?] Create station from [s]ong or [a]rtist? (i) Creating station... Ok.\n");
    }

    @Test
    void genreStationsAreFetchedOnceThenPickedByCategoryAndGenre() {
        client.genres = List.of(
                new GenreCategory("Jazz", List.of(new GenreCategory.Genre("Bebop", "G100"),
                        new GenreCategory.Genre("Cool Jazz", "G101"))),
                new GenreCategory("Classical", List.of(new GenreCategory.Genre("Baroque", "G200"))));
        prompter.answer("0", "1");
        press("g");
        prompter.answer("1", "");
        press("g");

        assertThat(client.calls).containsExactly("genres", "create token G101");
        assertThat(console.output())
                .contains("(i) Receiving genre stations... Ok.\n\t 0) Jazz\n\t 1) Classical\n[?] Select category: ")
                .contains("\t 0) Bebop\n\t 1) Cool Jazz\n[?] Select genre: ")
                .contains("(i) Adding genre station \"Cool Jazz\"... Ok.\n");
    }

    @Test
    void sharedStationsAreAddedByTheirNumericId() {
        prompter.answer("1234567890");
        press("j");
        prompter.answer("sh123");
        press("j");

        assertThat(client.calls).containsExactly("create token 1234567890");
        assertThat(console.output()).startsWith("[?] Station id: (i) Adding shared station... Ok.\n");
    }

    @Test
    void addMusicSearchesThenSeedsThePlayingStation() {
        client.searchResult = new SearchResult(List.of(),
                List.of(new SearchResult.SongMatch("So What", "Miles Davis", "S789")));
        prompter.answer("so what", "0");

        press("a");

        assertThat(client.calls).containsExactly("search so what", "addMusic Bill Evans Radio S789");
    }

    @Test
    void renameAsksForTheNewNameAndAnEmptyAnswerCancels() {
        prompter.answer("Late Night");
        press("r");
        prompter.answer("");
        press("r");

        assertThat(client.calls).containsExactly("rename Bill Evans Radio -> Late Night");
        assertThat(state.station()).map(Station::name).contains("Late Night");
    }

    @Test
    void deleteWantsAnExplicitYes() {
        prompter.answer("");
        press("d");
        prompter.answer("n");
        press("d");
        assertThat(client.calls).isEmpty();
        assertThat(console.output()).startsWith("[?] Really delete \"Bill Evans Radio\"? [yN] ");

        prompter.answer("y");
        press("d");

        assertThat(client.calls).containsExactly("delete Bill Evans Radio");
        assertThat(state.station()).isEmpty();
        assertThat(player.stops()).isEqualTo(1);
    }

    @Test
    void quickMixCanOnlyBeEditedWhileQuickMixIsPlaying() {
        press("x");

        assertThat(console.output()).isEqualTo("/!\\ Please select a QuickMix station first.\n");
        assertThat(client.calls).isEmpty();
    }

    @Test
    void quickMixTogglesStationsAndSavesOnEnter() {
        client.playlists.add(List.of(song("q", "200")));
        radio.tune(QUICKMIX);
        client.calls.clear();
        // sorted menu: 0 Bill Evans (member), 1 Hard Bop (not), 2 QuickMix
        prompter.answer("1", "0", "");

        press("x");

        assertThat(client.calls).containsExactly("quickmix [Hard Bop Radio]");
        assertThat(state.findStation("200")).map(Station::inQuickMix).contains(false);
        assertThat(state.findStation("300")).map(Station::inQuickMix).contains(true);
    }

    @Test
    void quickMixShortcutsSelectAllNoneOrInvert() {
        client.playlists.add(List.of(song("q", "200")));
        radio.tune(QUICKMIX);

        client.calls.clear();
        prompter.answer("a", "");
        press("x");
        assertThat(client.calls).containsExactly("quickmix [Bill Evans Radio, Hard Bop Radio, QuickMix]");

        client.calls.clear();
        prompter.answer("n", "");
        press("x");
        assertThat(client.calls).containsExactly("quickmix []");

        client.calls.clear();
        prompter.answer("t", "");
        press("x");
        assertThat(client.calls).containsExactly("quickmix [Bill Evans Radio, Hard Bop Radio, QuickMix]");
    }

    @Test
    void bookmarkAsksWhichAndCanBeCancelled() {
        prompter.answer("s");
        press("b");
        prompter.answer("a");
        press("b");
        prompter.answer("");
        press("b");

        assertThat(client.calls).containsExactly("bookmark song a", "bookmark artist a");
        assertThat(console.output()).startsWith("[?] Bookmark [s]ong or [a]rtist? (i) Bookmarking song... Ok.\n");
    }

    @Test
    void ratingsAreReportedWithTheNewRating() {
        press("+");

        assertThat(events.types()).containsExactly(EventType.SONG_LOVE);
        assertThat(events.last().song().rating()).isEqualTo(Rating.LOVE);
        assertThat(events.last().result().isOk()).isTrue();
    }

    @Test
    void explainBookmarksAndGenreFetchAreReported() {
        press("e");
        prompter.answer("s");
        press("b");
        prompter.answer("a");
        press("b");
        prompter.answer("");
        press("g");

        assertThat(events.types()).containsExactly(EventType.SONG_EXPLAIN, EventType.SONG_BOOKMARK,
                EventType.ARTIST_BOOKMARK, EventType.STATION_FETCH_GENRE);
    }

    @Test
    void historyIsEmptyUntilASongHasEnded() {
        press("h");

        assertThat(console.output()).isEqualTo("(i) No history yet.\n");
    }

    @Test
    void historyCanBeSwitchedOff() {
        PlaybackState noHistory = new PlaybackState(0);
        new HistoryAction(noHistory, listPicker, prompter, console, renderer).execute(ActionId.HISTORY, null);

        assertThat(console.output()).isEqualTo("(i) History disabled.\n");
    }

    @Test
    void aPastSongCanBeLovedAfterAllWithoutTouchingWhatIsPlaying() {
        songEnds(); // a is history, b is playing
        prompter.answer("0", "+");

        press("h");

        assertThat(console.output()).isEqualTo("""
                \t 0) Artist of a - a
                [?] Select song: [?] What to do with this song? (i) Loving song... Ok.
                """);
        assertThat(client.calls).containsExactly("love a");
        assertThat(state.history()).extracting(Song::rating).containsExactly(Rating.LOVE);
        assertThat(state.song()).map(Song::rating).contains(Rating.NONE);
        assertThat(events.last().song().title()).isEqualTo("a");
        assertThat(events.last().song().rating()).isEqualTo(Rating.LOVE);
    }

    @Test
    void banningAPastSongDoesNotSkipTheCurrentOne() {
        songEnds();
        prompter.answer("0", "-");

        press("h");

        assertThat(client.calls).containsExactly("ban a");
        assertThat(player.stops()).isZero();
    }

    @Test
    void banningTheCurrentSongStillSkips() {
        songEnds();

        press("-");

        assertThat(client.calls).containsExactly("transform Hard Bop Radio", "ban b");
        assertThat(player.stops()).isEqualTo(1);
    }

    @Test
    void actionsOnAPastSongUseItsOwnStation() {
        songEnds(); // history: a from Bill Evans Radio; playing: b from Hard Bop Radio
        prompter.answer("0", "i");

        press("h");

        assertThat(console.output()).endsWith("""
                |>  Station "Bill Evans Radio" (200)
                |>  "a" by "Artist of a" on "Album of a"
                """);
    }

    @Test
    void afterHelpTheQuestionIsAskedAgain() {
        songEnds();
        prompter.answer("0", "?", "v", "s");

        press("h");

        assertThat(console.output()).contains("quit\n").contains("[?] Create station from [s]ong or [a]rtist? ");
        assertThat(client.calls).containsExactly("create song a");
    }

    @Test
    void anUnboundKeyOrEnterLeavesTheHistoryMenu() {
        songEnds();
        prompter.answer("0", "#");
        press("h");
        prompter.answer("0", "");
        press("h");
        prompter.answer("");
        press("h");

        assertThat(client.calls).isEmpty();
    }

    @Test
    void aPastSongWhoseStationWasDeletedCannotBeActedOn() {
        songEnds();
        state.removeStation(EVANS);
        prompter.answer("0");

        press("h");

        assertThat(console.output()).endsWith("/!\\ Station does not exist any more.\n");
        assertThat(prompter.prompts()).as("the 'what to do' question is never asked").isEqualTo(1);
        assertThat(client.calls).isEmpty();
    }

    @Test
    void theHistoryListCanBeFilteredLikeEveryOtherList() {
        songEnds();
        songEnds(); // history: b, a; the queue ran dry, nothing is playing
        prompter.answer("artist of a", "1", "e");

        press("h");

        assertThat(client.calls).containsExactly("explain a");
    }

    private static final StationInfo FULL_INFO = new StationInfo(
            List.of(new StationInfo.ArtistSeed("Bill Evans", "A1")),
            List.of(new StationInfo.SongSeed("So What", "Miles Davis", "S1")),
            List.of(new StationInfo.Feedback("Peace Piece", "Bill Evans", "F1", true),
                    new StationInfo.Feedback("Birdland", "Weather Report", "F2", false)));

    @Test
    void manageStationOffersOnlyWhatTheStationHas() {
        assertThat(ManageStationAction.Menu.of(FULL_INFO, EVANS))
                .isEqualTo(new ManageStationAction.Menu(
                        "Delete [a]rtist/[s]ong seeds or [f]eedback? Manage [m]ode? ", "asfm"));
        assertThat(ManageStationAction.Menu.of(new StationInfo(List.of(), FULL_INFO.songSeeds(), List.of()), EVANS)
                .question()).isEqualTo("Delete [s]ong seeds? Manage [m]ode? ");
        assertThat(ManageStationAction.Menu.of(new StationInfo(List.of(), List.of(), FULL_INFO.feedback()), QUICKMIX))
                .isEqualTo(new ManageStationAction.Menu("Delete [f]eedback? ", "f"));
        assertThat(ManageStationAction.Menu.of(new StationInfo(List.of(), List.of(), List.of()), EVANS).question())
                .isEqualTo("Manage [m]ode? ");
    }

    @Test
    void aQuickMixWithNothingToDeleteHasNoActions() {
        client.playlists.add(List.of(song("q", "200")));
        radio.tune(QUICKMIX);
        console.clear();

        press("=");

        assertThat(console.output()).isEqualTo("(i) Fetching station info... Ok.\n(i) No actions available.\n");
    }

    @Test
    void seedsCanBeDeleted() {
        client.stationInfo = FULL_INFO;
        prompter.answer("a", "0");
        press("=");
        prompter.answer("s", "0");
        press("=");

        assertThat(client.calls).containsExactly("info Bill Evans Radio", "deleteSeed A1",
                "info Bill Evans Radio", "deleteSeed S1");
        assertThat(console.output()).contains("\t 0) Bill Evans\n[?] Select artist: (i) Deleting artist seed... Ok.\n")
                .contains("\t 0) Miles Davis - So What\n[?] Select song: (i) Deleting song seed... Ok.\n");
        assertThat(events.types()).containsExactly(EventType.STATION_FETCH_INFO,
                EventType.STATION_DELETE_ARTIST_SEED, EventType.STATION_FETCH_INFO, EventType.STATION_DELETE_SONG_SEED);
    }

    @Test
    void feedbackIsListedWithItsThumbAndCanBeTakenBack() {
        client.stationInfo = FULL_INFO;
        prompter.answer("f", "1");

        press("=");

        assertThat(console.output()).contains(
                "\t 0) Bill Evans - Peace Piece <3\n\t 1) Weather Report - Birdland </3\n[?] Select song: ");
        assertThat(client.calls).containsExactly("info Bill Evans Radio", "deleteFeedback F2");
    }

    @Test
    void backingOutOfManageStationDeletesNothing() {
        client.stationInfo = FULL_INFO;
        prompter.answer("");
        press("=");
        prompter.answer("a", "");
        press("=");
        prompter.answer("x");
        press("=");

        assertThat(client.calls).containsOnly("info Bill Evans Radio");
    }

    @Test
    void aNewModeIsPickedByPositionSentByIdAndHeardImmediately() {
        client.modes = List.of(new StationMode(0, "My Station", "As you know it", false),
                new StationMode(7, "Deep Cuts", "Less familiar songs", true));
        prompter.answer("m", "9", "1");

        press("=");

        assertThat(console.output()).contains("""
                \t 0) My Station: As you know it
                \t 1) Deep Cuts: Less familiar songs (active)
                [?] Pick a new mode: (i) Selecting mode "Deep Cuts"... Ok.
                """);
        assertThat(client.calls).containsExactly("info Bill Evans Radio", "modes Bill Evans Radio",
                "setMode Bill Evans Radio 7");
        assertThat(player.stops()).as("the queue was chosen under the old mode").isEqualTo(1);
        assertThat(state.upcoming()).isEmpty();
    }

    @Test
    void leavingTheModeMenuChangesNothing() {
        client.modes = List.of(new StationMode(0, "My Station", "", true));
        prompter.answer("m", "");

        press("=");

        assertThat(client.calls).doesNotContain("setMode Bill Evans Radio 0");
        assertThat(player.stops()).isZero();
    }

    @Test
    void settingsAreShownWithThePasswordMasked() {
        client.settings = new AccountSettings("me@example.com", true);
        prompter.answer("");

        press("!");

        assertThat(console.output()).isEqualTo("""
                (i) Retrieving settings... Ok.
                \t 0) Username (me@example.com)
                \t 1) Password (*****)
                \t 2) Explicit content filter (yes)
                [?] Change setting:\s""");
        assertThat(client.calls).containsExactly("settings");
    }

    @Test
    void severalSettingsAreCollectedAndSentAsOneChange() {
        prompter.answer("2", "y", "0", "new@example.com", "");

        press("!");

        assertThat(client.calls).containsExactly("settings",
                "changeAccount user=new@example.com password=null filter=true");
        assertThat(console.output()).endsWith(
                "(i) Changing settings... Ok.\n(i) Remember to update your config file, or the next start cannot log in.\n");
        assertThat(events.types()).containsExactly(EventType.SETTINGS_GET, EventType.SETTINGS_CHANGE);
    }

    @Test
    void aNewPasswordIsAskedForWithoutEcho() {
        prompter.answer("1", "n3w-s3cret", "");

        press("!");

        assertThat(prompter.secretsAsked()).isEqualTo(1);
        assertThat(client.calls).contains("changeAccount user=null password=n3w-s3cret filter=null");
        assertThat(console.output()).doesNotContain("n3w-s3cret");
    }

    @Test
    void theFilterQuestionDefaultsToTheCurrentValueAndNeedsNoReminder() {
        client.settings = new AccountSettings("me@example.com", true);
        prompter.answer("2", "", "");

        press("!");

        assertThat(client.calls).contains("changeAccount user=null password=null filter=true");
        assertThat(console.output()).doesNotContain("Remember to update");
    }

    @Test
    void unknownNumbersAndEmptyAnswersChangeNothing() {
        prompter.answer("7", "0", "", "1", "", "");

        press("!");

        assertThat(client.calls).containsExactly("settings");
    }

    @Test
    void unboundKeysDoNothing() {
        press("#yzQ");

        assertThat(console.output()).isEmpty();
        assertThat(client.calls).isEmpty();
    }

    @Test
    void songActionsAreIgnoredWhenNothingIsPlaying() {
        state.clearStation();
        state.finishSong();

        press("+n");

        assertThat(client.calls).isEmpty();
        assertThat(console.output()).isEqualTo("/!\\ No song playing.\n/!\\ No station selected.\n");
        assertThat(player.stops()).isZero();

        console.clear();
        press("-tei pSadrxbv=");
        assertThat(client.calls).isEmpty();
        assertThat(console.output()).doesNotContain("(i)").contains("No song playing.").contains("No station selected.");
    }

    @Test
    void stationIndependentActionsStillWorkWhenNothingIsPlaying() {
        state.clearStation();
        state.finishSong();

        press("(?");

        assertThat(player.volume()).isEqualTo(-1);
        assertThat(console.output()).contains("quit");
    }
}

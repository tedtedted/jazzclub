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
import com.tedredington.jazzclub.app.PlaybackState;
import com.tedredington.jazzclub.app.Radio;
import com.tedredington.jazzclub.app.StationPicker;
import com.tedredington.jazzclub.app.StationService;
import com.tedredington.jazzclub.pandora.error.PandoraApiException;
import com.tedredington.jazzclub.pandora.model.AudioQuality;
import com.tedredington.jazzclub.pandora.model.GenreCategory;
import com.tedredington.jazzclub.pandora.model.SearchResult;
import com.tedredington.jazzclub.pandora.model.Station;
import com.tedredington.jazzclub.pandora.model.Rating;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.player.PlaybackResult;
import com.tedredington.jazzclub.testsupport.FakeAudioPlayer;
import com.tedredington.jazzclub.testsupport.RecordingConsole;
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
    private final Radio radio = new Radio(client, player, state, console, renderer, AudioQuality.HIGH, 3);
    private final StationService stationService = new StationService(client, state, radio, console);
    private final StationPicker stationPicker = new StationPicker(console, prompter, renderer);
    private final ListPicker listPicker = new ListPicker(console, prompter);
    private final MusicSearch musicSearch = new MusicSearch(client, console, prompter, listPicker);
    private final List<KeyAction> actions = List.of(
            new HelpAction(bindings, console),
            new RateSongAction(client, state, radio, stationService, console),
            new ExplainAction(client, state, console),
            new SongInfoAction(state, console, renderer),
            new TransportAction(player, radio, state),
            new ChangeStationAction(stationPicker, state, radio),
            new CreateStationAction(client, state, stationService, musicSearch, listPicker, prompter, console),
            new EditStationAction(state, stationService, musicSearch, stationPicker, prompter, console),
            new BookmarkAction(client, state, prompter, console));
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
        new HelpAction(custom, console).execute(ActionId.HELP);

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
                "", "", "%t%@%s", "", "", "", "", " @ "));
        console.clear();
        new SongInfoAction(state, console, withStation).execute(ActionId.UPCOMING);
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
    void quitStopsThePlayerAndEndsTheLoop() {
        press("q");

        assertThat(state.quitRequested()).isTrue();
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
    void unboundKeysDoNothing() {
        press("#yzQ");

        assertThat(console.output()).isEmpty();
        assertThat(client.calls).isEmpty();
    }

    @Test
    void songActionsAreIgnoredWhenNothingIsPlaying() {
        state.clearStation();
        state.finishSong();

        press("+-tein pSadrxbv");

        assertThat(client.calls).isEmpty();
        assertThat(console.output()).isEmpty();
        assertThat(player.stops()).isZero();
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

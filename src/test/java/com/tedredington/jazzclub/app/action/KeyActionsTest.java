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
import com.tedredington.jazzclub.app.PlaybackState;
import com.tedredington.jazzclub.app.Radio;
import com.tedredington.jazzclub.app.StationPicker;
import com.tedredington.jazzclub.pandora.error.PandoraApiException;
import com.tedredington.jazzclub.pandora.model.AudioQuality;
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
    private final List<KeyAction> actions = List.of(
            new HelpAction(bindings, console),
            new RateSongAction(client, state, radio, console),
            new ExplainAction(client, state, console),
            new SongInfoAction(state, console, renderer),
            new TransportAction(player, radio, state),
            new ChangeStationAction(new StationPicker(console, prompter, renderer), state, radio));
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

        assertThat(console.output()).startsWith("\r\tl    love song\n\te    explain why this song is played\n")
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
    void unboundKeysDoNothing() {
        press("#xyz");

        assertThat(console.output()).isEmpty();
        assertThat(client.calls).isEmpty();
    }

    @Test
    void songActionsAreIgnoredWhenNothingIsPlaying() {
        state.clearStation();
        state.finishSong();

        press("+-tein pS");

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

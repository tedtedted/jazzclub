package com.tedredington.jazzclub.app;

import static com.tedredington.jazzclub.testsupport.TestData.EVANS;
import static com.tedredington.jazzclub.testsupport.TestData.FORMAT;
import static com.tedredington.jazzclub.testsupport.TestData.HARD_BOP;
import static com.tedredington.jazzclub.testsupport.TestData.song;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.tedredington.jazzclub.credentials.CredentialsException;
import com.tedredington.jazzclub.credentials.CredentialsProvider;
import com.tedredington.jazzclub.event.Event;
import com.tedredington.jazzclub.event.EventQueue;
import com.tedredington.jazzclub.pandora.UserCredentials;
import com.tedredington.jazzclub.pandora.error.InvalidLoginException;
import com.tedredington.jazzclub.pandora.model.AudioQuality;
import com.tedredington.jazzclub.player.PlaybackResult;
import com.tedredington.jazzclub.testsupport.FakeAudioPlayer;
import com.tedredington.jazzclub.app.event.EventType;
import com.tedredington.jazzclub.app.event.PlayerEvents;
import com.tedredington.jazzclub.testsupport.RecordingConsole;
import com.tedredington.jazzclub.testsupport.RecordingEvents;
import com.tedredington.jazzclub.testsupport.ScriptedPrompter;
import com.tedredington.jazzclub.testsupport.StubPandoraClient;
import com.tedredington.jazzclub.ui.Renderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class PlayerLoopTest {

    private final StubPandoraClient client = new StubPandoraClient();
    private final FakeAudioPlayer player = new FakeAudioPlayer();
    private final PlaybackState state = new PlaybackState(5);
    private final RecordingConsole console = new RecordingConsole();
    private final ScriptedPrompter prompter = new ScriptedPrompter();
    private final EventQueue events = new EventQueue();
    private final Renderer renderer = new Renderer(FORMAT);
    private final RecordingEvents playerEvents = new RecordingEvents();
    private final PlayerEvents emitter = playerEvents.on(state, player);
    private final Radio radio = new Radio(client, player, state, console, renderer, AudioQuality.HIGH, 3, emitter);
    private final KeyBindings bindings = new KeyBindings(Map.of());
    private CredentialsProvider credentials = () -> new UserCredentials("me@example.com", "pw");

    /** Quit is the only action the loop needs to end; the real ones are covered in KeyActionsTest. */
    private final KeyAction quit = new KeyAction() {
        @Override
        public Set<ActionId> ids() {
            return Set.of(ActionId.values());
        }

        @Override
        public void execute(ActionId id, ActionContext context) {
            if (id == ActionId.QUIT) {
                state.requestQuit();
            }
        }
    };

    private PlayerLoop loop(String autostart) {
        ActionDispatcher dispatcher = new ActionDispatcher(bindings, state, console, List.of(quit));
        return new PlayerLoop(credentials, client, player, state, radio,
                new StationPicker(console, prompter, renderer), dispatcher, bindings, events, console, renderer,
                "1.2.3", autostart, new PandoraCalls(console, emitter));
    }

    @Test
    void signsInPicksAStationPlaysAndQuitsCleanly() {
        client.stations = List.of(EVANS, HARD_BOP);
        client.playlists.add(List.of(song("a", "200")));
        prompter.answer("0");
        events.publish(new Event.KeyPressed('q'));

        int exitCode = loop(null).run();

        assertThat(exitCode).isEqualTo(PlayerLoop.EXIT_OK);
        assertThat(client.calls).containsExactly("login me@example.com", "stations", "playlist Bill Evans Radio HIGH");
        assertThat(console.output()).startsWith("""
                Welcome to jazzclub (1.2.3)! Press ? for a list of commands.
                (i) Login... Ok.
                (i) Get stations... Ok.
                """).contains("|>  \"a\" by").endsWith("\n");
        assertThat(player.stops()).isEqualTo(1);
    }

    @Test
    void aWholeSessionIsReportedFromLoginToTheLastSong() {
        client.stations = List.of(EVANS);
        client.playlists.add(List.of(song("a", "200")));
        prompter.answer("0");
        events.publish(new Event.KeyPressed('q'));

        loop(null).run();

        assertThat(playerEvents.types()).containsExactly(EventType.USER_LOGIN, EventType.USER_GET_STATIONS,
                EventType.STATION_FETCH_PLAYLIST, EventType.SONG_START, EventType.SONG_FINISH);
    }

    @Test
    void quittingReportsHowMuchOfTheLastSongWasHeard() {
        client.stations = List.of(EVANS);
        client.playlists.add(List.of(song("a", "200")));
        prompter.answer("0");
        player.elapsed(Duration.ofSeconds(42));
        events.publish(new Event.KeyPressed('q'));

        loop(null).run();

        assertThat(playerEvents.last().type()).isEqualTo(EventType.SONG_FINISH);
        assertThat(playerEvents.last().played()).as("a scrobbler decides on this").hasSeconds(42);
    }

    @Test
    void aFailedLoginIsReportedToo() {
        client.failure = new InvalidLoginException();

        loop(null).run();

        assertThat(playerEvents.types()).containsExactly(EventType.USER_LOGIN);
        assertThat(playerEvents.last().result().isOk()).isFalse();
    }

    @Test
    void autostartStationSkipsTheMenu() {
        client.stations = List.of(EVANS, HARD_BOP);
        client.playlists.add(List.of(song("a", "300")));
        events.publish(new Event.KeyPressed('q'));

        loop(" 300 ").run();

        assertThat(prompter.prompts()).isZero();
        assertThat(state.station()).contains(HARD_BOP);
    }

    @Test
    void anUnknownAutostartStationFallsBackToTheMenu() {
        client.stations = List.of(EVANS);
        prompter.answer("");
        events.publish(new Event.KeyPressed('q'));

        loop("nope").run();

        assertThat(console.output()).contains("/!\\ Error: Autostart station not found.\n");
        assertThat(prompter.prompts()).isEqualTo(1);
    }

    @Test
    void backingOutOfTheFirstMenuLeavesAnIdleButUsablePlayer() {
        client.stations = List.of(EVANS);
        prompter.answer("");
        events.publish(new Event.KeyPressed('q'));

        assertThat(loop(null).run()).isEqualTo(PlayerLoop.EXIT_OK);
        assertThat(player.played()).isEmpty();
    }

    @Test
    void wrongPasswordIsAOneLineErrorAndANonZeroExit() {
        client.failure = new InvalidLoginException();

        int exitCode = loop(null).run();

        assertThat(exitCode).isEqualTo(PlayerLoop.EXIT_FAILURE);
        assertThat(console.output()).endsWith("(i) Login... Error: Wrong email address or password.\n");
        assertThat(client.calls).containsExactly("login me@example.com");
    }

    @Test
    void missingCredentialsAreExplainedBeforeAnyNetworkCall() {
        credentials = () -> {
            throw new CredentialsException("No Pandora account configured. Add a line 'user = ...'");
        };

        assertThat(loop(null).run()).isEqualTo(PlayerLoop.EXIT_FAILURE);
        assertThat(console.output()).endsWith("/!\\ No Pandora account configured. Add a line 'user = ...'\n");
        assertThat(client.calls).isEmpty();
    }

    @Test
    void ticksRefreshTheCountdownWhilePlaying() {
        client.stations = List.of(EVANS);
        client.playlists.add(List.of(song("a", "200")));
        prompter.answer("0");
        player.elapsed(Duration.ofSeconds(5));
        events.publish(new Event.Tick());
        events.publish(new Event.KeyPressed('q'));

        loop(null).run();

        assertThat(console.output()).contains("#   -03:00/03:05\r");
    }

    @Test
    void noCountdownWhileIdle() {
        client.stations = List.of(EVANS);
        prompter.answer("");
        events.publish(new Event.Tick());
        events.publish(new Event.KeyPressed('q'));

        loop(null).run();

        assertThat(console.output()).doesNotContain("#   ");
    }

    @Test
    void aFinishedTrackStartsTheNextOne() {
        client.stations = List.of(EVANS);
        client.playlists.add(List.of(song("a", "200"), song("b", "200")));
        prompter.answer("0");
        events.publish(new Event.TrackFinished(1, PlaybackResult.completed()));
        events.publish(new Event.KeyPressed('q'));

        loop(null).run();

        assertThat(player.played()).hasSize(2);
    }

    @Test
    void endOfInputQuits() {
        client.stations = List.of(EVANS);
        prompter.answer("");
        events.publish(new Event.InputClosed());

        assertThat(loop(null).run()).isEqualTo(PlayerLoop.EXIT_OK);
    }

    @Test
    void interruptionQuits() {
        client.stations = List.of(EVANS);
        prompter.answer("");
        Thread.currentThread().interrupt();

        assertThat(loop(null).run()).isEqualTo(PlayerLoop.EXIT_OK);
        assertThat(Thread.interrupted()).isTrue();
    }
}

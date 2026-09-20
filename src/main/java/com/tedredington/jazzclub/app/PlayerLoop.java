package com.tedredington.jazzclub.app;

import java.util.Optional;

import com.tedredington.jazzclub.app.event.EventType;
import com.tedredington.jazzclub.credentials.CredentialsException;
import com.tedredington.jazzclub.credentials.CredentialsProvider;
import com.tedredington.jazzclub.event.Event;
import com.tedredington.jazzclub.event.EventQueue;
import com.tedredington.jazzclub.pandora.PandoraClient;
import com.tedredington.jazzclub.pandora.error.PandoraException;
import com.tedredington.jazzclub.pandora.model.Station;
import com.tedredington.jazzclub.player.AudioPlayer;
import com.tedredington.jazzclub.ui.Console;
import com.tedredington.jazzclub.ui.MessageType;
import com.tedredington.jazzclub.ui.Renderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** jazzclub's main loop: log in, pick a station, then react to events until the user quits. */
public final class PlayerLoop {

    public static final int EXIT_OK = 0;
    public static final int EXIT_FAILURE = 1;

    private static final Logger log = LoggerFactory.getLogger(PlayerLoop.class);

    private final CredentialsProvider credentials;
    private final PandoraClient client;
    private final AudioPlayer player;
    private final PlaybackState state;
    private final Radio radio;
    private final StationPicker stationPicker;
    private final ActionDispatcher dispatcher;
    private final KeyBindings bindings;
    private final EventQueue events;
    private final Console console;
    private final Renderer renderer;
    private final String version;
    private final String autostartStation;
    private final PandoraCalls calls;

    public PlayerLoop(CredentialsProvider credentials, PandoraClient client, AudioPlayer player, PlaybackState state,
                      Radio radio, StationPicker stationPicker, ActionDispatcher dispatcher, KeyBindings bindings,
                      EventQueue events, Console console, Renderer renderer, String version,
                      String autostartStation, PandoraCalls calls) {
        this.calls = calls;
        this.credentials = credentials;
        this.client = client;
        this.player = player;
        this.state = state;
        this.radio = radio;
        this.stationPicker = stationPicker;
        this.dispatcher = dispatcher;
        this.bindings = bindings;
        this.events = events;
        this.console = console;
        this.renderer = renderer;
        this.version = version;
        this.autostartStation = autostartStation;
    }

    /** @return the process exit code */
    public int run() {
        console.append("Welcome to jazzclub (" + version + ")! "
                + bindings.keyFor(ActionId.HELP).map(k -> "Press " + k + " for a list of commands.").orElse("")
                + "\n");
        if (!signIn()) {
            return EXIT_FAILURE;
        }
        initialStation().ifPresent(radio::tune);

        try {
            while (!state.quitRequested()) {
                handle(events.take());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            radio.shutdown();
            console.append("\n");
        }
        return EXIT_OK;
    }

    private boolean signIn() {
        try {
            var login = credentials.credentials();
            calls.run("Login... ", EventType.USER_LOGIN, Selection.NONE, () -> client.login(login));
            calls.run("Get stations... ", EventType.USER_GET_STATIONS, Selection.NONE,
                    () -> state.stations(client.stations()));
            return true;
        } catch (CredentialsException e) {
            log.debug("No usable credentials", e);
            console.error(e.getMessage() + "\n");
        } catch (PandoraException e) {
            log.debug("Sign-in failed", e);
            console.append("Error: " + e.getMessage() + "\n");
        }
        return false;
    }

    private Optional<Station> initialStation() {
        if (autostartStation != null && !autostartStation.isBlank()) {
            Optional<Station> autostart = state.findStation(autostartStation.strip());
            if (autostart.isPresent()) {
                return autostart;
            }
            console.error("Error: Autostart station not found.\n");
        }
        return stationPicker.pick(state.stations(), "Select station: ");
    }

    private void handle(Event event) {
        switch (event) {
            case Event.KeyPressed(char key) -> dispatcher.dispatch(key);
            case Event.TrackFinished(long id, var result) -> radio.onTrackFinished(id, result);
            case Event.Tick tick -> printTime();
            case Event.InputClosed closed -> state.requestQuit();
        }
    }

    private void printTime() {
        if (player.isActive() && !player.isPaused()) {
            state.song().ifPresent(song ->
                    console.print(MessageType.TIME, renderer.time(player.elapsed(), song.length()) + "\r"));
        }
    }
}

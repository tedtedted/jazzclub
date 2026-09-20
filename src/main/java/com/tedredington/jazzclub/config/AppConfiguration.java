package com.tedredington.jazzclub.config;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.tedredington.jazzclub.app.ActionDispatcher;
import com.tedredington.jazzclub.app.KeyAction;
import com.tedredington.jazzclub.app.KeyBindings;
import com.tedredington.jazzclub.app.ListPicker;
import com.tedredington.jazzclub.app.MusicSearch;
import com.tedredington.jazzclub.app.PandoraCalls;
import com.tedredington.jazzclub.app.PlaybackState;
import com.tedredington.jazzclub.app.PlayerLoop;
import com.tedredington.jazzclub.app.Radio;
import com.tedredington.jazzclub.app.SessionStore;
import com.tedredington.jazzclub.app.StationPicker;
import com.tedredington.jazzclub.app.StationService;
import com.tedredington.jazzclub.app.event.PlayerEvents;
import com.tedredington.jazzclub.cli.VersionProvider;
import com.tedredington.jazzclub.config.file.StateFile;
import com.tedredington.jazzclub.config.file.XdgDirectories;
import com.tedredington.jazzclub.credentials.CredentialsProvider;
import com.tedredington.jazzclub.event.Event;
import com.tedredington.jazzclub.event.EventQueue;
import com.tedredington.jazzclub.pandora.PandoraClient;
import com.tedredington.jazzclub.player.AudioPlayer;
import com.tedredington.jazzclub.player.StreamingAudioPlayer;
import com.tedredington.jazzclub.player.ffmpeg.FfmpegDecoder;
import com.tedredington.jazzclub.player.javasound.JavaSoundAudioSink;
import com.tedredington.jazzclub.player.javasound.JavaSoundNativeSupport;
import com.tedredington.jazzclub.remote.ControlFifo;
import com.tedredington.jazzclub.terminal.TerminalSession;
import com.tedredington.jazzclub.ui.AnsiConsole;
import com.tedredington.jazzclub.ui.Console;
import com.tedredington.jazzclub.ui.EventQueuePrompter;
import com.tedredington.jazzclub.ui.Prompter;
import com.tedredington.jazzclub.ui.Renderer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/** Wires the player. The classes themselves know nothing about Spring, except the key actions. */
@Configuration(proxyBeanMethods = false)
class AppConfiguration {

    @Bean
    EventQueue eventQueue() {
        return new EventQueue();
    }

    @Bean
    Console console(JazzclubProperties properties) {
        return new AnsiConsole(System.out, properties.format().msg());
    }

    @Bean
    SessionStore sessionStore(JazzclubProperties properties) {
        return new StateFile(properties.stateFile() != null
                ? properties.stateFile()
                : XdgDirectories.system().stateFile());
    }

    @Bean
    Prompter prompter(EventQueue events, Console console) {
        return new EventQueuePrompter(events, console);
    }

    @Bean
    Renderer renderer(JazzclubProperties properties) {
        return new Renderer(properties.format());
    }

    @Bean
    KeyBindings keyBindings(JazzclubProperties properties) {
        return new KeyBindings(properties.keys());
    }

    @Bean
    PlaybackState playbackState(JazzclubProperties properties) {
        return new PlaybackState(properties.history());
    }

    /** Lazy: nothing touches the sound system until the first song. */
    @Bean
    @Lazy
    AudioPlayer audioPlayer(JazzclubProperties properties, EventQueue events) {
        JavaSoundNativeSupport.prepare(XdgDirectories.system().cacheDirectory().resolve("lib"));
        return new StreamingAudioPlayer(
                FfmpegDecoder.factory(properties.ffmpeg()),
                JavaSoundAudioSink::new,
                (id, result) -> events.publish(new Event.TrackFinished(id, result)),
                properties.volume(),
                properties.gainMul());
    }

    /** Player events travel over Spring's event bus; see EventCommandListener for a subscriber. */
    @Bean
    @Lazy
    PlayerEvents playerEvents(PlaybackState state, AudioPlayer player, ApplicationEventPublisher publisher,
                              JazzclubProperties properties) {
        return new PlayerEvents(state, player, publisher::publishEvent, properties.sort().comparator());
    }

    @Bean
    @Lazy
    PandoraCalls pandoraCalls(Console console, PlayerEvents events) {
        return new PandoraCalls(console, events);
    }

    @Bean
    @Lazy
    Radio radio(PandoraClient client, AudioPlayer player, PlaybackState state, Console console, Renderer renderer,
                JazzclubProperties properties, PlayerEvents events) {
        return new Radio(client, player, state, console, renderer, properties.audioQuality(), properties.maxRetry(),
                events);
    }

    @Bean
    ListPicker listPicker(Console console, Prompter prompter) {
        return new ListPicker(console, prompter);
    }

    @Bean
    @Lazy
    MusicSearch musicSearch(PandoraClient client, Console console, Prompter prompter, ListPicker picker) {
        return new MusicSearch(client, console, prompter, picker);
    }

    @Bean
    @Lazy
    StationService stationService(PandoraClient client, PandoraCalls calls, PlaybackState state, Radio radio) {
        return new StationService(client, calls, state, radio);
    }

    @Bean
    StationPicker stationPicker(Console console, Prompter prompter, Renderer renderer,
                                JazzclubProperties properties) {
        return new StationPicker(console, prompter, renderer, properties.sort().comparator(),
                properties.autoselect());
    }

    @Bean
    @Lazy
    ActionDispatcher actionDispatcher(KeyBindings bindings, PlaybackState state, Console console,
                                      List<KeyAction> actions) {
        return new ActionDispatcher(bindings, state, console, actions);
    }

    @Bean
    @Lazy
    PlayerLoop playerLoop(CredentialsProvider credentials, PandoraClient client, AudioPlayer player,
                          PlaybackState state, Radio radio, StationPicker stationPicker,
                          ActionDispatcher dispatcher, KeyBindings bindings, EventQueue events, Console console,
                          Renderer renderer, JazzclubProperties properties, PandoraCalls calls,
                          SessionStore sessionStore) {
        return new PlayerLoop(credentials, client, player, state, radio, stationPicker, dispatcher, bindings, events,
                console, renderer, VersionProvider.version(), properties.autostartStation(), calls, sessionStore);
    }

    @Bean(destroyMethod = "close")
    @Lazy
    TerminalSession terminalSession(EventQueue events) {
        return new TerminalSession(events);
    }

    @Bean(destroyMethod = "close")
    @Lazy
    ControlFifo controlFifo(JazzclubProperties properties, EventQueue events, Console console) {
        Path fifo = properties.fifo() != null
                ? properties.fifo()
                : XdgDirectories.system().configDirectory().resolve("ctl");
        return new ControlFifo(fifo, events, console);
    }

    /** Drives the once-a-second time display. */
    @Bean(destroyMethod = "shutdownNow")
    @Lazy
    ScheduledExecutorService ticker(EventQueue events) {
        ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(
                runnable -> Thread.ofPlatform().name("ticker").daemon(true).unstarted(runnable));
        ticker.scheduleAtFixedRate(() -> events.publish(new Event.Tick()), 1, 1, TimeUnit.SECONDS);
        return ticker;
    }
}

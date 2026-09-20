package com.tedredington.jazzclub.app;

import static com.tedredington.jazzclub.testsupport.TestData.EVANS;
import static com.tedredington.jazzclub.testsupport.TestData.FORMAT;
import static com.tedredington.jazzclub.testsupport.TestData.HARD_BOP;
import static com.tedredington.jazzclub.testsupport.TestData.QUICKMIX;
import static com.tedredington.jazzclub.testsupport.TestData.song;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.tedredington.jazzclub.pandora.error.PandoraApiException;
import com.tedredington.jazzclub.pandora.model.AudioQuality;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.player.PlaybackResult;
import com.tedredington.jazzclub.testsupport.FakeAudioPlayer;
import com.tedredington.jazzclub.app.event.EventType;
import com.tedredington.jazzclub.testsupport.RecordingConsole;
import com.tedredington.jazzclub.testsupport.RecordingEvents;
import com.tedredington.jazzclub.testsupport.StubPandoraClient;
import com.tedredington.jazzclub.ui.Renderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RadioTest {

    private final StubPandoraClient client = new StubPandoraClient();
    private final FakeAudioPlayer player = new FakeAudioPlayer();
    private final PlaybackState state = new PlaybackState(5);
    private final RecordingConsole console = new RecordingConsole();
    private final RecordingEvents events = new RecordingEvents();
    private final Radio radio = new Radio(client, player, state, console, new Renderer(FORMAT),
            AudioQuality.MEDIUM, 3, events.on(state, player));

    private final Song a = song("a", "200");
    private final Song b = song("b", "200");

    @BeforeEach
    void stations() {
        state.stations(List.of(QUICKMIX, EVANS, HARD_BOP));
    }

    /** The player thread winding down and the main loop receiving the event. */
    private void finish(PlaybackResult result) {
        long id = player.lastId();
        player.finish();
        radio.onTrackFinished(id, result);
    }

    @Test
    void tuningInFetchesAPlaylistAndStartsTheFirstSong() {
        client.playlists.add(List.of(a, b));

        radio.tune(EVANS);

        assertThat(client.calls).containsExactly("playlist Bill Evans Radio MEDIUM");
        assertThat(player.played()).singleElement()
                .isEqualTo(new FakeAudioPlayer.Played(1, a.audioUrl(), a.gainDb()));
        assertThat(state.song()).contains(a);
        assertThat(state.upcoming()).containsExactly(b);
        assertThat(console.output()).isEqualTo("""
                |>  Station "Bill Evans Radio" (200)
                (i) Receiving new playlist... Ok.
                |>  "a" by "Artist of a" on "Album of a"
                """);
    }

    @Test
    void whenASongEndsTheNextOneStartsWithoutAskingPandoraAgain() {
        client.playlists.add(List.of(a, b));
        radio.tune(EVANS);

        finish(PlaybackResult.completed());

        assertThat(player.played()).extracting(FakeAudioPlayer.Played::url).containsExactly(a.audioUrl(), b.audioUrl());
        assertThat(client.calls).hasSize(1);
        assertThat(state.history()).containsExactly(a);
    }

    @Test
    void anEmptyQueueIsRefilled() {
        client.playlists.add(List.of(a));
        client.playlists.add(List.of(b));
        radio.tune(EVANS);

        finish(PlaybackResult.completed());

        assertThat(client.calls).hasSize(2);
        assertThat(state.song()).contains(b);
    }

    @Test
    void skipStopsThePlayerAndLetsItsEndEventStartTheNextSong() {
        client.playlists.add(List.of(a, b));
        radio.tune(EVANS);

        radio.skip();
        assertThat(player.stops()).isEqualTo(1);
        assertThat(player.played()).hasSize(1);

        finish(PlaybackResult.stopped());
        assertThat(state.song()).contains(b);
    }

    @Test
    void changingStationWhilePlayingWaitsForThePlayerThenPlaysTheNewStation() {
        client.playlists.add(List.of(a, b));
        client.playlists.add(List.of(song("c", "300")));
        radio.tune(EVANS);

        radio.tune(HARD_BOP);
        finish(PlaybackResult.stopped());

        assertThat(client.calls).containsExactly("playlist Bill Evans Radio MEDIUM", "playlist Hard Bop Radio MEDIUM");
        assertThat(state.song()).map(Song::title).contains("c");
    }

    @Test
    void quickMixNamesTheRealStation() {
        client.playlists.add(List.of(song("x", "300")));

        radio.tune(QUICKMIX);

        assertThat(console.output()).contains("\"Album of x\" @ Hard Bop Radio\n");
    }

    @Test
    void eventsOfASupersededTrackAreIgnored() {
        client.playlists.add(List.of(a, b));
        radio.tune(EVANS);

        radio.onTrackFinished(999, PlaybackResult.completed());

        assertThat(player.played()).hasSize(1);
        assertThat(state.song()).contains(a);
    }

    @Test
    void aFailedTrackIsReportedAndTheNextOneTried() {
        client.playlists.add(List.of(a, b));
        radio.tune(EVANS);
        console.clear();

        finish(PlaybackResult.failed("Decoding failed: 403"));

        assertThat(console.output()).startsWith("/!\\ Decoding failed: 403\n|>  \"b\"");
        assertThat(state.station()).contains(EVANS);
    }

    @Test
    void tooManyFailuresInARowStopTheStationInsteadOfHammeringPandora() {
        client.playlists.add(List.of(a, b, song("c", "200"), song("d", "200")));
        radio.tune(EVANS);

        finish(PlaybackResult.failed("x"));
        finish(PlaybackResult.failed("x"));
        finish(PlaybackResult.failed("x"));

        assertThat(player.played()).hasSize(3);
        assertThat(state.station()).isEmpty();
        assertThat(state.song()).isEmpty();
        assertThat(console.output()).contains("Too many playback errors");
    }

    @Test
    void aSuccessResetsTheFailureCount() {
        client.playlists.add(List.of(a, b, song("c", "200"), song("d", "200"), song("e", "200")));
        radio.tune(EVANS);

        finish(PlaybackResult.failed("x"));
        finish(PlaybackResult.failed("x"));
        finish(PlaybackResult.completed());
        finish(PlaybackResult.failed("x"));

        assertThat(state.station()).contains(EVANS);
        assertThat(player.played()).hasSize(5);
    }

    @Test
    void aPlaylistErrorIsShownAndStopsTheStation() {
        client.failure = new PandoraApiException(1006, null);

        radio.tune(EVANS);

        assertThat(console.output()).endsWith("(i) Receiving new playlist... Error: Station does not exist.\n");
        assertThat(state.station()).isEmpty();
        assertThat(player.played()).isEmpty();
    }

    @Test
    void anEmptyPlaylistMeansNoTracksLeft() {
        radio.tune(EVANS);

        assertThat(console.output()).endsWith("(i) Receiving new playlist... Ok.\n(i) No tracks left.\n");
        assertThat(state.station()).isEmpty();
    }

    @Test
    void afterQuitNothingNewIsStarted() {
        client.playlists.add(List.of(a, b));
        radio.tune(EVANS);
        state.requestQuit();

        finish(PlaybackResult.stopped());

        assertThat(player.played()).hasSize(1);
        assertThat(state.song()).isEmpty();
    }

    @Test
    void aSongsLifeIsReportedForScrobblers() {
        client.playlists.add(List.of(a, b));
        radio.tune(EVANS);

        assertThat(events.types()).containsExactly(EventType.STATION_FETCH_PLAYLIST, EventType.SONG_START);
        assertThat(events.last().song()).isEqualTo(a);
        assertThat(events.last().station()).isEqualTo(EVANS);
        assertThat(events.last().upcoming()).containsExactly(b);

        long id = player.lastId();
        player.finish();
        radio.onTrackFinished(id, PlaybackResult.completed().withPlayed(java.time.Duration.ofSeconds(180)));

        assertThat(events.types()).containsExactly(EventType.STATION_FETCH_PLAYLIST, EventType.SONG_START,
                EventType.SONG_FINISH, EventType.SONG_START);
        assertThat(events.all().get(2).song()).as("songfinish is about the song that ended").isEqualTo(a);
        assertThat(events.all().get(2).played()).hasSeconds(180);
    }

    @Test
    void aFailedPlaylistFetchIsReportedWithItsError() {
        client.failure = new PandoraApiException(1006, null);

        radio.tune(EVANS);

        assertThat(events.last().type()).isEqualTo(EventType.STATION_FETCH_PLAYLIST);
        assertThat(events.last().result().isOk()).isFalse();
        assertThat(events.last().result().pandoraCode()).isEqualTo(1006 + 1024);
    }

    @Test
    void quittingMidSongStillReportsItAsFinishedWithTheTimeHeard() {
        client.playlists.add(List.of(a, b));
        radio.tune(EVANS);
        player.elapsed(java.time.Duration.ofSeconds(42));
        events.clear();

        radio.shutdown();
        radio.shutdown();

        assertThat(events.types()).as("once, however often shutdown is called").containsExactly(EventType.SONG_FINISH);
        assertThat(events.last().played()).hasSeconds(42);
        assertThat(player.stops()).isEqualTo(2);
    }

    @Test
    void shuttingDownAnIdleRadioReportsNothing() {
        radio.shutdown();

        assertThat(events.all()).isEmpty();
    }

    @Test
    void skipWithNothingPlayingStartsPlayback() {
        state.changeStation(EVANS);
        client.playlists.add(List.of(a));

        radio.skip();

        assertThat(state.song()).contains(a);
    }
}

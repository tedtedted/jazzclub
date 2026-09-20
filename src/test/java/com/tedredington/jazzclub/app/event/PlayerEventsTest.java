package com.tedredington.jazzclub.app.event;

import static com.tedredington.jazzclub.testsupport.TestData.EVANS;
import static com.tedredington.jazzclub.testsupport.TestData.HARD_BOP;
import static com.tedredington.jazzclub.testsupport.TestData.QUICKMIX;
import static com.tedredington.jazzclub.testsupport.TestData.song;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import com.tedredington.jazzclub.app.PlaybackState;
import com.tedredington.jazzclub.app.Selection;
import com.tedredington.jazzclub.pandora.error.AudioQualityUnavailableException;
import com.tedredington.jazzclub.pandora.error.InvalidLoginException;
import com.tedredington.jazzclub.pandora.error.PandoraApiException;
import com.tedredington.jazzclub.pandora.error.PandoraTransportException;
import com.tedredington.jazzclub.pandora.model.AudioQuality;
import com.tedredington.jazzclub.pandora.model.Rating;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.testsupport.FakeAudioPlayer;
import com.tedredington.jazzclub.testsupport.RecordingEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerEventsTest {

    private final PlaybackState state = new PlaybackState(5);
    private final FakeAudioPlayer player = new FakeAudioPlayer();
    private final RecordingEvents recorded = new RecordingEvents();
    private final PlayerEvents events = recorded.on(state, player);

    private final Song a = song("a", "300");
    private final Song b = song("b", "200");

    @BeforeEach
    void playingQuickMix() {
        state.stations(List.of(QUICKMIX, HARD_BOP, EVANS));
        state.changeStation(QUICKMIX);
        state.enqueue(List.of(a, b));
        state.advance();
        player.elapsed(Duration.ofSeconds(33));
    }

    @Test
    void anEventAboutThePlayingSongCarriesPositionQueueAndRealStation() {
        events.emit(EventType.SONG_LOVE, state.selection(), EventResult.OK);

        PlayerEvent event = recorded.last();
        assertThat(event.song()).isEqualTo(a);
        assertThat(event.station()).isEqualTo(QUICKMIX);
        assertThat(event.songStation()).as("on QuickMix, where the song really came from").isEqualTo(HARD_BOP);
        assertThat(event.played()).hasSeconds(33);
        assertThat(event.upcoming()).containsExactly(b);
    }

    @Test
    void stationsAreListedSortedByName() {
        events.emit(EventType.USER_GET_STATIONS, Selection.NONE, EventResult.OK);

        assertThat(recorded.last().stations()).containsExactly(EVANS, HARD_BOP, QUICKMIX);
        assertThat(recorded.last().song()).isNull();
        assertThat(recorded.last().played()).isZero();
    }

    @Test
    void stationsFollowTheSameOrderAsTheMenuSoScriptsCanUseTheNumbers() {
        List<PlayerEvent> collected = new java.util.ArrayList<>();
        PlayerEvents quickMixFirst = new PlayerEvents(state, player, collected::add,
                com.tedredington.jazzclub.app.StationSort.QUICKMIX_10_NAME_AZ.comparator());

        quickMixFirst.emit(EventType.USER_GET_STATIONS, Selection.NONE, EventResult.OK);

        assertThat(collected.getFirst().stations()).containsExactly(QUICKMIX, EVANS, HARD_BOP);
    }

    @Test
    void anEventAboutAPastSongHasNoPositionAndNoQueue() {
        state.advance(); // a is history now

        events.emit(EventType.SONG_BAN, new Selection(HARD_BOP, a), EventResult.OK);

        assertThat(recorded.last().played()).isZero();
        assertThat(recorded.last().upcoming()).isEmpty();
        assertThat(recorded.last().songStation()).as("not QuickMix, so no separate song station").isNull();
    }

    @Test
    void listenersSeeTheSongAsItIsNowNotAsItWasWhenTheKeyWasPressed() {
        Selection pressedOn = state.selection();
        state.updateSong(a.withRating(Rating.LOVE));

        events.emit(EventType.SONG_LOVE, pressedOn, EventResult.OK);

        assertThat(recorded.last().song().rating()).isEqualTo(Rating.LOVE);
    }

    @Test
    void anExplicitPlayedTimeWinsBecauseThePlayerHasAlreadyMovedOn() {
        events.emit(EventType.SONG_FINISH, state.selection(), EventResult.OK, Duration.ofSeconds(185));

        assertThat(recorded.last().played()).hasSeconds(185);
    }

    @Test
    void resultsTranslateIntoPianobarsCodes() {
        assertThat(EventResult.OK.isOk()).isTrue();
        assertThat(EventResult.OK.pandoraCode()).isEqualTo(1);
        assertThat(EventResult.OK.pandoraMessage()).isEqualTo("Everything is fine :)");
        assertThat(EventResult.OK.networkMessage()).isEqualTo("No error");

        EventResult api = EventResult.of(new PandoraApiException(1006, null));
        assertThat(api.pandoraCode()).isEqualTo(2030);
        assertThat(api.pandoraMessage()).isEqualTo("Station does not exist.");
        assertThat(api.networkCode()).isZero();
        assertThat(api.isOk()).isFalse();

        EventResult network = EventResult.of(new PandoraTransportException("Could not reach Pandora at x", null));
        assertThat(network.pandoraCode()).isEqualTo(1);
        assertThat(network.networkCode()).isEqualTo(7);
        assertThat(network.networkMessage()).isEqualTo("Could not reach Pandora at x");
        assertThat(network.isOk()).isFalse();

        assertThat(EventResult.of(new InvalidLoginException()).pandoraCode()).isZero();
        assertThat(EventResult.of(new AudioQualityUnavailableException(AudioQuality.HIGH)).pandoraMessage())
                .contains("high");
    }

    @Test
    void eventNamesArePianobars() {
        assertThat(EventType.SONG_START.pianobarName()).isEqualTo("songstart");
        assertThat(EventType.SONG_SHELF.pianobarName()).isEqualTo("songshelf");
        assertThat(EventType.STATION_QUICKMIX_TOGGLE.pianobarName()).isEqualTo("stationquickmixtoggle");
        assertThat(EventType.values()).extracting(EventType::pianobarName).doesNotHaveDuplicates()
                .allMatch(name -> name.matches("[a-z]+"));
    }
}

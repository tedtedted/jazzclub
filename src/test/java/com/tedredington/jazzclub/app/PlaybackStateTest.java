package com.tedredington.jazzclub.app;

import static com.tedredington.jazzclub.testsupport.TestData.EVANS;
import static com.tedredington.jazzclub.testsupport.TestData.HARD_BOP;
import static com.tedredington.jazzclub.testsupport.TestData.song;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.tedredington.jazzclub.pandora.model.Rating;
import com.tedredington.jazzclub.pandora.model.Song;
import org.junit.jupiter.api.Test;

class PlaybackStateTest {

    private final PlaybackState state = new PlaybackState(2);
    private final Song a = song("a", "200");
    private final Song b = song("b", "200");
    private final Song c = song("c", "200");

    @Test
    void startsEmpty() {
        assertThat(state.station()).isEmpty();
        assertThat(state.song()).isEmpty();
        assertThat(state.upcoming()).isEmpty();
        assertThat(state.history()).isEmpty();
        assertThat(state.quitRequested()).isFalse();
    }

    @Test
    void advanceWalksThroughTheQueue() {
        state.enqueue(List.of(a, b));

        assertThat(state.advance()).contains(a);
        assertThat(state.upcoming()).containsExactly(b);
        assertThat(state.advance()).contains(b);
        assertThat(state.advance()).isEmpty();
        assertThat(state.song()).isEmpty();
    }

    @Test
    void playedSongsEnterTheHistoryNewestFirstUpToItsLimit() {
        state.enqueue(List.of(a, b, c));
        state.advance();
        state.advance();
        state.advance();
        state.finishSong();

        assertThat(state.history()).containsExactly(c, b);
    }

    @Test
    void aHistoryOfZeroKeepsNothing() {
        PlaybackState noHistory = new PlaybackState(0);
        noHistory.enqueue(List.of(a));
        noHistory.advance();
        noHistory.finishSong();

        assertThat(noHistory.history()).isEmpty();
    }

    @Test
    void theHistoryRemembersTheRatingGivenWhilePlaying() {
        state.enqueue(List.of(a));
        state.advance();
        state.updateSong(a.withRating(Rating.LOVE));
        state.finishSong();

        assertThat(state.history()).extracting(Song::rating).containsExactly(Rating.LOVE);
    }

    @Test
    void changingStationDropsWhatWasQueuedForTheOldOne() {
        state.changeStation(EVANS);
        state.enqueue(List.of(a, b));

        state.changeStation(HARD_BOP);

        assertThat(state.station()).contains(HARD_BOP);
        assertThat(state.upcoming()).isEmpty();
    }

    @Test
    void clearingTheStationStopsTheRadio() {
        state.changeStation(EVANS);
        state.enqueue(List.of(a));

        state.clearStation();

        assertThat(state.station()).isEmpty();
        assertThat(state.upcoming()).isEmpty();
    }

    @Test
    void findsStationsByTheirId() {
        state.stations(List.of(EVANS, HARD_BOP));

        assertThat(state.findStation("300")).contains(HARD_BOP);
        assertThat(state.findStation("999")).isEmpty();
    }

    @Test
    void putStationAddsOrReplacesByToken() {
        state.stations(List.of(EVANS));

        state.putStation(HARD_BOP);
        state.putStation(EVANS.withName("Renamed"));

        assertThat(state.stations()).containsExactlyInAnyOrder(HARD_BOP, EVANS.withName("Renamed"));
    }

    @Test
    void removeStationForgetsIt() {
        state.stations(List.of(EVANS, HARD_BOP));

        state.removeStation(EVANS);

        assertThat(state.stations()).containsExactly(HARD_BOP);
    }

    @Test
    void aSongInTheHistoryCanBeUpdatedAndLookedUpFresh() {
        state.enqueue(List.of(a, b));
        state.advance();
        state.advance(); // a in history, b playing

        state.updateSong(a.withRating(Rating.BAN));

        assertThat(state.history()).extracting(Song::rating).containsExactly(Rating.BAN);
        assertThat(state.song()).contains(b);
        assertThat(state.current(a).rating()).isEqualTo(Rating.BAN);
        assertThat(state.current(b)).isSameAs(b);
        assertThat(state.current(c)).as("unknown songs come back unchanged").isSameAs(c);
        assertThat(state.isPlaying(b)).isTrue();
        assertThat(state.isPlaying(a)).isFalse();
    }

    @Test
    void theDefaultSelectionIsWhatIsOnAir() {
        assertThat(state.selection()).isEqualTo(Selection.NONE);

        state.changeStation(EVANS);
        state.enqueue(List.of(a));
        state.advance();

        assertThat(state.selection()).isEqualTo(new Selection(EVANS, a));
        assertThat(state.selection().songIfAny()).contains(a);
        assertThat(Selection.NONE.stationIfAny()).isEmpty();
        assertThat(state.historySize()).isEqualTo(2);
    }

    @Test
    void quitIsSticky() {
        state.requestQuit();

        assertThat(state.quitRequested()).isTrue();
    }
}
